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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.core.Settings;
import com.justjournal.model.FollowingState;
import com.justjournal.model.RemoteFollowing;
import com.justjournal.model.User;
import com.justjournal.repository.RemoteFollowingRepository;
import com.justjournal.repository.RemotePostRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.services.AvatarService;
import com.justjournal.services.EntryService;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/** Following fediverse accounts: the Follow, the answer to it, and the Undo. */
class FollowingServiceTests {

  private static final String BOB = "https://www.justjournal.com/users/bob";
  private static final String ALICE = "https://remote.social/users/alice";
  private static final URI ALICE_INBOX = URI.create(ALICE + "/inbox");
  private static final String FOLLOW_ID = BOB + "#follows/1";

  private final ObjectMapper mapper = new ObjectMapper();
  private RemoteFollowingRepository repository;
  private RemotePostRepository postRepository;
  private RemoteAccountResolver resolver;
  private DeliveryService deliveryService;
  private FederationPolicy federationPolicy;
  private FollowingService service;
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
    repository = mock(RemoteFollowingRepository.class);
    postRepository = mock(RemotePostRepository.class);
    resolver = mock(RemoteAccountResolver.class);
    deliveryService = mock(DeliveryService.class);
    federationPolicy = mock(FederationPolicy.class);
    service =
        new FollowingService(
            repository, postRepository, resolver, deliveryService, federationPolicy, objects);

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    when(federationPolicy.federates(bob)).thenReturn(true);
    when(repository.findByUserIdAndActorId(anyInt(), anyString())).thenReturn(Optional.empty());
    when(repository.findByUserIdAndFollowId(anyInt(), anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(resolver.resolve("@alice@remote.social"))
        .thenReturn(
            new RemoteAccountResolver.RemoteAccount(
                URI.create(ALICE),
                ALICE_INBOX,
                URI.create("https://remote.social/inbox"),
                "@alice@remote.social",
                "Alice",
                "https://remote.social/@alice"));
  }

  private RemoteFollowing following(final FollowingState state) {
    final RemoteFollowing following = new RemoteFollowing();
    following.setUserId(7);
    following.setActorId(ALICE);
    following.setInbox(ALICE_INBOX.toString());
    following.setSharedInbox("https://remote.social/inbox");
    following.setFollowId(FOLLOW_ID);
    following.setState(state);
    when(repository.findByUserIdAndActorId(7, ALICE)).thenReturn(Optional.of(following));
    when(repository.findByUserIdAndFollowId(7, FOLLOW_ID)).thenReturn(Optional.of(following));
    return following;
  }

  /** The one activity queued for Alice's inbox, from Bob. */
  private JsonNode queued() throws Exception {
    final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Collection<URI>> inboxes = ArgumentCaptor.forClass(Collection.class);
    verify(deliveryService).enqueue(eq(BOB), eq(7), json.capture(), inboxes.capture());
    assertEquals(List.of(ALICE_INBOX), List.copyOf(inboxes.getValue()));
    return mapper.readTree(json.getValue());
  }

  private ObjectNode answer(final String type, final JsonNode object) {
    final ObjectNode activity = mapper.createObjectNode();
    activity.put("id", ALICE + "#" + type);
    activity.put("type", type);
    activity.put("actor", ALICE);
    activity.set("object", object);
    return activity;
  }

  private ObjectNode embeddedFollow(final String id) {
    final ObjectNode follow = mapper.createObjectNode();
    if (id != null) follow.put("id", id);
    follow.put("type", "Follow");
    follow.put("actor", BOB);
    follow.put("object", ALICE);
    return follow;
  }

  // ---- follow ---------------------------------------------------------------------------

  @Test
  void follow_storesItPending_andQueuesAFollowForTheAccountsInbox() throws Exception {
    final RemoteFollowing saved = service.follow(bob, "@alice@remote.social");

    assertEquals(7, saved.getUserId());
    assertEquals(ALICE, saved.getActorId());
    assertEquals(ALICE_INBOX.toString(), saved.getInbox());
    assertEquals("https://remote.social/inbox", saved.getSharedInbox());
    assertEquals(FollowingState.PENDING, saved.getState());
    assertEquals("@alice@remote.social", saved.getHandle());
    assertEquals("Alice", saved.getDisplayName());
    assertEquals("https://remote.social/@alice", saved.getProfileUrl());
    assertTrue(saved.getFollowId().startsWith(BOB + "#follows/"), saved.getFollowId());

    final JsonNode follow = queued();
    assertEquals(ActivityStreamsObjects.AS_CONTEXT, follow.get("@context").asText());
    assertEquals("Follow", follow.get("type").asText());
    assertEquals(saved.getFollowId(), follow.get("id").asText());
    assertEquals(BOB, follow.get("actor").asText());
    assertEquals(ALICE, follow.get("object").asText());
  }

  @Test
  void follow_whenTheJournalDoesNotFederate_isRefused() throws Exception {
    when(federationPolicy.federates(bob)).thenReturn(false);

    assertThrows(
        FollowingService.FollowRefusedException.class,
        () -> service.follow(bob, "@alice@remote.social"));
    verifyNoInteractions(resolver, deliveryService);
    verify(repository, never()).save(any());
  }

  @Test
  void follow_pastTheCap_isRefused() throws Exception {
    service.setMaxPerUser(3);
    when(repository.countByUserId(7)).thenReturn(3L);

    final FollowingService.FollowRefusedException refused =
        assertThrows(
            FollowingService.FollowRefusedException.class,
            () -> service.follow(bob, "@alice@remote.social"));
    assertTrue(refused.getMessage().contains("at most 3"));
    verifyNoInteractions(resolver, deliveryService);

    when(repository.countByUserId(7)).thenReturn(2L);
    service.follow(bob, "@alice@remote.social");
    verify(repository).save(any());
  }

  @Test
  void follow_ofAnAccountAlreadyFollowed_isRefused() throws Exception {
    following(FollowingState.ACCEPTED);

    assertThrows(
        FollowingService.FollowRefusedException.class,
        () -> service.follow(bob, "@alice@remote.social"));
    verify(repository, never()).save(any());
    verifyNoInteractions(deliveryService);
  }

  @Test
  void follow_racingAnotherRequest_isRefusedAsAlreadyFollowed() throws Exception {
    when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    final FollowingService.FollowRefusedException refused =
        assertThrows(
            FollowingService.FollowRefusedException.class,
            () -> service.follow(bob, "@alice@remote.social"));
    assertEquals("You already follow that account.", refused.getMessage());
    verifyNoInteractions(deliveryService);
  }

  @Test
  void follow_ofAnAccountThatCantBeFollowed_isRefusedWithTheReason() throws Exception {
    when(resolver.resolve("@bob@justjournal.com"))
        .thenThrow(new RemoteAccountResolver.RefusedException("That account is on this site."));
    when(resolver.resolve("@alice@down.example")).thenThrow(new IOException("timed out"));

    final FollowingService.FollowRefusedException refused =
        assertThrows(
            FollowingService.FollowRefusedException.class,
            () -> service.follow(bob, "@bob@justjournal.com"));
    assertEquals("That account is on this site.", refused.getMessage());
    assertThrows(IOException.class, () -> service.follow(bob, "@alice@down.example"));
    verify(repository, never()).save(any());
    verifyNoInteractions(deliveryService);
  }

  @Test
  void follow_thatCantBeQueued_isNotKept() throws Exception {
    when(deliveryService.enqueue(anyString(), any(), anyString(), any()))
        .thenThrow(new IllegalStateException("db"));

    assertThrows(IllegalStateException.class, () -> service.follow(bob, "@alice@remote.social"));
    verify(repository).delete(any(RemoteFollowing.class));
  }

  // ---- unfollow -------------------------------------------------------------------------

  @Test
  void unfollow_queuesAnUndoEmbeddingTheFollow_andForgetsTheAccount() throws Exception {
    following(FollowingState.ACCEPTED);

    assertTrue(service.unfollow(bob, ALICE));

    final JsonNode undo = queued();
    assertEquals("Undo", undo.get("type").asText());
    assertEquals(BOB, undo.get("actor").asText());
    assertTrue(undo.get("id").asText().startsWith(BOB + "#undos/"));
    final JsonNode follow = undo.get("object");
    assertEquals("Follow", follow.get("type").asText());
    assertEquals(FOLLOW_ID, follow.get("id").asText());
    assertEquals(BOB, follow.get("actor").asText());
    assertEquals(ALICE, follow.get("object").asText());
    assertFalse(follow.has("@context"));
    verify(repository).deleteByUserIdAndActorId(7, ALICE);
    // nobody else here follows Alice, so her posts go too
    verify(postRepository).deleteByActorId(ALICE);
  }

  @Test
  void unfollow_keepsPostsOthersStillFollow() {
    following(FollowingState.PENDING);
    when(repository.existsByActorIdAndState(ALICE, FollowingState.ACCEPTED)).thenReturn(true);

    assertTrue(service.unfollow(bob, ALICE));

    verify(deliveryService).enqueue(eq(BOB), eq(7), anyString(), any());
    verify(postRepository, never()).deleteByActorId(any());
  }

  @Test
  void unfollow_ofADeclinedFollow_sendsNothing() {
    following(FollowingState.REJECTED);

    assertTrue(service.unfollow(bob, ALICE));

    verifyNoInteractions(deliveryService);
    verify(repository).deleteByUserIdAndActorId(7, ALICE);
  }

  @Test
  void unfollow_ofAnAccountNotFollowed_doesNothing() {
    assertFalse(service.unfollow(bob, ALICE));
    assertFalse(service.unfollow(bob, null));
    verifyNoInteractions(deliveryService);
    verify(repository, never()).deleteByUserIdAndActorId(anyInt(), any());
  }

  // ---- Accept and Reject ----------------------------------------------------------------

  @Test
  void accept_byTheFollowsId_acceptsIt() {
    final RemoteFollowing following = following(FollowingState.PENDING);

    final ObjectNode accept = answer("Accept", mapper.getNodeFactory().textNode(FOLLOW_ID));
    assertEquals(InboxService.Outcome.HANDLED, service.response(bob, accept, ALICE));

    assertEquals(FollowingState.ACCEPTED, following.getState());
    verify(repository).save(following);
  }

  @Test
  void accept_ofTheEmbeddedFollow_acceptsIt_withOrWithoutItsId() {
    final RemoteFollowing following = following(FollowingState.PENDING);

    assertEquals(
        InboxService.Outcome.HANDLED,
        service.response(bob, answer("Accept", embeddedFollow(FOLLOW_ID)), ALICE));
    assertEquals(FollowingState.ACCEPTED, following.getState());

    following.setState(FollowingState.PENDING);
    assertEquals(
        InboxService.Outcome.HANDLED,
        service.response(bob, answer("Accept", embeddedFollow(null)), ALICE));
    assertEquals(FollowingState.ACCEPTED, following.getState());

    // an id of the remote server's own, as some send
    following.setState(FollowingState.PENDING);
    assertEquals(
        InboxService.Outcome.HANDLED,
        service.response(bob, answer("Accept", embeddedFollow(ALICE + "#follow/9")), ALICE));
    assertEquals(FollowingState.ACCEPTED, following.getState());
  }

  @Test
  void reject_marksItRejected_andDropsTheAccountsPosts() {
    final RemoteFollowing following = following(FollowingState.ACCEPTED);

    assertEquals(
        InboxService.Outcome.HANDLED,
        service.response(bob, answer("Reject", embeddedFollow(FOLLOW_ID)), ALICE));

    assertEquals(FollowingState.REJECTED, following.getState());
    verify(repository).save(following);
    verify(postRepository).deleteByActorId(ALICE);
  }

  @Test
  void answer_signedBySomeoneElse_isIgnored() {
    final RemoteFollowing following = following(FollowingState.PENDING);
    final String mallory = "https://evil.example/users/mallory";

    final ObjectNode accept = answer("Accept", mapper.getNodeFactory().textNode(FOLLOW_ID));
    accept.put("actor", mallory);
    assertEquals(InboxService.Outcome.IGNORED, service.response(bob, accept, mallory));
    final ObjectNode reject = answer("Reject", embeddedFollow(FOLLOW_ID));
    reject.put("actor", mallory);
    assertEquals(InboxService.Outcome.IGNORED, service.response(bob, reject, mallory));

    assertEquals(FollowingState.PENDING, following.getState());
    verify(repository, never()).save(any());
  }

  @Test
  void answer_toAFollowNotSent_isIgnored() {
    assertEquals(
        InboxService.Outcome.IGNORED,
        service.response(
            bob, answer("Accept", mapper.getNodeFactory().textNode(BOB + "#follows/x")), ALICE));
    assertEquals(
        InboxService.Outcome.IGNORED, service.response(bob, answer("Accept", null), ALICE));
    // someone else's Follow, embedded
    final ObjectNode other = embeddedFollow(null);
    other.put("actor", "https://www.justjournal.com/users/carol");
    assertEquals(
        InboxService.Outcome.IGNORED, service.response(bob, answer("Accept", other), ALICE));
    verify(repository, never()).save(any());
  }

  // ---- counts and cleanup ---------------------------------------------------------------

  @Test
  void countAccepted_countsOnlyAcceptedFollows() {
    when(repository.countByUserIdAndState(7, FollowingState.ACCEPTED)).thenReturn(4L);
    assertEquals(4, service.countAccepted(bob));
  }

  @Test
  void deliveryInboxes_preferSharedInboxes_andLeaveOutDeclinedFollows() {
    final RemoteFollowing shared = following(FollowingState.ACCEPTED);
    final RemoteFollowing own = new RemoteFollowing();
    own.setActorId("https://gts.example/users/ann");
    own.setInbox("https://gts.example/users/ann/inbox");
    own.setState(FollowingState.PENDING);
    final RemoteFollowing declined = new RemoteFollowing();
    declined.setActorId("https://no.example/users/x");
    declined.setInbox("https://no.example/users/x/inbox");
    declined.setState(FollowingState.REJECTED);
    when(repository.findByUserIdOrderByIdAsc(7)).thenReturn(List.of(shared, own, declined));

    assertEquals(
        Set.of(
            URI.create("https://remote.social/inbox"),
            URI.create("https://gts.example/users/ann/inbox")),
        service.deliveryInboxes(bob));
  }

  @Test
  void removeAll_forgetsTheFollows_andPostsNobodyFollowsAnyMore() {
    when(repository.deleteByUserId(7)).thenReturn(2L);

    assertEquals(2, service.removeAll(bob));

    verify(postRepository).deleteUnfollowed(FollowingState.ACCEPTED);
    verifyNoInteractions(deliveryService);
  }

  @Test
  void remoteActorGone_forgetsItsFollowsAndPosts() {
    when(repository.deleteByActorId(ALICE)).thenReturn(3L);

    assertEquals(3, service.remoteActorGone(URI.create(ALICE)));

    verify(postRepository).deleteByActorId(ALICE);
    assertEquals(0, service.remoteActorGone(null));
  }
}
