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
package com.justjournal.core;

import com.justjournal.model.DeliveryStatus;
import com.justjournal.model.OutboundDelivery;
import com.justjournal.repository.OutboundDeliveryRepository;
import com.justjournal.services.activitypub.DeliveryService;
import com.justjournal.services.activitypub.DeliverySigner;
import com.justjournal.services.activitypub.FederationPolicy;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Posts queued ActivityPub activities to other servers, a batch every half minute. Each delivery
 * is claimed first, so two servers never post the same one; a claim that is never finished runs
 * out after a while and the delivery is tried again. Nothing runs while federation is off for the
 * site, or while there is nothing to sign requests with; queued deliveries simply wait.
 */
@Slf4j
@Component
public class ActivityDelivery {

  static final Duration LEASE = Duration.ofMinutes(5);

  private final OutboundDeliveryRepository repository;
  private final DeliveryService deliveryService;
  private final FederationPolicy federationPolicy;
  private final ObjectProvider<DeliverySigner> signers;
  private final AtomicBoolean warnedNoSigner = new AtomicBoolean();

  @Setter
  @Value("${activitypub.delivery.batch-size:20}")
  private int batchSize = 20;

  @Setter
  @Value("${activitypub.delivery.keep-delivered-days:7}")
  private int keepDeliveredDays = 7;

  @Setter
  @Value("${activitypub.delivery.keep-failed-days:30}")
  private int keepFailedDays = 30;

  public ActivityDelivery(
      final OutboundDeliveryRepository repository,
      final DeliveryService deliveryService,
      final FederationPolicy federationPolicy,
      final ObjectProvider<DeliverySigner> signers) {
    this.repository = repository;
    this.deliveryService = deliveryService;
    this.federationPolicy = federationPolicy;
    this.signers = signers;
  }

  @Scheduled(fixedDelay = 1000 * 30, initialDelay = 1000 * 60)
  public void run() {
    if (!federationPolicy.isEnabled()) return;
    final DeliverySigner signer = signers.getIfAvailable();
    if (signer == null) {
      if (warnedNoSigner.compareAndSet(false, true)) {
        log.warn("No DeliverySigner is configured; ActivityPub deliveries stay queued");
      }
      return;
    }

    final Date now = new Date();
    final List<OutboundDelivery> due =
        repository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            DeliveryStatus.PENDING, now, PageRequest.of(0, batchSize));
    final Date lease = new Date(now.getTime() + LEASE.toMillis());
    for (final OutboundDelivery delivery : due) {
      if (repository.claim(delivery.getId(), now, lease) != 1) continue;
      try {
        deliveryService.attempt(delivery, signer, new Date());
      } catch (final RuntimeException e) {
        log.error("Could not deliver activity {}", delivery.getId(), e);
      }
    }
  }

  /** Removes deliveries finished long enough ago, once an hour. */
  @Scheduled(fixedDelay = 1000 * 60 * 60, initialDelay = 1000 * 60 * 10)
  public void purge() {
    if (!federationPolicy.isEnabled()) return;
    final long now = System.currentTimeMillis();
    final int removed =
        deliveryService.purge(
            new Date(now - Duration.ofDays(keepDeliveredDays).toMillis()),
            new Date(now - Duration.ofDays(keepFailedDays).toMillis()));
    if (removed > 0) log.info("Removed {} finished ActivityPub deliveries", removed);
  }
}
