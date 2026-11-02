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

import static com.justjournal.services.activitypub.ActivityPubFetcher.sameHost;
import static com.justjournal.services.activitypub.ActivityPubFetcher.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.model.User;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Acts on activities a journal's inbox took from other servers, once their signature checked out.
 * Only following is understood:
 *
 * <ul>
 *   <li>{@code Follow} of the journal's actor: the follower's actor is fetched for its inbox; if
 *       the journal federates the follower is stored and sent an {@code Accept}, otherwise sent a
 *       {@code Reject} and nothing is stored.
 *   <li>{@code Undo} of such a Follow, embedded as Mastodon sends it: the follower is removed.
 *   <li>{@code Delete} of the sending actor itself: the account is gone, so it is removed from the
 *       followers of every journal.
 * </ul>
 *
 * Everything else is ignored.
 */
@Slf4j
@Service
public class InboxService {

  /** What became of an activity. */
  public enum Outcome {
    /** Acted on. */
    HANDLED,
    /** Nothing to do, or nothing that can be done. */
    IGNORED,
    /** It couldn't be acted on for now; the sender should send it again later. */
    RETRY
  }

  static final String AS_CONTEXT = "https://www.w3.org/ns/activitystreams";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FollowerService followerService;
  private final DeliveryService deliveryService;
  private final FederationPolicy federationPolicy;
  private final ActorKeyService actorKeyService;
  private final ActivityPubFetcher fetcher;

  /** The same cap as for key documents: what is fetched here is the same actor document. */
  @Setter
  @Value("${activitypub.key-fetch.max-bytes:262144}")
  private int maxBytes = 262_144;

  public InboxService(
      final FollowerService followerService,
      final DeliveryService deliveryService,
      final FederationPolicy federationPolicy,
      final ActorKeyService actorKeyService,
      final ActivityPubFetcher fetcher) {
    this.followerService = followerService;
    this.deliveryService = deliveryService;
    this.federationPolicy = federationPolicy;
    this.actorKeyService = actorKeyService;
    this.fetcher = fetcher;
  }

  /**
   * Act on an activity sent to a user's inbox.
   *
   * @param user the journal whose inbox took it
   * @param activity the activity, a JSON object with a type
   * @param sender the actor whose key signed the request, which is also the activity's actor
   */
  public Outcome handle(final User user, final JsonNode activity, final String sender) {
    final String type = text(activity, "type");
    if (type == null) return Outcome.IGNORED;
    return switch (type) {
      case "Follow" -> follow(user, activity, sender);
      case "Undo" -> undo(user, activity, sender);
      case "Delete" -> delete(activity, sender);
      default -> Outcome.IGNORED;
    };
  }

  /**
   * A Delete whose signature can't be checked. When a Mastodon account is deleted it signs the
   * Delete with the account's key, but its actor (and so the key) is already gone, so the
   * signature can't be verified. Such a Delete of the sending actor itself is taken only when the
   * actor follows a journal here and a fresh, unsigned fetch of the actor's own address answers
   * 404 or 410; anyone could send the Delete, but only the actor's server can make it vanish.
   *
   * @return HANDLED when the actor was removed, IGNORED when the Delete is not taken
   */
  public Outcome deleteOfGoneActor(final JsonNode activity) {
    if (!"Delete".equals(text(activity, "type"))) return Outcome.IGNORED;
    final String actor = id(activity.get("actor"));
    final URI actorUri = uri(actor);
    if (actorUri == null || !actor.equals(id(activity.get("object")))) return Outcome.IGNORED;
    if (!followerService.followsAny(actorUri) || !isGone(actor)) return Outcome.IGNORED;
    final long removed = followerService.removeEverywhere(actorUri);
    log.info("Actor {} is gone; removed it from {} journals' followers", actor, removed);
    return Outcome.HANDLED;
  }

  private Outcome follow(final User user, final JsonNode follow, final String sender) {
    final String local = actorKeyService.userActorId(user.getUsername());
    if (!local.equals(id(follow.get("object")))) return Outcome.IGNORED;

    final Follower follower;
    try {
      follower = fetchFollower(sender);
    } catch (final IOException e) {
      log.debug("Could not fetch {}, which follows {}: {}", sender, local, e.getMessage());
      return Outcome.RETRY;
    } catch (final RefusedActorException e) {
      log.debug("Not taking a Follow of {} from {}: {}", local, sender, e.getMessage());
      return Outcome.IGNORED;
    }

    final boolean accept = federationPolicy.federates(user);
    if (accept) {
      followerService.add(user, follower.id(), follower.inbox(), follower.sharedInbox());
    }
    deliveryService.enqueue(
        local, user.getId(), reply(accept, local, follow, sender), List.of(follower.inbox()));
    log.info("{} a Follow of {} from {}", accept ? "Accepted" : "Rejected", local, sender);
    return Outcome.HANDLED;
  }

