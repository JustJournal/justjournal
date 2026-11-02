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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.model.Entry;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.activitypub.ActivityPublisher.Change;
import com.justjournal.services.activitypub.ActivityPublisher.EntryChanged;
import com.justjournal.services.activitypub.ActivityPublisher.ProfileChanged;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns what {@link ActivityPublisher} noted into activities queued for the owner's followers.
 *
 * <p>Runs once the transaction that made the change has committed, or straight away when there was
 * none, on another thread and in a transaction of its own: the user's request neither waits for it
 * nor fails because of it, and the entry is read as it was stored, not as the request's persistence
 * context last saw it.
 */
@Slf4j
@Component
public class ActivityPublishListener {

  private final FederationPolicy federationPolicy;
  private final UserRepository userRepository;
  private final EntryRepository entryRepository;
  private final EntryAccessService entryAccessService;
  private final EntryAttachmentService entryAttachmentService;
  private final ActivityStreamsObjects objects;
  private final DeliveryService deliveryService;

  public ActivityPublishListener(
      final FederationPolicy federationPolicy,
      final UserRepository userRepository,
      final EntryRepository entryRepository,
      final EntryAccessService entryAccessService,
      final EntryAttachmentService entryAttachmentService,
      final ActivityStreamsObjects objects,
      final DeliveryService deliveryService) {
    this.federationPolicy = federationPolicy;
    this.userRepository = userRepository;
    this.entryRepository = entryRepository;
    this.entryAccessService = entryAccessService;
    this.entryAttachmentService = entryAttachmentService;
    this.objects = objects;
    this.deliveryService = deliveryService;
  }

  @Async
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onEntryChanged(final EntryChanged change) {
    try {
      publish(change);
    } catch (final RuntimeException e) {
      log.warn("Could not publish the change to entry {}", change.entryId(), e);
    }
  }

  @Async
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onProfileChanged(final ProfileChanged change) {
    try {
      publish(change);
    } catch (final RuntimeException e) {
      log.warn("Could not publish the profile of user {}", change.userId(), e);
    }
  }

  /**
   * Queue what the change calls for.
   *
   * @return the queued activity, or null when nothing is sent
   */
  ObjectNode publish(final EntryChanged change) {
    final Entry entry =
        change.change() == Change.DELETED
            ? null
            : entryRepository.findById(change.entryId()).orElse(null);
    final User user =
        change.userId() > 0
            ? userRepository.findById(change.userId()).orElse(null)
            : entry == null ? null : entry.getUser();
    if (!federationPolicy.federates(user)) return null;

    final boolean now =
        entry != null
            && entry.getUser() != null
            && entry.getUser().getId() == user.getId()
            && entryAccessService.isPubliclyVisible(entry);
    final boolean was =
        change.change() == Change.ATTACHMENTS
            ? now
            : change.before() != null && change.before().visible();

    final ObjectNode activity;
    if (!was && now) {
      activity = objects.createActivity(entry, attachments(entry));
    } else if (was && now) {
      final boolean changed =
          change.attachmentsChanged()
              || change.before() == null
              || change.before().differsFrom(entry);
      if (!changed) return null;
      activity = objects.updateActivity(entry, attachments(entry), change.at());
    } else if (was) {
      activity = objects.deleteActivity(user, change.entryId(), change.at());
    } else {
      return null;
    }
    deliveryService.enqueueToFollowers(user, activity.toString());
    return activity;
  }

  /**
   * Queue an Update of the actor.
   *
   * @return the queued activity, or null when nothing is sent
   */
  ObjectNode publish(final ProfileChanged change) {
    final User user = userRepository.findById(change.userId()).orElse(null);
    if (!federationPolicy.federates(user)) return null;
    final ObjectNode activity = objects.updateActorActivity(user, change.at());
    deliveryService.enqueueToFollowers(user, activity.toString());
    return activity;
  }

  private FeedAttachments attachments(final Entry entry) {
    return entryAttachmentService.forFeed(List.of(entry)).get(entry.getId());
  }
}
