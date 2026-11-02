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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.core.Settings;
import com.justjournal.model.FollowingState;
import com.justjournal.model.RemoteFollowing;
import com.justjournal.model.RemotePost;
import com.justjournal.model.User;
import com.justjournal.repository.RemoteFollowingRepository;
import com.justjournal.repository.RemotePostRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.services.AvatarService;
import com.justjournal.services.EntryService;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/** Posts by followed fediverse accounts: what is stored, how it is cleaned, and when it goes. */
class RemotePostServiceTests {

  private static final String ALICE = "https://remote.social/users/alice";
  private static final String NOTE = ALICE + "/statuses/1";
  private static final String PUBLIC = ActivityStreamsObjects.PUBLIC;

  private final ObjectMapper mapper = new ObjectMapper();
  private RemotePostRepository repository;
  private RemoteFollowingRepository followingRepository;
  private FederationPolicy federationPolicy;
  private RemotePostService service;

  @BeforeEach
  void setUp() {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    final ActivityStreamsObjects objects =
        new ActivityStreamsObjects(
            settings,
            mock(EntryService.class),
            mock(AvatarService.class),
            mock(UserPicRepository.class),
            mock(ActorKeyService.class));
    repository = mock(RemotePostRepository.class);
    followingRepository = mock(RemoteFollowingRepository.class);
    federationPolicy = mock(FederationPolicy.class);
    service = new RemotePostService(repository, followingRepository, federationPolicy, objects);

    when(repository.findByObjectId(anyString())).thenReturn(Optional.empty());
    when(followingRepository.existsByActorIdAndState(ALICE, FollowingState.ACCEPTED))
        .thenReturn(true);
  }

  // ---- fixtures -------------------------------------------------------------------------

  private ObjectNode note(final String content) {
    final ObjectNode note = mapper.createObjectNode();
    note.put("id", NOTE);
    note.put("type", "Note");
    note.put("attributedTo", ALICE);
    note.put("url", "https://remote.social/@alice/1");
    note.put("published", Instant.now().minus(Duration.ofHours(1)).toString());
    note.putArray("to").add(PUBLIC);
    note.putArray("cc").add(ALICE + "/followers");
    note.put("content", content);
    return note;
  }

  private ObjectNode activity(final String type, final ObjectNode object) {
    final ObjectNode activity = mapper.createObjectNode();
    activity.put("id", NOTE + "/" + type);
    activity.put("type", type);
    activity.put("actor", ALICE);
    activity.set("object", object);
    return activity;
  }

  private RemotePost saved() {
    final ArgumentCaptor<RemotePost> post = ArgumentCaptor.forClass(RemotePost.class);
    verify(repository).save(post.capture());
    return post.getValue();
  }

  private RemotePost stored(final String actor) {
    final RemotePost post = new RemotePost();
    post.setObjectId(NOTE);
    post.setActorId(actor);
    post.setContent("<p>old</p>");
    post.setPublished(new Date(0));
    when(repository.findByObjectId(NOTE)).thenReturn(Optional.of(post));
    return post;
  }

  // ---- Create ---------------------------------------------------------------------------

  @Test
  void create_ofAPublicNoteFromAFollowedAccount_isStored() {
    final ObjectNode note = note("<p>Hello <a href=\"https://example.com/x\">there</a></p>");
    note.put("summary", "<p>food</p>");
    note.putArray("attachment")
        .addObject()
        .put("type", "Document")
        .put("mediaType", "image/png")
        .put("url", "https://files.remote.social/a.png")
        .put("name", "a cat");

    assertEquals(
        InboxService.Outcome.HANDLED, service.receive(activity("Create", note), ALICE));

    final RemotePost post = saved();
    assertEquals(NOTE, post.getObjectId());
    assertEquals(ALICE, post.getActorId());
    assertEquals("https://remote.social/@alice/1", post.getUrl());
    assertEquals("food", post.getSummary());
    assertEquals(
        "<p>Hello <a href=\"https://example.com/x\" rel=\"nofollow ugc noopener\">there</a></p>",
        post.getContent());
    assertEquals(
        List.of(new RemotePostService.Link("https://files.remote.social/a.png", "a cat")),
        RemotePostService.links(post));
    assertNull(post.getUpdated());
  }

  @Test
  void create_addressedToPublicInCc_isStored() {
    final ObjectNode note = note("<p>unlisted</p>");
    note.putArray("to").add(ALICE + "/followers");
    note.putArray("cc").add("as:Public");

    assertEquals(
        InboxService.Outcome.HANDLED, service.receive(activity("Create", note), ALICE));
  }

  @Test
  void create_ofFollowersOnlyOrDirectPosts_isIgnored() {
    final ObjectNode followersOnly = note("<p>secret</p>");
    followersOnly.putArray("to").add(ALICE + "/followers");
    followersOnly.remove("cc");
    final ObjectNode direct = note("<p>hi bob</p>");
    direct.put("to", "https://www.justjournal.com/users/bob");
    direct.remove("cc");

    assertEquals(
        InboxService.Outcome.IGNORED, service.receive(activity("Create", followersOnly), ALICE));
    assertEquals(InboxService.Outcome.IGNORED, service.receive(activity("Create", direct), ALICE));
    verify(repository, never()).save(any());
  }

