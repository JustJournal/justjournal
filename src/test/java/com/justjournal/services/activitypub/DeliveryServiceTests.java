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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.DeliveryStatus;
import com.justjournal.model.OutboundDelivery;
import com.justjournal.model.User;
import com.justjournal.repository.OutboundDeliveryRepository;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Queueing activities, and what each kind of answer from the inbox does to a delivery. */
class DeliveryServiceTests {

  private static final String ACTOR = "https://www.justjournal.com/users/bob";
  private static final String INBOX = "https://mastodon.social/inbox";
  private static final String ACTIVITY = "{\"type\":\"Create\",\"name\":\"Café\"}";
  private static final Date NOW = new Date(1_800_000_000_000L);

  private OutboundDeliveryRepository repository;
  private FollowerService followerService;
  private InboxClient inboxClient;
  private DeliveryService service;
  private final DeliverySigner signer =
      (actorId, target, body) -> Map.of("Signature", "keyId=\"" + actorId + "#main-key\"");

  @BeforeEach
  void setUp() throws Exception {
    repository = mock(OutboundDeliveryRepository.class);
    followerService = mock(FollowerService.class);
    inboxClient = mock(InboxClient.class);
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    when(repository.saveAll(any())).thenAnswer(inv -> new ArrayList<>(inv.getArgument(0)));
    when(inboxClient.check(any())).thenAnswer(inv -> inv.getArgument(0));
    service = new DeliveryService(repository, followerService, inboxClient, settings);
  }

  private static OutboundDelivery pending(final int attempts) {
    final OutboundDelivery delivery = new OutboundDelivery();
    delivery.setId(42);
    delivery.setUserId(7);
    delivery.setActorId(ACTOR);
    delivery.setTargetInbox(INBOX);
    delivery.setActivity(ACTIVITY);
    delivery.setAttempts(attempts);
    delivery.setNextAttemptAt(NOW);
    return delivery;
  }

  private DeliveryStatus answer(final OutboundDelivery delivery, final int status)
      throws Exception {
    when(inboxClient.post(any(), any(), any())).thenReturn(status);
    return service.attempt(delivery, signer, NOW);
  }

  private static Date after(final Duration wait) {
    return new Date(NOW.getTime() + wait.toMillis());
  }

  // ---- queueing --------------------------------------------------------------------------

  @Test
  void enqueue_queuesEachInboxOnce() {
    final List<OutboundDelivery> queued =
        service.enqueue(
            ACTOR,
            7,
            ACTIVITY,
            Arrays.asList(
                URI.create(INBOX),
                URI.create("https://other.example/inbox"),
                URI.create(INBOX),
                null,
                URI.create("/relative"),
                URI.create("https://other.example/inbox")));

    assertEquals(2, queued.size());
    assertEquals(INBOX, queued.get(0).getTargetInbox());
    assertEquals("https://other.example/inbox", queued.get(1).getTargetInbox());
    for (final OutboundDelivery delivery : queued) {
      assertEquals(ACTOR, delivery.getActorId());
      assertEquals(7, delivery.getUserId());
      assertEquals(ACTIVITY, delivery.getActivity());
      assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
      assertEquals(0, delivery.getAttempts());
      assertTrue(delivery.getNextAttemptAt().getTime() <= System.currentTimeMillis());
    }
  }

  @Test
  void enqueue_forTheInstanceActor_hasNoUser() {
    final List<OutboundDelivery> queued =
        service.enqueue(
            "https://www.justjournal.com/actor", null, ACTIVITY, List.of(URI.create(INBOX)));

    assertNull(queued.get(0).getUserId());
  }

  @Test
  void enqueue_withNoInboxes_queuesNothing() {
    assertTrue(service.enqueue(ACTOR, 7, ACTIVITY, List.of()).isEmpty());
    assertTrue(service.enqueue(ACTOR, 7, ACTIVITY, null).isEmpty());
    verify(repository, never()).saveAll(any());
  }

