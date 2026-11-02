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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.core.Settings;
import com.justjournal.model.OutboundDelivery;
import com.justjournal.model.User;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.services.AvatarService;
import com.justjournal.services.EntryService;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** The actor Delete sent when a journal stops federating and when an account is deleted. */
class ActorDeletionTests {

  private static final String ACTOR = "https://www.justjournal.com/users/bob";
  private static final URI MASTODON = URI.create("https://mastodon.social/inbox");
  private static final URI GOTOSOCIAL = URI.create("https://gts.example/users/ann/inbox");
  private static final URI BROKEN = URI.create("https://broken.example/inbox");

  private final ObjectMapper mapper = new ObjectMapper();
  private final List<Map<String, String>> signedHeaders =
      Collections.synchronizedList(new ArrayList<>());
  private final DeliverySigner signer =
      (actorId, target, body) -> {
        final Map<String, String> headers =
            Map.of("Signature", "keyId=\"" + actorId + "#main-key\"");
        signedHeaders.add(headers);
        return headers;
      };

  private FederationPolicy policy;
  private FollowerService followerService;
  private FollowingService followingService;
  private DeliveryService deliveryService;
  private InboxClient inboxClient;
  private ActorDeletion deletion;
  private User bob;

  @BeforeEach
  void setUp() throws Exception {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    final ActivityStreamsObjects objects =
        new ActivityStreamsObjects(
            settings,
            mock(EntryService.class),
            mock(AvatarService.class),
            mock(UserPicRepository.class),
            mock(ActorKeyService.class));

    policy = new FederationPolicy();
    policy.setEnabled(true);
    followerService = mock(FollowerService.class);
    followingService = mock(FollowingService.class);
    deliveryService = mock(DeliveryService.class);
    inboxClient = mock(InboxClient.class);
    when(inboxClient.check(any())).thenAnswer(inv -> inv.getArgument(0));
    deletion =
        new ActorDeletion(
            policy,
            followerService,
            followingService,
            deliveryService,
            objects,
            inboxClient,
            signer);

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
  }

  private void followedFrom(final URI... inboxes) {
    when(followerService.deliveryInboxes(bob)).thenReturn(new LinkedHashSet<>(List.of(inboxes)));
  }

  private void assertActorDelete(final String json) throws Exception {
    final JsonNode delete = mapper.readTree(json);
    assertEquals(ActivityStreamsObjects.AS_CONTEXT, delete.get("@context").asText());
    assertEquals("Delete", delete.get("type").asText());
    assertTrue(delete.get("id").asText().startsWith(ACTOR + "#delete/"), json);
    assertEquals(ACTOR, delete.get("actor").asText());
    assertEquals(ACTOR, delete.get("object").asText());
    assertEquals(ActivityStreamsObjects.PUBLIC, delete.get("to").get(0).asText());
  }

  // ---- federation stopped ---------------------------------------------------------------------

  @Test
  void federationStopped_queuesAnActorDelete_dropsWhatWasQueued_andForgetsFollowers()
      throws Exception {
    followedFrom(MASTODON, GOTOSOCIAL);
    when(deliveryService.enqueue(eq(ACTOR), eq(7), anyString(), any()))
        .thenReturn(List.of(new OutboundDelivery(), new OutboundDelivery()));

    assertEquals(2, deletion.federationStopped(bob));

    final ArgumentCaptor<String> activity = ArgumentCaptor.forClass(String.class);
    verify(deliveryService)
        .enqueue(eq(ACTOR), eq(7), activity.capture(), eq(Set.of(MASTODON, GOTOSOCIAL)));
    assertActorDelete(activity.getValue());
    // the inboxes are read before the followers go, and nothing queued earlier follows the Delete
    final InOrder order = inOrder(followerService, deliveryService);
    order.verify(followerService).deliveryInboxes(bob);
    order.verify(deliveryService).cancelPending(bob);
    order.verify(followerService).removeAll(bob);
    order.verify(deliveryService).enqueue(eq(ACTOR), eq(7), anyString(), any());
    verify(followingService).removeAll(bob);
  }

  @Test
  void federationStopped_alsoTellsTheServersOfFollowedAccounts_andForgetsTheFollows()
      throws Exception {
    final URI followed = URI.create("https://followed.example/inbox");
    followedFrom(MASTODON);
    when(followingService.deliveryInboxes(bob))
        .thenReturn(new LinkedHashSet<>(List.of(MASTODON, followed)));

    deletion.federationStopped(bob);

    final ArgumentCaptor<String> activity = ArgumentCaptor.forClass(String.class);
    verify(deliveryService)
        .enqueue(eq(ACTOR), eq(7), activity.capture(), eq(Set.of(MASTODON, followed)));
    assertActorDelete(activity.getValue());
    // read before the follows go; no Undo is queued, the actor Delete covers them
    final InOrder order = inOrder(followingService, deliveryService);
    order.verify(followingService).deliveryInboxes(bob);
    order.verify(followingService).removeAll(bob);
    verify(deliveryService).enqueue(anyString(), anyInt(), anyString(), any());
  }