  @Test
  void create_fromAnAccountNobodyFollowsOrNotYetAccepted_isIgnored() {
    when(followingRepository.existsByActorIdAndState(ALICE, FollowingState.ACCEPTED))
        .thenReturn(false);

    assertEquals(
        InboxService.Outcome.IGNORED,
        service.receive(activity("Create", note("<p>x</p>")), ALICE));
    verify(repository, never()).save(any());
  }

  @Test
  void create_notByTheSender_orOfAnotherHostsObject_isIgnored() {
    final String mallory = "https://evil.example/users/mallory";
    when(followingRepository.existsByActorIdAndState(mallory, FollowingState.ACCEPTED))
        .thenReturn(true);

    // Alice's note, sent by Mallory
    assertEquals(
        InboxService.Outcome.IGNORED,
        service.receive(activity("Create", note("<p>x</p>")), mallory));
    // Mallory's note, but with an id on Alice's server
    final ObjectNode forged = note("<p>x</p>");
    forged.put("attributedTo", mallory);
    assertEquals(
        InboxService.Outcome.IGNORED, service.receive(activity("Create", forged), mallory));
    verify(repository, never()).save(any());
  }

  @Test
  void create_ofOtherObjects_orRepliesToOurEntries_isIgnored() {
    final ObjectNode question = note("<p>?</p>");
    question.put("type", "Question");
    final ObjectNode reply = note("<p>nice entry</p>");
    reply.put("inReplyTo", "https://www.justjournal.com/users/bob/entry/5");
    final ObjectNode byReference = activity("Create", null);
    byReference.put("object", NOTE);

    assertEquals(
        InboxService.Outcome.IGNORED, service.receive(activity("Create", question), ALICE));
    assertEquals(InboxService.Outcome.IGNORED, service.receive(activity("Create", reply), ALICE));
    assertEquals(InboxService.Outcome.IGNORED, service.receive(byReference, ALICE));
    assertEquals(
        InboxService.Outcome.IGNORED,
        service.receive(activity("Announce", note("<p>boost</p>")), ALICE));
    verify(repository, never()).save(any());
  }

  @Test
  void create_replyingToSomeoneElse_isStored() {
    final ObjectNode reply = note("<p>agreed</p>");
    reply.put("inReplyTo", "https://other.example/users/carol/statuses/3");

    assertEquals(InboxService.Outcome.HANDLED, service.receive(activity("Create", reply), ALICE));
  }

  @Test
  void create_ofAPostAlreadyStored_isIgnored() {
    stored(ALICE);

    assertEquals(
        InboxService.Outcome.IGNORED,
        service.receive(activity("Create", note("<p>again</p>")), ALICE));
    verify(repository, never()).save(any());
  }

  @Test
  void create_ofAPostOlderThanItIsKept_isIgnored_andOneFromTheFutureIsDatedNow() {
    final ObjectNode old = note("<p>old</p>");
    old.put("published", "2001-01-01T00:00:00Z");
    assertEquals(InboxService.Outcome.IGNORED, service.receive(activity("Create", old), ALICE));

    final ObjectNode future = note("<p>soon</p>");
    future.put("published", "2999-01-01T00:00:00Z");
    final Date before = new Date();
    service.receive(activity("Create", future), ALICE);
    assertFalse(saved().getPublished().before(before));
  }

  @Test
  void content_isCleanedToTextParagraphsBreaksAndLinks() {
    final String dirty =
        "<p onclick=\"x()\">Hi<br><img src=\"https://tracker.example/p.gif\">"
            + "<script>alert(1)</script><span class=\"h-card\">@bob</span>"
            + "<a href=\"javascript:alert(1)\">bad</a> <a href=\"/relative\">rel</a>"
            + "<a href=\"https://ok.example/\" rel=\"me\" target=\"_blank\">ok</a></p>"
            + "<iframe src=\"https://evil.example/\"></iframe><b>bold</b>";

    final String clean = RemotePostService.clean(dirty);

    assertEquals(
        "<p>Hi<br>@bob<a rel=\"nofollow ugc noopener\">bad</a> "
            + "<a rel=\"nofollow ugc noopener\">rel</a>"
            + "<a href=\"https://ok.example/\" rel=\"nofollow ugc noopener\">ok</a></p>bold",
        clean);
    assertFalse(clean.contains("img"));
    assertFalse(clean.contains("script"));
    assertFalse(clean.contains("onclick"));
  }

  @Test
  void attachments_areKeptAsHttpLinksOnly() {
    final ObjectNode note = note("<p>pics</p>");
    final var attachments = note.putArray("attachment");
    attachments.addObject().put("type", "Image").put("url", "javascript:alert(1)");
    attachments
        .addObject()
        .put("type", "Document")
        .putArray("url")
        .addObject()
        .put("type", "Link")
        .put("href", "https://files.remote.social/b.mp4");
    for (int i = 0; i < 20; i++) {
      attachments.addObject().put("url", "https://files.remote.social/" + i + ".png");
    }

    service.receive(activity("Create", note), ALICE);

    final List<RemotePostService.Link> links = RemotePostService.links(saved());
    assertEquals(RemotePostService.MAX_ATTACHMENTS, links.size());
    assertEquals("https://files.remote.social/b.mp4", links.get(0).url());
    assertTrue(links.stream().allMatch(l -> l.url().startsWith("https://")));
  }