  @Test
  void enqueue_needsASenderAndAnActivity() {
    final List<URI> inboxes = List.of(URI.create(INBOX));
    assertThrows(
        IllegalArgumentException.class, () -> service.enqueue(" ", 7, ACTIVITY, inboxes));
    assertThrows(IllegalArgumentException.class, () -> service.enqueue(ACTOR, 7, "", inboxes));
  }

  @Test
  void enqueueToFollowers_sendsFromTheUsersActorToTheirInboxes() {
    final User bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    when(followerService.deliveryInboxes(bob))
        .thenReturn(
            new LinkedHashSet<>(
                List.of(URI.create(INBOX), URI.create("https://small.example/u/c/inbox"))));

    final List<OutboundDelivery> queued = service.enqueueToFollowers(bob, ACTIVITY);

    assertEquals(2, queued.size());
    assertEquals(ACTOR, queued.get(0).getActorId());
    assertEquals(7, queued.get(0).getUserId());
    assertEquals("https://small.example/u/c/inbox", queued.get(1).getTargetInbox());
  }

  // ---- the backoff schedule ---------------------------------------------------------------

  @Test
  void backoff_growsThenGivesUpAfterAboutTwoDays() {
    assertEquals(Duration.ofMinutes(1), DeliveryService.backoff(1));
    assertEquals(Duration.ofMinutes(5), DeliveryService.backoff(2));
    assertEquals(Duration.ofMinutes(30), DeliveryService.backoff(3));
    assertEquals(Duration.ofHours(2), DeliveryService.backoff(4));
    assertEquals(Duration.ofHours(12), DeliveryService.backoff(5));
    assertEquals(Duration.ofHours(12), DeliveryService.backoff(8));
    assertNull(DeliveryService.backoff(9));
    assertEquals(9, DeliveryService.MAX_ATTEMPTS);

    Duration total = Duration.ZERO;
    for (int attempt = 1; attempt < DeliveryService.MAX_ATTEMPTS; attempt++) {
      total = total.plus(DeliveryService.backoff(attempt));
    }
    // the last attempt comes a little over two days after the first
    assertTrue(total.compareTo(Duration.ofHours(48)) > 0, total.toString());
    assertTrue(total.compareTo(Duration.ofHours(56)) < 0, total.toString());
  }

  // ---- status transitions -----------------------------------------------------------------

  @Test
  void success_isDelivered() throws Exception {
    final OutboundDelivery delivery = pending(0);

    assertEquals(DeliveryStatus.DELIVERED, answer(delivery, 202));

    verify(repository).record(42, DeliveryStatus.DELIVERED, 1, NOW, null, NOW);
  }

  @Test
  void success_postsTheStoredActivityWithTheSignersHeaders() throws Exception {
    answer(pending(0), 200);

    final ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
    verify(inboxClient).post(eq(URI.create(INBOX)), body.capture(), headers.capture());
    assertEquals(ACTIVITY, new String(body.getValue(), StandardCharsets.UTF_8));
    assertEquals("keyId=\"" + ACTOR + "#main-key\"", headers.getValue().get("Signature"));
  }

  @Test
  void serverError_isRetriedAfterTheFirstWait() throws Exception {
    assertEquals(DeliveryStatus.PENDING, answer(pending(0), 503));

    verify(repository)
        .record(
            42, DeliveryStatus.PENDING, 1, after(Duration.ofMinutes(1)), "HTTP 503", null);
  }

  @Test
  void tooManyRequests_isRetried() throws Exception {
    assertEquals(DeliveryStatus.PENDING, answer(pending(2), 429));

    verify(repository)
        .record(
            42, DeliveryStatus.PENDING, 3, after(Duration.ofMinutes(30)), "HTTP 429", null);
  }

  @Test
  void otherClientErrors_failAtOnce() throws Exception {
    for (final int status : new int[] {400, 401, 403, 404, 410, 422}) {
      assertEquals(DeliveryStatus.FAILED, answer(pending(0), status), "HTTP " + status);
      verify(repository).record(42, DeliveryStatus.FAILED, 1, NOW, "HTTP " + status, NOW);
    }
  }

