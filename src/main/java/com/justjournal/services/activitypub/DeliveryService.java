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

import com.justjournal.core.Settings;
import com.justjournal.model.DeliveryStatus;
import com.justjournal.model.OutboundDelivery;
import com.justjournal.model.User;
import com.justjournal.repository.OutboundDeliveryRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The queue of activities to post to other servers' inboxes. Callers decide whether to send
 * anything at all (see {@link FederationPolicy}); this only queues what it is given and works
 * through it with retries.
 *
 * <p>A temporary failure (5xx, 429, timeouts, connection errors) is tried again after {@link
 * #BACKOFF}, about two days in all before the delivery is given up. Any other answer that isn't
 * 2xx, redirects included, fails it at once, as does an inbox {@link InboxClient} refuses.
 */
@Slf4j
@Service
public class DeliveryService {

  /** How long to wait after each failed attempt; once these run out the delivery fails. */
  static final List<Duration> BACKOFF =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(30),
          Duration.ofHours(2),
          Duration.ofHours(12),
          Duration.ofHours(12),
          Duration.ofHours(12),
          Duration.ofHours(12));

  /** The first attempt and one after each wait: the last is about 50 hours after the first. */
  static final int MAX_ATTEMPTS = BACKOFF.size() + 1;

  private static final String USERS = "users/";

  private final OutboundDeliveryRepository repository;
  private final FollowerService followerService;
  private final InboxClient inboxClient;
  private final Settings settings;

  public DeliveryService(
      final OutboundDeliveryRepository repository,
      final FollowerService followerService,
      final InboxClient inboxClient,
      final Settings settings) {
    this.repository = repository;
    this.followerService = followerService;
    this.inboxClient = inboxClient;
    this.settings = settings;
  }

  /**
   * Queue an activity for some inboxes, each only once.
   *
   * @param senderActorId the sending actor's id
   * @param senderUserId the sending user, or null for the instance actor
   * @param activityJson the activity, posted exactly as given
   * @param inboxes where to post it; nulls and URLs that can't be stored are skipped
   * @return the queued deliveries
   */
  @Transactional
  public List<OutboundDelivery> enqueue(
      final String senderActorId,
      final Integer senderUserId,
      final String activityJson,
      final Collection<URI> inboxes) {
    if (senderActorId == null || senderActorId.isBlank()) {
      throw new IllegalArgumentException("A sender actor id is required");
    }
    if (activityJson == null || activityJson.isBlank()) {
      throw new IllegalArgumentException("An activity is required");
    }
    if (inboxes == null || inboxes.isEmpty()) return List.of();

    final Set<String> targets = new LinkedHashSet<>();
    for (final URI inbox : inboxes) {
      if (inbox == null) continue;
      try {
        targets.add(FollowerService.url(inbox, "inbox"));
      } catch (final IllegalArgumentException e) {
        log.debug("Not queueing for inbox {}: {}", inbox, e.getMessage());
      }
    }

    final Date now = new Date();
    final List<OutboundDelivery> queued = new ArrayList<>(targets.size());
    for (final String target : targets) {
      final OutboundDelivery delivery = new OutboundDelivery();
      delivery.setUserId(senderUserId);
      delivery.setActorId(senderActorId);
      delivery.setTargetInbox(target);
      delivery.setActivity(activityJson);
      delivery.setStatus(DeliveryStatus.PENDING);
      delivery.setNextAttemptAt(now);
      delivery.setCreated(now);
      queued.add(delivery);
    }
    return queued.isEmpty() ? List.of() : repository.saveAll(queued);
  }

  /**
   * Queue an activity from a user for everyone following them, once per server where servers
   * share an inbox.
   */
  @Transactional
  public List<OutboundDelivery> enqueueToFollowers(final User sender, final String activityJson) {
    return enqueue(
        actorId(sender), sender.getId(), activityJson, followerService.deliveryInboxes(sender));
  }

  /** The user's actor id, the same address WebFinger and the actor document give out. */
  public String actorId(final User user) {
    return settings.getBaseUri().replace("http://", "https://") + USERS + user.getUsername();
  }

  /**
   * Post one claimed delivery and record how it went.
   *
   * @param delivery a PENDING delivery this caller has claimed
   * @param signer signs the request
   * @param now the time of this attempt
   * @return the delivery's status afterwards
   */
  public DeliveryStatus attempt(
      final OutboundDelivery delivery, final DeliverySigner signer, final Date now) {
    final int attempts = delivery.getAttempts() + 1;
    final byte[] body = delivery.getActivity().getBytes(StandardCharsets.UTF_8);

    final URI target;
    try {
      target = inboxClient.check(URI.create(delivery.getTargetInbox()));
    } catch (final IllegalArgumentException | InboxRefusedException e) {
      return fail(delivery, attempts, e.getMessage(), now);
    }

    try {
      final Map<String, String> headers = signer.sign(delivery.getActorId(), target, body);
      final int status = inboxClient.post(target, body, headers);
      if (status >= 200 && status < 300) {
        final Date next = delivery.getNextAttemptAt();
        record(delivery, DeliveryStatus.DELIVERED, attempts, next, null, now);
        return DeliveryStatus.DELIVERED;
      }
      if (status == 429 || status >= 500) return retry(delivery, attempts, "HTTP " + status, now);
      return fail(delivery, attempts, "HTTP " + status, now);
    } catch (final InboxRefusedException e) {
      return fail(delivery, attempts, e.getMessage(), now);
    } catch (final IOException | RuntimeException e) {
      return retry(delivery, attempts, e.getClass().getSimpleName() + ": " + e.getMessage(), now);
    }
  }

  /**
   * How long to wait after a failed attempt.
   *
   * @param attempts attempts made so far, the failed one included
   * @return the wait, or null when the delivery should be given up
   */
  static Duration backoff(final int attempts) {
    if (attempts < 1 || attempts > BACKOFF.size()) return null;
    return BACKOFF.get(attempts - 1);
  }

  /**
   * Remove finished deliveries that are old enough not to matter.
   *
   * @return how many were removed
   */
  public int purge(final Date deliveredBefore, final Date failedBefore) {
    return repository.deleteFinished(DeliveryStatus.DELIVERED, deliveredBefore)
        + repository.deleteFinished(DeliveryStatus.FAILED, failedBefore);
  }

  private DeliveryStatus retry(
      final OutboundDelivery delivery, final int attempts, final String error, final Date now) {
    final Duration wait = backoff(attempts);
    if (wait == null) {
      return fail(delivery, attempts, "Gave up after " + attempts + " attempts: " + error, now);
    }
    log.debug(
        "Delivery {} to {} failed, retrying: {}",
        delivery.getId(),
        delivery.getTargetInbox(),
        error);
    record(
        delivery,
        DeliveryStatus.PENDING,
        attempts,
        new Date(now.getTime() + wait.toMillis()),
        error,
        null);
    return DeliveryStatus.PENDING;
  }

  private DeliveryStatus fail(
      final OutboundDelivery delivery, final int attempts, final String error, final Date now) {
    log.info("Delivery {} to {} failed: {}", delivery.getId(), delivery.getTargetInbox(), error);
    record(delivery, DeliveryStatus.FAILED, attempts, delivery.getNextAttemptAt(), error, now);
    return DeliveryStatus.FAILED;
  }

  private void record(
      final OutboundDelivery delivery,
      final DeliveryStatus status,
      final int attempts,
      final Date next,
      final String error,
      final Date finished) {
    delivery.setStatus(status);
    delivery.setAttempts(attempts);
    delivery.setNextAttemptAt(next);
    delivery.setLastError(truncate(error));
    delivery.setFinishedAt(finished);
    repository.record(delivery.getId(), status, attempts, next, delivery.getLastError(), finished);
  }

  private static String truncate(final String error) {
    if (error == null || error.length() <= OutboundDelivery.MAX_ERROR_LENGTH) return error;
    return error.substring(0, OutboundDelivery.MAX_ERROR_LENGTH);
  }
}
