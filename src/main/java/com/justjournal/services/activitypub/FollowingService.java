/*
 * Copyright (c) 2003-2026 Lucas Holt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.justjournal.services.activitypub;

import static com.justjournal.services.activitypub.ActivityPubFetcher.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.model.FollowingState;
import com.justjournal.model.RemoteFollowing;
import com.justjournal.model.User;
import com.justjournal.repository.RemoteFollowingRepository;
import com.justjournal.repository.RemotePostRepository;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fediverse accounts a writer follows. Following sends a signed {@code Follow}; the remote
 * server's {@code Accept} or {@code Reject} decides whether the account's public posts are taken
 * ({@link RemotePostService}). A {@code Reject} keeps the row as REJECTED, so the writer can see
 * it was declined; removing it sends nothing. Unfollowing sends an {@code Undo} of the Follow.
 *
 * <p>Only writers whose journal federates may follow anyone. The list is the writer's own: only
 * the count of accepted follows is public, as the actor's {@code following} collection.
 */
@Slf4j
@Service
public class FollowingService {

  private final RemoteFollowingRepository repository;
  private final RemotePostRepository postRepository;
  private final RemoteAccountResolver resolver;
  private final DeliveryService deliveryService;
  private final FederationPolicy federationPolicy;
  private final ActivityStreamsObjects objects;

  /** Most accounts one writer may follow, declined ones included. */
  @Setter
  @Value("${activitypub.following.max-per-user:500}")
  private int maxPerUser = 500;

  public FollowingService(
      final RemoteFollowingRepository repository,
      final RemotePostRepository postRepository,
      final RemoteAccountResolver resolver,
      final DeliveryService deliveryService,
      final FederationPolicy federationPolicy,
      final ActivityStreamsObjects objects) {
    this.repository = repository;
    this.postRepository = postRepository;
    this.resolver = resolver;
    this.deliveryService = deliveryService;
    this.federationPolicy = federationPolicy;
    this.objects = objects;
  }

  /** Why a writer can't follow an account. The message is for the writer. */
  public static final class FollowRefusedException extends Exception {
    public FollowRefusedException(final String message) {
      super(message);
    }
  }

  /**
   * Follow a remote account: find it, store it as PENDING, and queue a Follow for its inbox. Not
   * transactional as a whole, since finding the account means talking to its server.
   *
   * @param account {@code @user@host} or the address of its profile
   * @throws FollowRefusedException when the writer doesn't federate, follows too many accounts
   *     already, follows this one already, or the account can't be followed
   * @throws IOException when the account's server couldn't be reached
   */
  public RemoteFollowing follow(final User user, final String account)
      throws FollowRefusedException, IOException {
    if (!federationPolicy.federates(user)) {
      throw new FollowRefusedException(
          "Turn on the fediverse setting in your journal preferences first.");
    }
    if (repository.countByUserId(user.getId()) >= maxPerUser) {
      throw new FollowRefusedException(
          "You can follow at most " + maxPerUser + " fediverse accounts.");
    }

    final RemoteAccountResolver.RemoteAccount remote;
    try {
      remote = resolver.resolve(account);
    } catch (final RemoteAccountResolver.RefusedException e) {
      throw new FollowRefusedException(e.getMessage());
    }
    final String actorId = remote.id().toASCIIString();
    if (repository.findByUserIdAndActorId(user.getId(), actorId).isPresent()) throw already();

    final String local = objects.actorId(user);
    final RemoteFollowing following = new RemoteFollowing();
    following.setUserId(user.getId());
    following.setActorId(actorId);
    following.setInbox(FollowerService.url(remote.inbox(), "inbox"));
    following.setSharedInbox(
        remote.sharedInbox() == null ? null : FollowerService.url(remote.sharedInbox(), "inbox"));
    following.setFollowId(local + "#follows/" + UUID.randomUUID());
    following.setState(FollowingState.PENDING);
    following.setDisplayName(remote.displayName());
    following.setHandle(remote.handle());
    following.setProfileUrl(remote.profileUrl());

    final RemoteFollowing saved;
    try {
      saved = repository.save(following);
    } catch (final DataIntegrityViolationException e) {
      throw already(); // followed by another request meanwhile
    }
    try {
      final String follow = followActivity(saved, local, true).toString();
      deliveryService.enqueue(local, user.getId(), follow, List.of(remote.inbox()));
    } catch (final RuntimeException e) {
      repository.delete(saved);
      throw e;
    }
    log.info("{} asked to follow {}", user.getUsername(), actorId);
    return saved;
  }

  /**
   * Stop following an account: queue an Undo of the Follow, unless the account declined it, and
   * forget the account. Its stored posts go too when nobody else here follows it.
   *
   * @return false when the writer wasn't following it
   */
  @Transactional
  public boolean unfollow(final User user, final String actorId) {
    if (actorId == null) return false;
    final Optional<RemoteFollowing> found =
        repository.findByUserIdAndActorId(user.getId(), actorId);
    if (found.isEmpty()) return false;
    final RemoteFollowing following = found.get();
    if (following.getState() != FollowingState.REJECTED) {
      final String local = objects.actorId(user);
      final ObjectNode undo = ActivityStreamsObjects.withContext();
      undo.put("id", local + "#undos/" + UUID.randomUUID());
      undo.put("type", "Undo");
      undo.put("actor", local);
      undo.set("object", followActivity(following, local, false));
      deliveryService.enqueue(
          local, user.getId(), undo.toString(), List.of(URI.create(following.getInbox())));
    }
    repository.deleteByUserIdAndActorId(user.getId(), actorId);
    removePostsIfUnfollowed(actorId);
    return true;
  }

