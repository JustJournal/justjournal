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
package com.justjournal.ctl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.model.Journal;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.activitypub.ActivityPubFetcher;
import com.justjournal.services.activitypub.ActorKeyService;
import com.justjournal.services.activitypub.DeliveryService;
import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.FollowerService;
import com.justjournal.services.activitypub.HttpSignatureException;
import com.justjournal.services.activitypub.HttpSignatureException.Reason;
import com.justjournal.services.activitypub.HttpSignatureService;
import com.justjournal.services.activitypub.InboxService;
import com.justjournal.services.activitypub.RemoteReplyService;
import com.justjournal.services.activitypub.SeenActivities;
import com.justjournal.services.activitypub.VerifiedSignature;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InboxControllerTests {

  private static final String ACTIVITY_JSON = "application/activity+json";
  private static final String BOB = "https://www.justjournal.com/users/bob";
  private static final String ALICE = "https://remote.social/users/alice";
  private static final String ALICE_INBOX = ALICE + "/inbox";
  private static final String SHARED_INBOX = "https://remote.social/inbox";
  private static final String FOLLOW_ID = "https://remote.social/0f6d1c2e";
  private static final String SIGNATURE = "keyId=\"" + ALICE + "#main-key\",signature=\"abc\"";

  private final ObjectMapper mapper = new ObjectMapper();
  private UserRepository userRepository;
  private FederationPolicy federationPolicy;
  private HttpSignatureService httpSignatureService;
  private FollowerService followerService;
  private DeliveryService deliveryService;
  private ActivityPubFetcher fetcher;
  private InboxController controller;
  private MockMvc mockMvc;
  private User bob;
  private Journal journal;

  @BeforeEach
  void setUp() throws Exception {
    userRepository = mock(UserRepository.class);
    federationPolicy = mock(FederationPolicy.class);
    httpSignatureService = mock(HttpSignatureService.class);
    followerService = mock(FollowerService.class);
    deliveryService = mock(DeliveryService.class);
    fetcher = mock(ActivityPubFetcher.class);
    final ActorKeyService actorKeyService = mock(ActorKeyService.class);
    when(actorKeyService.userActorId("bob")).thenReturn(BOB);

    final InboxService inboxService =
        new InboxService(
            followerService,
            deliveryService,
            federationPolicy,
            actorKeyService,
            fetcher,
            mock(RemoteReplyService.class));
    controller =
        new InboxController(
            userRepository,
            federationPolicy,
            httpSignatureService,
            new SeenActivities(new ConcurrentMapCacheManager(SeenActivities.CACHE)),
            inboxService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    journal = new Journal();
    journal.setUser(bob);
    bob.getJournals().add(journal);
    when(userRepository.findByUsername("bob")).thenReturn(bob);

    when(federationPolicy.isEnabled()).thenReturn(true);
    when(federationPolicy.federates(bob)).thenReturn(true);
    when(httpSignatureService.verify(any(), any(), any(), any()))
        .thenReturn(new VerifiedSignature(ALICE + "#main-key", ALICE));
    when(fetcher.normalize(anyString()))
        .thenAnswer(
            inv -> {
              final URI uri = URI.create(inv.getArgument(0));
              return new NormalizedUrl(uri, uri, uri.getHost());
            });
    serveActor(aliceActor());
  }

  // ---- fixtures -------------------------------------------------------------------------

  private ObjectNode aliceActor() {
    final ObjectNode actor = mapper.createObjectNode();
    actor.put("id", ALICE);
    actor.put("type", "Person");
    actor.put("inbox", ALICE_INBOX);
    actor.putObject("endpoints").put("sharedInbox", SHARED_INBOX);
    return actor;
  }

  private void serveActor(final JsonNode actor) throws Exception {
    doReturn(
            new SafeHttpFetcher.Result(
                URI.create(ALICE),
                ACTIVITY_JSON,
                StandardCharsets.UTF_8,
                mapper.writeValueAsBytes(actor)))
        .when(fetcher)
        .signedGet(any(), anyInt());
  }

  private ObjectNode follow() {
    final ObjectNode follow = mapper.createObjectNode();
    follow.put("@context", "https://www.w3.org/ns/activitystreams");
    follow.put("id", FOLLOW_ID);
    follow.put("type", "Follow");
    follow.put("actor", ALICE);
    follow.put("object", BOB);
    return follow;
  }

  private ObjectNode activity(final String type, final String id, final JsonNode object) {
    final ObjectNode activity = mapper.createObjectNode();
    activity.put("@context", "https://www.w3.org/ns/activitystreams");
    if (id != null) activity.put("id", id);
    activity.put("type", type);
    activity.put("actor", ALICE);
    activity.set("object", object);
    return activity;
  }

  private ResultActions send(final String path, final Object body) throws Exception {
    return send(path, ACTIVITY_JSON, body, SIGNATURE);
  }

  private ResultActions send(
      final String path, final String contentType, final Object body, final String signature)
      throws Exception {
    final String content = body instanceof String text ? text : mapper.writeValueAsString(body);
    return mockMvc.perform(
        post(path)
            .contentType(contentType)
            .header("Signature", signature)
            .header("Date", "Mon, 02 Nov 2026 12:00:00 GMT")
            .content(content));
  }

  private void refuseSignature(final Reason reason) throws Exception {
    doThrow(new HttpSignatureException(reason, "no"))
        .when(httpSignatureService)
        .verify(any(), any(), any(), any());
  }

  /** The one activity queued, after checking who sends it and where. */
  private JsonNode queuedTo(final String inbox) throws Exception {
    final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Collection<URI>> inboxes = ArgumentCaptor.forClass(Collection.class);
    verify(deliveryService).enqueue(eq(BOB), eq(7), json.capture(), inboxes.capture());
    assertEquals(List.of(URI.create(inbox)), List.copyOf(inboxes.getValue()));
    return mapper.readTree(json.getValue());
  }

  private void nothingDone() {
    verify(followerService, never()).add(any(), any(), any(), any());
    verify(followerService, never()).remove(any(), any());
    verify(followerService, never()).removeEverywhere(any());
    verifyNoInteractions(deliveryService);
  }

  // ---- site switch, users, methods ------------------------------------------------------

  @Test
  void federationOff_takesNothing() throws Exception {
    when(federationPolicy.isEnabled()).thenReturn(false);

    send("/users/bob/inbox", follow())
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, ""));
    mockMvc
        .perform(get("/users/bob/inbox"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, ""));
    send("/actor/inbox", follow())
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, ""));
    send("/users/nobody/inbox", follow()).andExpect(status().isNotFound());

    verifyNoInteractions(httpSignatureService);
    nothingDone();
  }

  @Test
  void unknownOrHiddenUser_isNotFound() throws Exception {
    send("/users/nobody/inbox", follow()).andExpect(status().isNotFound());
    send("/users/no%20one/inbox", follow()).andExpect(status().isNotFound());
    journal.setOwnerViewOnly(true);
    send("/users/bob/inbox", follow()).andExpect(status().isNotFound());

    verifyNoInteractions(httpSignatureService);
    nothingDone();
  }

  @Test
  void get_isNotAllowed() throws Exception {
    mockMvc
        .perform(get("/users/bob/inbox"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, "POST"));
  }

  // ---- what a request must be -----------------------------------------------------------

  @Test
  void tooLarge_isRefusedBeforeVerifying() throws Exception {
    controller.setMaxBytes(100);
    final ObjectNode follow = follow();
    follow.put("summary", "x".repeat(200));

    send("/users/bob/inbox", follow).andExpect(status().isPayloadTooLarge());

    verifyNoInteractions(httpSignatureService);
    nothingDone();
  }

  @Test
  void otherContentTypes_areUnsupported() throws Exception {
    for (final String type :
        List.of("application/json", "text/plain", "application/x-www-form-urlencoded")) {
      send("/users/bob/inbox", type, follow(), SIGNATURE)
          .andExpect(status().isUnsupportedMediaType());
    }
    mockMvc
        .perform(post("/users/bob/inbox").content(mapper.writeValueAsString(follow())))
        .andExpect(status().isUnsupportedMediaType());
    verifyNoInteractions(httpSignatureService);
    nothingDone();
  }

  @Test
  void ldJsonWithAProfile_isTaken() throws Exception {
    send(
            "/users/bob/inbox",
            "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"",
            follow(),
            SIGNATURE)
        .andExpect(status().isAccepted());
    verify(followerService).add(any(), any(), any(), any());
  }

  @Test
  void malformed_isABadRequest() throws Exception {
    final ObjectNode noActor = follow();
    noActor.remove("actor");
    final ObjectNode noType = follow();
    noType.remove("type");
    final ObjectNode noObject = follow();
    noObject.remove("object");
    for (final Object body :
        List.of("{\"type\":", "[]", "\"Follow\"", "{} {}", "", noActor, noType, noObject)) {
      send("/users/bob/inbox", body).andExpect(status().isBadRequest());
    }
    nothingDone();
  }

  // ---- signatures -----------------------------------------------------------------------

  @Test
  void verifies_theExactRequest() throws Exception {
    final String body = mapper.writeValueAsString(follow());
    mockMvc
        .perform(
            post("/users/bob/inbox?x=1")
                .contentType(ACTIVITY_JSON)
                .header("Signature", SIGNATURE)
                .content(body))
        .andExpect(status().isAccepted());

    final ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, List<String>>> headers = ArgumentCaptor.forClass(Map.class);
    final ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(httpSignatureService)
        .verify(eq("POST"), path.capture(), headers.capture(), bytes.capture());
    assertEquals("/users/bob/inbox?x=1", path.getValue());
    assertEquals(List.of(SIGNATURE), headers.getValue().get("Signature"));
    assertArrayEquals(body.getBytes(StandardCharsets.UTF_8), bytes.getValue());
  }

  @Test
  void badSignature_isUnauthorized() throws Exception {
    refuseSignature(Reason.BAD_SIGNATURE);
    send("/users/bob/inbox", follow()).andExpect(status().isUnauthorized());
    send("/actor/inbox", follow()).andExpect(status().isUnauthorized());
    nothingDone();
  }

  @Test
  void actorOtherThanTheSigner_isUnauthorized() throws Exception {
    final ObjectNode follow = follow();
    follow.put("actor", "https://remote.social/users/mallory");
    send("/users/bob/inbox", follow).andExpect(status().isUnauthorized());
    nothingDone();
  }

  @Test
  void replay_isAcceptedButIgnored() throws Exception {
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());

    verify(followerService, times(1)).add(any(), any(), any(), any());
    verify(deliveryService, times(1)).enqueue(any(), any(), any(), any());
  }

  @Test
  void replayWithoutAnId_isKnownByItsSignature() throws Exception {
    final ObjectNode follow = follow();
    follow.remove("id");
    send("/users/bob/inbox", follow).andExpect(status().isAccepted());
    send("/users/bob/inbox", follow).andExpect(status().isAccepted());
    verify(followerService, times(1)).add(any(), any(), any(), any());

    send("/users/bob/inbox", ACTIVITY_JSON, follow, SIGNATURE + ",x=\"1\"")
        .andExpect(status().isAccepted());
    verify(followerService, times(2)).add(any(), any(), any(), any());
  }

  // ---- Follow ---------------------------------------------------------------------------

  @Test
  void follow_whenTheJournalFederates_isStoredAndAccepted() throws Exception {
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());

    verify(followerService)
        .add(bob, URI.create(ALICE), URI.create(ALICE_INBOX), URI.create(SHARED_INBOX));
    final JsonNode accept = queuedTo(ALICE_INBOX);
    assertEquals("https://www.w3.org/ns/activitystreams", accept.get("@context").asText());
    assertEquals("Accept", accept.get("type").asText());
    assertEquals(BOB, accept.get("actor").asText());
    assertTrue(accept.get("id").asText().startsWith(BOB + "#accepts/"));
    final JsonNode object = accept.get("object");
    assertEquals(FOLLOW_ID, object.get("id").asText());
    assertEquals("Follow", object.get("type").asText());
    assertEquals(ALICE, object.get("actor").asText());
    assertEquals(BOB, object.get("object").asText());
  }

  @Test
  void follow_acceptIdsAreUnique() throws Exception {
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());
    final ObjectNode again = follow();
    again.put("id", FOLLOW_ID + "-2");
    send("/users/bob/inbox", again).andExpect(status().isAccepted());

    final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(deliveryService, times(2)).enqueue(eq(BOB), eq(7), json.capture(), any());
    assertNotEquals(
        mapper.readTree(json.getAllValues().get(0)).get("id"),
        mapper.readTree(json.getAllValues().get(1)).get("id"));
  }

  @Test
  void follow_withoutASharedInbox_isStored() throws Exception {
    final ObjectNode actor = aliceActor();
    actor.remove("endpoints");
    serveActor(actor);
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());
    verify(followerService).add(bob, URI.create(ALICE), URI.create(ALICE_INBOX), null);
  }

  @Test
  void follow_whenTheJournalDoesNotFederate_isRejected() throws Exception {
    when(federationPolicy.federates(bob)).thenReturn(false);

    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());

    verify(followerService, never()).add(any(), any(), any(), any());
    final JsonNode reject = queuedTo(ALICE_INBOX);
    assertEquals("Reject", reject.get("type").asText());
    assertEquals(BOB, reject.get("actor").asText());
    assertTrue(reject.get("id").asText().startsWith(BOB + "#rejects/"));
    assertEquals(FOLLOW_ID, reject.get("object").get("id").asText());
  }

  @Test
  void follow_ofSomeoneElse_isIgnored() throws Exception {
    final ObjectNode follow = follow();
    follow.put("object", "https://www.justjournal.com/users/carol");
    send("/users/bob/inbox", follow).andExpect(status().isAccepted());
    verify(fetcher, never()).signedGet(any(), anyInt());
    nothingDone();
  }

  @Test
  void follow_fromAnActorWhoseDocumentIsSomeoneElse_isIgnored() throws Exception {
    final ObjectNode actor = aliceActor();
    actor.put("id", "https://remote.social/users/mallory");
    serveActor(actor);
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());
    nothingDone();
  }

  @Test
  void follow_fromAnActorWithoutAnInbox_isIgnored() throws Exception {
    final ObjectNode actor = aliceActor();
    actor.put("inbox", "ftp://remote.social/inbox");
    serveActor(actor);
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());
    nothingDone();
  }

  @Test
  void follow_whoseActorCantBeFetchedForNow_isRetried() throws Exception {
    doThrow(new IOException("timed out")).when(fetcher).signedGet(any(), anyInt());
    send("/users/bob/inbox", follow()).andExpect(status().isServiceUnavailable());
    nothingDone();

    // the sender's retry is not taken for a replay
    serveActor(aliceActor());
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());
    verify(followerService).add(any(), any(), any(), any());
  }

  @Test
  void follow_whoseActorIsGone_isIgnored() throws Exception {
    when(fetcher.signedGet(any(), anyInt()))
        .thenThrow(new FetchException(410, true, "HTTP status 410"));
    send("/users/bob/inbox", follow()).andExpect(status().isAccepted());
    nothingDone();
  }

  // ---- Undo -----------------------------------------------------------------------------

  @Test
  void undoOfAFollow_removesTheFollower() throws Exception {
    send("/users/bob/inbox", activity("Undo", ALICE + "#follows/1/undo", follow()))
        .andExpect(status().isAccepted());
    verify(followerService).remove(bob, URI.create(ALICE));
    verifyNoInteractions(deliveryService);
  }

  @Test
  void undoOfSomethingElse_isIgnored() throws Exception {
    final ObjectNode like = follow();
    like.put("type", "Like");
    final ObjectNode someoneElses = follow();
    someoneElses.put("actor", "https://remote.social/users/mallory");
    final ObjectNode ofCarol = follow();
    ofCarol.put("object", "https://www.justjournal.com/users/carol");
    int n = 0;
    for (final JsonNode object :
        List.of(like, someoneElses, ofCarol, mapper.getNodeFactory().textNode(FOLLOW_ID))) {
      send("/users/bob/inbox", activity("Undo", ALICE + "#undo/" + n++, object))
          .andExpect(status().isAccepted());
    }
    nothingDone();
  }

  // ---- Delete ---------------------------------------------------------------------------

  @Test
  void deleteOfTheSender_removesItEverywhere() throws Exception {
    send(
            "/users/bob/inbox",
            activity("Delete", ALICE + "#delete", mapper.getNodeFactory().textNode(ALICE)))
        .andExpect(status().isAccepted());
    verify(followerService).removeEverywhere(URI.create(ALICE));
  }

  @Test
  void deleteOfSomethingElse_isIgnored() throws Exception {
    send(
            "/users/bob/inbox",
            activity(
                "Delete",
                ALICE + "/statuses/1#delete",
                mapper.getNodeFactory().textNode(ALICE + "/statuses/1")))
        .andExpect(status().isAccepted());
    nothingDone();
  }

  @Test
  void deleteOfAGoneActor_withoutAVerifiableSignature_removesIt() throws Exception {
    refuseSignature(Reason.KEY_UNAVAILABLE);
    when(followerService.followsAny(URI.create(ALICE))).thenReturn(true);
    when(fetcher.unsignedGet(any(), anyInt()))
        .thenThrow(new FetchException(410, true, "HTTP status 410"));

    send(
            "/users/bob/inbox",
            activity("Delete", ALICE + "#delete", mapper.getNodeFactory().textNode(ALICE)))
        .andExpect(status().isAccepted());

    verify(followerService).removeEverywhere(URI.create(ALICE));
    final ArgumentCaptor<NormalizedUrl> url = ArgumentCaptor.forClass(NormalizedUrl.class);
    verify(fetcher).unsignedGet(url.capture(), anyInt());
    assertEquals(URI.create(ALICE), url.getValue().fetchUri());
  }

  @Test
  void deleteOfALiveActor_withoutAVerifiableSignature_isRefused() throws Exception {
    refuseSignature(Reason.KEY_UNAVAILABLE);
    when(followerService.followsAny(URI.create(ALICE))).thenReturn(true);
    when(fetcher.unsignedGet(any(), anyInt()))
        .thenReturn(
            new SafeHttpFetcher.Result(
                URI.create(ALICE), ACTIVITY_JSON, StandardCharsets.UTF_8, "{}".getBytes()));
    final ObjectNode delete =
        activity("Delete", ALICE + "#delete", mapper.getNodeFactory().textNode(ALICE));

    send("/users/bob/inbox", delete).andExpect(status().isUnauthorized());

    // nor when the actor can't be reached, or answers something else
    doThrow(new IOException("timed out")).when(fetcher).unsignedGet(any(), anyInt());
    send("/users/bob/inbox", delete).andExpect(status().isUnauthorized());
    doThrow(new FetchException(401, true, "HTTP status 401"))
        .when(fetcher)
        .unsignedGet(any(), anyInt());
    send("/users/bob/inbox", delete).andExpect(status().isUnauthorized());

    verify(followerService, never()).removeEverywhere(any());
  }

  @Test
  void deleteWithABadSignature_isRefusedWithoutFetching() throws Exception {
    refuseSignature(Reason.BAD_SIGNATURE);
    when(followerService.followsAny(URI.create(ALICE))).thenReturn(true);
    send(
            "/users/bob/inbox",
            activity("Delete", ALICE + "#delete", mapper.getNodeFactory().textNode(ALICE)))
        .andExpect(status().isUnauthorized());
    verify(fetcher, never()).unsignedGet(any(), anyInt());
    verify(followerService, never()).removeEverywhere(any());
  }

  @Test
  void deleteOfAnActorFollowingNobody_isRefusedWithoutFetching() throws Exception {
    refuseSignature(Reason.KEY_UNAVAILABLE);
    send(
            "/users/bob/inbox",
            activity("Delete", ALICE + "#delete", mapper.getNodeFactory().textNode(ALICE)))
        .andExpect(status().isUnauthorized());
    verify(fetcher, never()).unsignedGet(any(), anyInt());
  }

  // ---- everything else ------------------------------------------------------------------

  @Test
  void unknownActivity_isAcceptedAndIgnored() throws Exception {
    send(
            "/users/bob/inbox",
            activity(
                "Like", ALICE + "#likes/1", mapper.getNodeFactory().textNode(BOB + "/entry/1")))
        .andExpect(status().isAccepted());
    send("/users/bob/inbox", activity("Create", ALICE + "/statuses/2/activity", follow()))
        .andExpect(status().isAccepted());
    verify(fetcher, never()).signedGet(any(), anyInt());
    nothingDone();
  }

  @Test
  void instanceInbox_verifiesThenIgnores() throws Exception {
    send("/actor/inbox", follow()).andExpect(status().isAccepted());
    verify(httpSignatureService).verify(eq("POST"), eq("/actor/inbox"), any(), any());
    verify(fetcher, never()).signedGet(any(), anyInt());
    nothingDone();

    mockMvc
        .perform(get("/actor/inbox"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, "POST"));
  }
}