  // ---- Update and Delete ----------------------------------------------------------------

  @Test
  void update_byTheAuthor_replacesTheStoredPost() {
    final RemotePost post = stored(ALICE);

    assertEquals(
        InboxService.Outcome.HANDLED,
        service.receive(activity("Update", note("<p>edited</p>")), ALICE));

    assertEquals("<p>edited</p>", post.getContent());
    assertEquals(new Date(0), post.getPublished());
    assertTrue(post.getUpdated() != null);
    verify(repository).save(post);
  }

  @Test
  void updateAndDelete_bySomeoneElse_areIgnored() {
    final RemotePost post = stored("https://remote.social/users/mallory");

    assertEquals(
        InboxService.Outcome.IGNORED,
        service.receive(activity("Update", note("<p>edited</p>")), ALICE));
    final ObjectNode delete = activity("Delete", null);
    delete.put("object", NOTE);
    assertEquals(InboxService.Outcome.IGNORED, service.receive(delete, ALICE));

    assertEquals("<p>old</p>", post.getContent());
    verify(repository, never()).save(any());
    verify(repository, never()).delete(any());
  }

  @Test
  void update_thatMakesAPostNonPublic_removesIt() {
    final RemotePost post = stored(ALICE);
    final ObjectNode hidden = note("<p>now private</p>");
    hidden.putArray("to").add(ALICE + "/followers");
    hidden.remove("cc");

    assertEquals(InboxService.Outcome.HANDLED, service.receive(activity("Update", hidden), ALICE));
    verify(repository).delete(post);
  }

  @Test
  void delete_byTheAuthor_removesThePost_asATombstoneOrById() {
    final RemotePost post = stored(ALICE);
    final ObjectNode tombstone = mapper.createObjectNode();
    tombstone.put("id", NOTE);
    tombstone.put("type", "Tombstone");

    assertEquals(
        InboxService.Outcome.HANDLED, service.receive(activity("Delete", tombstone), ALICE));
    verify(repository).delete(post);

    final ObjectNode byId = activity("Delete", null);
    byId.put("object", NOTE);
    assertEquals(InboxService.Outcome.HANDLED, service.receive(byId, ALICE));
  }

  @Test
  void delete_ofSomethingNotStored_orOfTheActor_isIgnored() {
    final ObjectNode unknown = activity("Delete", null);
    unknown.put("object", ALICE + "/statuses/99");
    final ObjectNode actor = activity("Delete", null);
    actor.put("object", ALICE);

    assertEquals(InboxService.Outcome.IGNORED, service.receive(unknown, ALICE));
    assertEquals(InboxService.Outcome.IGNORED, service.receive(actor, ALICE));
    verify(repository, never()).delete(any());
  }

  // ---- friends page and purge -----------------------------------------------------------

  @Test
  void forFriendsPage_givesPostsWithTheirAccount_onlyWhileTheJournalFederates() {
    final User bob = new User();
    bob.setId(7);
    final RemotePost post = stored(ALICE);
    post.setAttachments("[{\"url\":\"https://files.remote.social/a.png\",\"name\":null}]");
    final RemoteFollowing alice = new RemoteFollowing();
    alice.setActorId(ALICE);
    when(repository.findFollowedBy(eq(7), eq(FollowingState.ACCEPTED), any(Pageable.class)))
        .thenReturn(List.of(post));
    when(followingRepository.findByUserIdOrderByIdAsc(7)).thenReturn(List.of(alice));

    when(federationPolicy.federates(bob)).thenReturn(false);
    assertEquals(List.of(), service.forFriendsPage(bob, 20));
    verify(repository, never()).findFollowedBy(any(Integer.class), any(), any());

    when(federationPolicy.federates(bob)).thenReturn(true);
    final List<RemotePostService.FriendsPagePost> posts = service.forFriendsPage(bob, 20);
    assertEquals(1, posts.size());
    assertEquals(post, posts.get(0).post());
    assertEquals(alice, posts.get(0).account());
    assertEquals("https://files.remote.social/a.png", posts.get(0).attachments().get(0).url());
  }

  @Test
  void purge_removesOldPostsAndPostsNobodyFollows() {
    service.setKeepDays(30);
    when(repository.deletePublishedBefore(any())).thenReturn(2);
    when(repository.deleteUnfollowed(FollowingState.ACCEPTED)).thenReturn(3);
    final Date now = new Date();

    assertEquals(5, service.purge(now));

    final ArgumentCaptor<Date> cutoff = ArgumentCaptor.forClass(Date.class);
    verify(repository).deletePublishedBefore(cutoff.capture());
    assertEquals(now.getTime() - Duration.ofDays(30).toMillis(), cutoff.getValue().getTime());
    verifyNoInteractions(followingRepository);
  }
}