  /**
   * Act on an {@code Accept} or {@code Reject} sent to a writer's inbox. It must name one of the
   * writer's Follows, by id or embedded, and be signed by the account that Follow was sent to.
   *
   * @param user the journal whose inbox took it
   * @param sender the actor whose key signed it
   */
  @Transactional
  public InboxService.Outcome response(
      final User user, final JsonNode activity, final String sender) {
    final String type = text(activity, "type");
    if (!"Accept".equals(type) && !"Reject".equals(type)) return InboxService.Outcome.IGNORED;
    final Optional<RemoteFollowing> found = findFollow(user, activity.get("object"), sender);
    if (found.isEmpty()) return InboxService.Outcome.IGNORED;
    final RemoteFollowing following = found.get();
    if (!following.getActorId().equals(sender)) {
      log.debug("{} of a Follow of {} was sent by {}", type, following.getActorId(), sender);
      return InboxService.Outcome.IGNORED;
    }

    following.setState("Accept".equals(type) ? FollowingState.ACCEPTED : FollowingState.REJECTED);
    repository.save(following);
    if (following.getState() == FollowingState.REJECTED) removePostsIfUnfollowed(sender);
    log.info("{} {} a Follow from {}", sender, type.toLowerCase(Locale.ROOT), user.getUsername());
    return InboxService.Outcome.HANDLED;
  }

  /** The Follow an Accept or Reject is about: by its id, or else by who follows whom. */
  private Optional<RemoteFollowing> findFollow(
      final User user, final JsonNode follow, final String sender) {
    final String followId = InboxService.id(follow);
    if (followId != null) {
      final Optional<RemoteFollowing> byId =
          repository.findByUserIdAndFollowId(user.getId(), followId);
      if (byId.isPresent()) return byId;
    }
    // some servers embed the Follow without its id, or with an id of their own
    if (follow != null
        && follow.isObject()
        && "Follow".equals(text(follow, "type"))
        && objects.actorId(user).equals(InboxService.id(follow.get("actor")))
        && sender.equals(InboxService.id(follow.get("object")))) {
      return repository.findByUserIdAndActorId(user.getId(), sender);
    }
    return Optional.empty();
  }

  /** Everything the writer follows or asked to follow, oldest first. Never show this publicly. */
  @Transactional(readOnly = true)
  public List<RemoteFollowing> list(final User user) {
    return repository.findByUserIdOrderByIdAsc(user.getId());
  }

  /** How many accounts accepted the writer's Follow. */
  @Transactional(readOnly = true)
  public long countAccepted(final User user) {
    return repository.countByUserIdAndState(user.getId(), FollowingState.ACCEPTED);
  }

  /**
   * The inboxes an actor Delete for the writer goes to, so the servers of the accounts they follow
   * drop the follow: each server's shared inbox when it has one, otherwise the account's own inbox.
   * Declined follows are left out.
   */
  @Transactional(readOnly = true)
  public Set<URI> deliveryInboxes(final User user) {
    final Set<URI> inboxes = new LinkedHashSet<>();
    for (final RemoteFollowing following : list(user)) {
      if (following.getState() == FollowingState.REJECTED) continue;
      final String target =
          following.getSharedInbox() == null || following.getSharedInbox().isBlank()
              ? following.getInbox()
              : following.getSharedInbox();
      try {
        inboxes.add(URI.create(target));
      } catch (final IllegalArgumentException e) {
        // stored values were checked by follow(); skip anything that slipped past
      }
    }
    return inboxes;
  }

  /** Whether anyone here follows the actor, whatever the state. */
  @Transactional(readOnly = true)
  public boolean followedByAny(final URI actorId) {
    return actorId != null && repository.existsByActorId(actorId.toASCIIString());
  }

  /**
   * Forget everything a writer follows, when their journal stops federating or the account goes.
   * The actor Delete sent then tells the remote servers, so no Undo is sent.
   *
   * @return how many accounts the writer followed
   */
  @Transactional
  public long removeAll(final User user) {
    final long removed = repository.deleteByUserId(user.getId());
    if (removed > 0) postRepository.deleteUnfollowed(FollowingState.ACCEPTED);
    return removed;
  }

  /**
   * A remote actor was deleted: nobody follows it any more, and its posts go.
   *
   * @return how many writers followed it
   */
  @Transactional
  public long remoteActorGone(final URI actorId) {
    if (actorId == null) return 0;
    final String actor = actorId.toASCIIString();
    postRepository.deleteByActorId(actor);
    return repository.deleteByActorId(actor);
  }

  private void removePostsIfUnfollowed(final String actorId) {
    if (!repository.existsByActorIdAndState(actorId, FollowingState.ACCEPTED)) {
      postRepository.deleteByActorId(actorId);
    }
  }

  /** The Follow as sent; with a context to send on its own, without to embed in an Undo. */
  private static ObjectNode followActivity(
      final RemoteFollowing following, final String local, final boolean withContext) {
    final ObjectNode follow =
        withContext
            ? ActivityStreamsObjects.withContext()
            : JsonNodeFactory.instance.objectNode();
    follow.put("id", following.getFollowId());
    follow.put("type", "Follow");
    follow.put("actor", local);
    follow.put("object", following.getActorId());
    return follow;
  }

  private static FollowRefusedException already() {
    return new FollowRefusedException("You already follow that account.");
  }
}