  private Outcome undo(final User user, final JsonNode undo, final String sender) {
    final JsonNode follow = undo.get("object");
    // only an embedded Follow can be matched: the ids of Follows are not kept
    if (follow == null || !follow.isObject() || !"Follow".equals(text(follow, "type")))
      return Outcome.IGNORED;
    final String local = actorKeyService.userActorId(user.getUsername());
    if (!sender.equals(id(follow.get("actor"))) || !local.equals(id(follow.get("object"))))
      return Outcome.IGNORED;
    final URI actor = uri(sender);
    if (actor == null) return Outcome.IGNORED;
    followerService.remove(user, actor);
    return Outcome.HANDLED;
  }

  private Outcome delete(final JsonNode delete, final String sender) {
    final URI actor = uri(sender);
    if (actor == null || !sender.equals(id(delete.get("object")))) return Outcome.IGNORED;
    final long removed = followerService.removeEverywhere(actor);
    log.info("Actor {} was deleted; removed it from {} journals' followers", sender, removed);
    return Outcome.HANDLED;
  }

  /** Accept or Reject of a Follow, from the followed journal's actor. */
  private static String reply(
      final boolean accept, final String local, final JsonNode follow, final String sender) {
    final ObjectNode reply = MAPPER.createObjectNode();
    reply.put("@context", AS_CONTEXT);
    reply.put("id", local + (accept ? "#accepts/" : "#rejects/") + UUID.randomUUID());
    reply.put("type", accept ? "Accept" : "Reject");
    reply.put("actor", local);
    // the Follow as received, down to what identifies it
    final ObjectNode object = reply.putObject("object");
    final String followId = text(follow, "id");
    if (followId != null) object.put("id", followId);
    object.put("type", "Follow");
    object.put("actor", sender);
    object.put("object", local);
    return reply.toString();
  }

  /** A follower's actor, its inbox, and its server's shared inbox if it has one. */
  private record Follower(URI id, URI inbox, URI sharedInbox) {}

  /** Why a follower's actor document can't be used; asking again won't help. */
  private static final class RefusedActorException extends Exception {
    RefusedActorException(final String message) {
      super(message);
    }
  }

  /**
   * Fetch a follower's actor, signed by the instance actor, and read its inboxes.
   *
   * @throws IOException when the fetch failed in a way that may pass
   * @throws RefusedActorException when the actor may not be fetched or its document is unusable
   */
  private Follower fetchFollower(final String actorId) throws IOException, RefusedActorException {
    final URI id = httpUrl(actorId);
    if (id == null) throw new RefusedActorException("the actor id is not a usable URL");
    final NormalizedUrl url;
    final SafeHttpFetcher.Result result;
    try {
      url = fetcher.normalize(actorId);
      result = fetcher.signedGet(url, maxBytes);
    } catch (final LinkRejectedException e) {
      throw new RefusedActorException("refused address: " + e.getMessage());
    } catch (final FetchException e) {
      if (e.permanent()) throw new RefusedActorException(e.getMessage());
      throw e;
    }
    if (!sameHost(result.finalUri(), url.asciiHost()))
      throw new RefusedActorException("redirected to " + result.finalUri().getHost());

    final JsonNode actor;
    try {
      actor = MAPPER.readTree(result.body());
    } catch (final IOException e) {
      throw new RefusedActorException("the actor is not JSON");
    }
    if (actor == null || !actor.isObject() || !actorId.equals(text(actor, "id")))
      throw new RefusedActorException("the document is not that actor");

    final URI inbox = httpUrl(text(actor, "inbox"));
    if (inbox == null) throw new RefusedActorException("the actor has no usable inbox");
    final URI sharedInbox = httpUrl(text(actor.get("endpoints"), "sharedInbox"));
    return new Follower(id, inbox, sharedInbox);
  }

  /** Whether an unsigned fetch of the actor says it is gone. */
  private boolean isGone(final String actorId) {
    try {
      fetcher.unsignedGet(fetcher.normalize(actorId), maxBytes);
      return false;
    } catch (final FetchException e) {
      return e.status() == 404 || e.status() == 410;
    } catch (final IOException | LinkRejectedException e) {
      log.debug("Could not tell whether {} is gone: {}", actorId, e.getMessage());
      return false;
    }
  }

  /** An address {@link FollowerService} will store, or null. */
  private static URI httpUrl(final String value) {
    final URI uri = uri(value);
    if (uri == null) return null;
    try {
      FollowerService.url(uri, "inbox");
      return uri;
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  /** The id of an object, or the object when it is given by its id alone. */
  public static String id(final JsonNode node) {
    if (node == null) return null;
    if (node.isTextual()) return node.asText();
    return node.isObject() ? text(node, "id") : null;
  }

  private static URI uri(final String value) {
    if (value == null) return null;
    try {
      return new URI(value);
    } catch (final URISyntaxException e) {
      return null;
    }
  }
}