  @Test
  void aRedirect_isNotFollowedAndFails() throws Exception {
    assertEquals(DeliveryStatus.FAILED, answer(pending(0), 301));

    verify(repository).record(42, DeliveryStatus.FAILED, 1, NOW, "HTTP 301", NOW);
  }

  @Test
  void aTimeout_isRetried() throws Exception {
    when(inboxClient.post(any(), any(), any()))
        .thenThrow(new SocketTimeoutException("Read timed out"));

    assertEquals(DeliveryStatus.PENDING, service.attempt(pending(3), signer, NOW));

    verify(repository)
        .record(
            42,
            DeliveryStatus.PENDING,
            4,
            after(Duration.ofHours(2)),
            "SocketTimeoutException: Read timed out",
            null);
  }

  @Test
  void aConnectionError_isRetried() throws Exception {
    when(inboxClient.post(any(), any(), any())).thenThrow(new IOException("Connection refused"));

    assertEquals(DeliveryStatus.PENDING, service.attempt(pending(4), signer, NOW));

    verify(repository)
        .record(
            42,
            DeliveryStatus.PENDING,
            5,
            after(Duration.ofHours(12)),
            "IOException: Connection refused",
            null);
  }

  @Test
  void theLastTemporaryFailure_givesUp() throws Exception {
    assertEquals(
        DeliveryStatus.FAILED, answer(pending(DeliveryService.MAX_ATTEMPTS - 1), 500));

    verify(repository)
        .record(
            42,
            DeliveryStatus.FAILED,
            DeliveryService.MAX_ATTEMPTS,
            NOW,
            "Gave up after 9 attempts: HTTP 500",
            NOW);
  }

  @Test
  void aRefusedInbox_failsWithoutSigningOrPosting() throws Exception {
    final DeliverySigner refusingSigner = mock(DeliverySigner.class);
    when(inboxClient.check(any())).thenThrow(new InboxRefusedException("Inbox is not https"));

    assertEquals(DeliveryStatus.FAILED, service.attempt(pending(0), refusingSigner, NOW));

    verify(repository).record(42, DeliveryStatus.FAILED, 1, NOW, "Inbox is not https", NOW);
    verify(refusingSigner, never()).sign(any(), any(), any());
    verify(inboxClient, never()).post(any(), any(), any());
  }

  @Test
  void anAddressRefusedWhenConnecting_fails() throws Exception {
    when(inboxClient.post(any(), any(), any()))
        .thenThrow(new InboxRefusedException("Inbox host does not resolve to a public address"));

    assertEquals(DeliveryStatus.FAILED, service.attempt(pending(0), signer, NOW));
  }

  @Test
  void aSignerFailure_isRetried() throws Exception {
    final DeliverySigner broken =
        (actorId, target, body) -> {
          throw new IllegalStateException("no key");
        };

    assertEquals(DeliveryStatus.PENDING, service.attempt(pending(0), broken, NOW));
    verify(inboxClient, never()).post(any(), any(), any());
  }

  @Test
  void aLongError_isCutToTheColumnSize() throws Exception {
    when(inboxClient.post(any(), any(), any())).thenThrow(new IOException("x".repeat(1000)));

    service.attempt(pending(0), signer, NOW);

    final ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
    verify(repository)
        .record(anyInt(), any(), anyInt(), any(), error.capture(), isNull());
    assertEquals(OutboundDelivery.MAX_ERROR_LENGTH, error.getValue().length());
  }

  @Test
  void purge_removesOldDeliveredAndFailedRows() {
    final Date delivered = new Date(1);
    final Date failed = new Date(2);
    when(repository.deleteFinished(DeliveryStatus.DELIVERED, delivered)).thenReturn(3);
    when(repository.deleteFinished(DeliveryStatus.FAILED, failed)).thenReturn(1);

    assertEquals(4, service.purge(delivered, failed));
  }
}