  @Test
  void federationStopped_withNoFollowers_queuesNothing() {
    followedFrom();

    assertEquals(0, deletion.federationStopped(bob));

    verify(deliveryService).cancelPending(bob);
    verify(deliveryService, never()).enqueue(anyString(), anyInt(), anyString(), any());
  }

  @Test
  void federationStopped_whileTheSiteDoesNotFederate_sendsNothing() {
    policy.setEnabled(false);

    assertEquals(0, deletion.federationStopped(bob));
    assertEquals(0, deletion.federationStopped(null));

    verifyNoInteractions(followerService, followingService, deliveryService);
  }

  // ---- account deleted ------------------------------------------------------------------------

  @Test
  void accountDeleting_postsASignedActorDeleteToEachServerStraightAway() throws Exception {
    followedFrom(MASTODON, GOTOSOCIAL);
    when(inboxClient.post(any(), any(), any())).thenReturn(202);

    assertEquals(2, deletion.accountDeleting(bob));

    final ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
    verify(inboxClient).post(eq(MASTODON), body.capture(), eq(signedHeaders.get(0)));
    verify(inboxClient).post(eq(GOTOSOCIAL), any(), any());
    assertActorDelete(new String(body.getValue(), StandardCharsets.UTF_8));
    assertEquals(2, signedHeaders.size());
    assertEquals("keyId=\"" + ACTOR + "#main-key\"", signedHeaders.get(0).get("Signature"));
    // not queued: the key that signs it is about to be deleted
    verifyNoInteractions(deliveryService);
  }

  @Test
  void accountDeleting_alsoPostsToTheServersOfFollowedAccounts() throws Exception {
    final URI followed = URI.create("https://followed.example/inbox");
    when(followingService.deliveryInboxes(bob)).thenReturn(Set.of(followed));
    when(inboxClient.post(any(), any(), any())).thenReturn(202);

    assertEquals(1, deletion.accountDeleting(bob));

    verify(inboxClient).post(eq(followed), any(), any());
  }

  @Test
  void accountDeleting_countsOnlyServersThatTookIt_andNeverThrows() throws Exception {
    final URI refused = URI.create("https://refused.example/inbox");
    final URI erroring = URI.create("https://erroring.example/inbox");
    followedFrom(MASTODON, GOTOSOCIAL, BROKEN, refused, erroring);
    when(inboxClient.post(eq(MASTODON), any(), any())).thenReturn(200);
    when(inboxClient.post(eq(GOTOSOCIAL), any(), any())).thenReturn(500);
    when(inboxClient.post(eq(BROKEN), any(), any())).thenThrow(new IOException("reset"));
    when(inboxClient.check(refused)).thenThrow(new InboxRefusedException("private address"));
    when(inboxClient.post(eq(erroring), any(), any())).thenThrow(new IllegalStateException("x"));

    assertEquals(1, deletion.accountDeleting(bob));
  }

  @Test
  void accountDeleting_givesUpOnSlowServersWhenTimeIsUp() throws Exception {
    followedFrom(MASTODON, GOTOSOCIAL);
    deletion.setAccountDeleteSeconds(1);
    when(inboxClient.post(eq(MASTODON), any(), any())).thenReturn(202);
    when(inboxClient.post(eq(GOTOSOCIAL), any(), any()))
        .thenAnswer(
            inv -> {
              Thread.sleep(30_000);
              return 202;
            });

    final long start = System.nanoTime();
    assertEquals(1, deletion.accountDeleting(bob));
    final long seconds = (System.nanoTime() - start) / 1_000_000_000L;
    assertTrue(seconds < 5, "took " + seconds + "s");
  }

  @Test
  void accountDeleting_sendsNothingWithoutFollowersOrWhileTheSiteDoesNotFederate()
      throws Exception {
    followedFrom();
    assertEquals(0, deletion.accountDeleting(bob));

    followedFrom(MASTODON);
    policy.setEnabled(false);
    assertEquals(0, deletion.accountDeleting(bob));
    assertEquals(0, deletion.accountDeleting(null));

    verify(inboxClient, never()).post(any(), any(), any());
  }

  @Test
  void accountDeleting_aFailureReadingFollowers_doesNotStopTheDeletion() {
    when(followerService.deliveryInboxes(bob)).thenThrow(new IllegalStateException("db down"));

    assertEquals(0, deletion.accountDeleting(bob));
  }
}
