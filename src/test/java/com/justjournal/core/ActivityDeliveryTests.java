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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.justjournal.model.DeliveryStatus;
import com.justjournal.model.OutboundDelivery;
import com.justjournal.repository.OutboundDeliveryRepository;
import com.justjournal.services.activitypub.DeliveryService;
import com.justjournal.services.activitypub.DeliverySigner;
import com.justjournal.services.activitypub.FederationPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.Pageable;

/** The delivery job: idle unless federation is on and requests can be signed. */
class ActivityDeliveryTests {

  private OutboundDeliveryRepository repository;
  private DeliveryService deliveryService;
  private FederationPolicy policy;
  private final DeliverySigner signer = (actorId, target, body) -> Map.of();

  @BeforeEach
  void setUp() {
    repository = mock(OutboundDeliveryRepository.class);
    deliveryService = mock(DeliveryService.class);
    policy = new FederationPolicy();
    policy.setEnabled(true);
  }

  /** A real bean factory, so "no signer bean" behaves as it would in the application. */
  private static ObjectProvider<DeliverySigner> signers(final DeliverySigner signer) {
    final DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    if (signer != null) factory.registerSingleton("deliverySigner", signer);
    return factory.getBeanProvider(DeliverySigner.class);
  }

  private ActivityDelivery job(final DeliverySigner signer) {
    return new ActivityDelivery(repository, deliveryService, policy, signers(signer));
  }

  private static OutboundDelivery delivery(final int id) {
    final OutboundDelivery delivery = new OutboundDelivery();
    delivery.setId(id);
    return delivery;
  }

  @Test
  void idleWhileFederationIsOff() {
    policy.setEnabled(false);
    final ActivityDelivery job = job(signer);

    job.run();
    job.purge();

    verifyNoInteractions(repository, deliveryService);
  }

  @Test
  void idleWithoutASigner_leavingDeliveriesPending() {
    final ActivityDelivery job = job(null);

    job.run();
    job.run(); // warns once, still does nothing

    verifyNoInteractions(repository, deliveryService);
  }

  @Test
  void attemptsTheDeliveriesItClaims() {
    final OutboundDelivery first = delivery(1);
    final OutboundDelivery taken = delivery(2);
    final OutboundDelivery third = delivery(3);
    when(repository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            eq(DeliveryStatus.PENDING), any(), any(Pageable.class)))
        .thenReturn(List.of(first, taken, third));
    when(repository.claim(anyInt(), any(), any())).thenReturn(1);
    when(repository.claim(eq(2), any(), any())).thenReturn(0);
    // one failing delivery doesn't stop the batch
    doThrow(new IllegalStateException("boom"))
        .when(deliveryService)
        .attempt(eq(first), eq(signer), any());

    job(signer).run();

    verify(deliveryService).attempt(eq(first), eq(signer), any());
    verify(deliveryService, never()).attempt(eq(taken), any(), any());
    verify(deliveryService).attempt(eq(third), eq(signer), any());
  }

  @Test
  void purgesFinishedDeliveries() {
    job(signer).purge();

    verify(deliveryService).purge(any(), any());
  }
}
