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

import com.justjournal.model.User;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells the servers following a journal that its actor is gone, so they remove the account with
 * its posts and follows:
 *
 * <ul>
 *   <li>when the journal stops federating (the writer turned it off, or made the journal owner
 *       view only), through the delivery queue with its retries;
 *   <li>just before the account is deleted, posted straight away, since the keys that sign it go
 *       with the account.
 * </ul>
 *
 * <p>Nothing is sent while federation is off for the site. Nothing here throws on a delivery
 * problem: the writer's change goes ahead either way.
 */
@Slf4j
@Service
public class ActorDeletion {

  /** How many inboxes are posted to at once when an account is deleted. */
  static final int THREADS = 8;

  private final FederationPolicy federationPolicy;
  private final FollowerService followerService;
  private final DeliveryService deliveryService;
  private final ActivityStreamsObjects objects;
  private final InboxClient inboxClient;
  private final DeliverySigner signer;

  /** How long deleting an account waits for the other servers, all of them together. */
  @Setter
  @Value("${activitypub.delivery.account-delete-seconds:30}")
  private long accountDeleteSeconds = 30;

  public ActorDeletion(
      final FederationPolicy federationPolicy,
      final FollowerService followerService,
      final DeliveryService deliveryService,
      final ActivityStreamsObjects objects,
      final InboxClient inboxClient,
      final DeliverySigner signer) {
    this.federationPolicy = federationPolicy;
    this.followerService = followerService;
    this.deliveryService = deliveryService;
    this.objects = objects;
    this.inboxClient = inboxClient;
    this.signer = signer;
  }

  /**
   * The journal stopped federating. Queue a Delete of the actor for its followers, drop anything
   * still queued for them so no Create or Update arrives after it, and forget the followers. A
   * writer who turns federation on again starts with nobody following.
   *
   * @return how many inboxes the Delete was queued for
   */
  @Transactional
  public int federationStopped(final User user) {
    if (user == null || !federationPolicy.isEnabled()) return 0;
    final Set<URI> inboxes = followerService.deliveryInboxes(user);
    deliveryService.cancelPending(user);
    followerService.removeAll(user);
    if (inboxes.isEmpty()) return 0;

    final String delete = objects.deleteActorActivity(user, new Date()).toString();
    return deliveryService.enqueue(objects.actorId(user), user.getId(), delete, inboxes).size();
  }

  /**
   * The account is about to be deleted. Post a signed Delete of the actor to each follower's
   * server now, a few at a time, waiting at most {@code activitypub.delivery.account-delete-seconds}
   * in all. Servers that don't take it in that time, or answer with an error, are not tried again:
   * the key that signs it is deleted with the account.
   *
   * @return how many inboxes took the Delete
   */
  public int accountDeleting(final User user) {
    if (user == null || !federationPolicy.isEnabled()) return 0;
    try {
      final Set<URI> inboxes = followerService.deliveryInboxes(user);
      if (inboxes.isEmpty()) return 0;

      final String actorId = objects.actorId(user);
      final byte[] body =
          objects.deleteActorActivity(user, new Date()).toString().getBytes(StandardCharsets.UTF_8);
      final List<Callable<Boolean>> posts = new ArrayList<>(inboxes.size());
      for (final URI inbox : inboxes) posts.add(() -> post(inbox, actorId, body));

      final int delivered = postAll(posts);
      log.info(
          "Account {} deleted: {} of {} fediverse servers took the actor Delete",
          user.getUsername(),
          delivered,
          inboxes.size());
      return delivered;
    } catch (final RuntimeException e) {
      log.warn("Could not send the actor Delete for account {}", user.getUsername(), e);
      return 0;
    }
  }

  private int postAll(final List<Callable<Boolean>> posts) {
    final ExecutorService pool = Executors.newFixedThreadPool(Math.min(THREADS, posts.size()));
    try {
      int delivered = 0;
      for (final Future<Boolean> result :
          pool.invokeAll(posts, accountDeleteSeconds, TimeUnit.SECONDS)) {
        try {
          if (Boolean.TRUE.equals(result.get())) delivered++;
        } catch (final CancellationException | ExecutionException e) {
          // out of time, or failed; post() logs its own failures
        }
      }
      return delivered;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return 0;
    } finally {
      pool.shutdownNow();
    }
  }

  private boolean post(final URI inbox, final String actorId, final byte[] body) {
    try {
      final URI target = inboxClient.check(inbox);
      final int status = inboxClient.post(target, body, signer.sign(actorId, target, body));
      if (status >= 200 && status < 300) return true;
      log.info("Actor Delete to {} refused: HTTP {}", inbox, status);
    } catch (final InboxRefusedException | IOException | RuntimeException e) {
      log.info("Actor Delete to {} failed: {}", inbox, e.getMessage());
    }
    return false;
  }
}
