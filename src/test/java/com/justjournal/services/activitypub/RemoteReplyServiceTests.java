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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.model.Entry;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.RemoteReply;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.RemoteReplyRepository;
import com.justjournal.services.CommentMailService;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.activitypub.InboxService.Outcome;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class RemoteReplyServiceTests {

  private static final String BOB = "https://www.justjournal.com/users/bob";
  private static final String ENTRY = BOB + "/entry/5";
  private static final String ALICE = "https://remote.social/users/alice";
  private static final String MALLORY = "https://evil.example/users/mallory";
  private static final String NOTE = ALICE + "/statuses/1";
  private static final String PUBLIC = "https://www.w3.org/ns/activitystreams#Public";

  private final ObjectMapper mapper = new ObjectMapper();
  private RemoteReplyRepository repository;
  private EntryRepository entryRepository;
  private FederationPolicy federationPolicy;
  private ActivityPubFetcher fetcher;
  private CommentMailService commentMailService;
  private RemoteReplyService service;
  private User bob;
  private Journal journal;
  private Entry entry;
  private ObjectNode aliceActor;

  @BeforeEach
  void setUp() throws Exception {
    repository = mock(RemoteReplyRepository.class);
    entryRepository = mock(EntryRepository.class);
    federationPolicy = mock(FederationPolicy.class);
    fetcher = mock(ActivityPubFetcher.class);
    commentMailService = mock(CommentMailService.class);
    final ActorKeyService actorKeyService = mock(ActorKeyService.class);
    when(actorKeyService.userActorId("bob")).thenReturn(BOB);
    service =
        new RemoteReplyService(
            repository,
            entryRepository,
            federationPolicy,
            new EntryAccessService(),
            actorKeyService,
            fetcher,
            commentMailService);

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    journal = new Journal();
    journal.setUser(bob);
    bob.getJournals().add(journal);

    entry = new Entry();
    entry.setId(5);
    entry.setUser(bob);
    entry.setSecurity(Security.PUBLIC);
    entry.setAllowComments(PrefBool.Y);
    when(entryRepository.findById(5)).thenReturn(Optional.of(entry));
    when(repository.findByObjectId(anyString())).thenReturn(Optional.empty());

    when(federationPolicy.isEnabled()).thenReturn(true);
    when(federationPolicy.federates(bob)).thenReturn(true);

    when(fetcher.normalize(anyString()))
        .thenAnswer(
            inv -> {
              final URI uri = URI.create(inv.getArgument(0));
              return new NormalizedUrl(uri, uri, uri.getHost());
            });
    aliceActor = mapper.createObjectNode();
    aliceActor.put("id", ALICE);
    aliceActor.put("type", "Person");
    aliceActor.put("preferredUsername", "alice");
    aliceActor.put("name", "Alice\nLiddell");
    aliceActor.put("url", "https://remote.social/@alice");
    aliceActor.putObject("icon").put("url", "https://remote.social/avatar.png");
    when(fetcher.signedGet(any(), anyInt())).thenAnswer(inv -> actorDocument(aliceActor));
  }

  private static SafeHttpFetcher.Result actorDocument(final ObjectNode actor) {
    return new SafeHttpFetcher.Result(
        URI.create(actor.get("id").asText()),
        "application/activity+json",
        StandardCharsets.UTF_8,
        actor.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** A public reply by Alice to Bob's entry 5, as Mastodon sends it. */
  private ObjectNode create(final String content) {
    final ObjectNode create = mapper.createObjectNode();
    create.put("@context", "https://www.w3.org/ns/activitystreams");
    create.put("id", NOTE + "/activity");
    create.put("type", "Create");
    create.put("actor", ALICE);
    final ObjectNode note = create.putObject("object");
    note.put("id", NOTE);
    note.put("type", "Note");
    note.put("attributedTo", ALICE);
    note.put("inReplyTo", ENTRY);
    note.put("content", content);
    note.put("published", "2024-03-11T08:30:00Z");
    note.putArray("to").add(PUBLIC);
    note.putArray("cc").add(ALICE + "/followers").add(BOB);
    return create;
  }

  private ObjectNode create() {
    return create("<p><span class=\"h-card\"><a href=\"" + BOB + "\">@bob</a></span> Nice post</p>");
  }

  private ObjectNode note(final ObjectNode activity) {
    return (ObjectNode) activity.get("object");
  }

  private RemoteReply stored() {
    final RemoteReply reply = new RemoteReply();
    reply.setId(3);
    reply.setEntryId(5);
    reply.setObjectId(NOTE);
    reply.setActorId(ALICE);
    reply.setAuthorName("Alice");
    reply.setAuthorUrl(ALICE);
    reply.setContent("<p>old</p>");
    when(repository.findByObjectId(NOTE)).thenReturn(Optional.of(reply));
    return reply;
  }

  private void assertIgnored(final ObjectNode activity, final String sender) {
    assertEquals(Outcome.IGNORED, service.create(bob, activity, sender));
    verify(repository, never()).saveAndFlush(any());
    verify(commentMailService, never()).commentAdded(any(), any(), any(), any());
  }

  // ---- Create -------------------------------------------------------------------------------

  @Test
  void aReplyToAnEntryIsStored() {
    assertEquals(Outcome.HANDLED, service.create(bob, create(), ALICE));

    final ArgumentCaptor<RemoteReply> captor = ArgumentCaptor.forClass(RemoteReply.class);
    verify(repository).saveAndFlush(captor.capture());
    final RemoteReply reply = captor.getValue();
    assertEquals(5, reply.getEntryId());
    assertEquals(NOTE, reply.getObjectId());
    assertEquals(ALICE, reply.getActorId());
    assertEquals("Alice Liddell", reply.getAuthorName());
    assertEquals("@alice@remote.social", reply.getAuthorHandle());
    assertEquals("https://remote.social/@alice", reply.getAuthorUrl());
    assertEquals(
        "<p><a href=\"" + BOB + "\" rel=\"nofollow ugc noopener\">@bob</a> Nice post</p>",
        reply.getContent());
    assertEquals(Date.from(java.time.Instant.parse("2024-03-11T08:30:00Z")), reply.getPublished());
    assertNotNull(reply.getCreated());
    assertNull(reply.getUpdated());
    // the writer is told, as for any comment
    verify(commentMailService)
        .commentAdded(
            entry, "Alice Liddell (@alice@remote.social)", "(no subject)", "@bob Nice post");
  }

  @Test
  void anArticleReplyAndAnUnlistedOneAreStoredToo() {
    final ObjectNode create = create();
    note(create).put("type", "Article");
    note(create).putArray("to").add(ALICE + "/followers");
    note(create).putArray("cc").add(PUBLIC);

    assertEquals(Outcome.HANDLED, service.create(bob, create, ALICE));
    verify(repository).saveAndFlush(any());
  }

  @Test
  void aFuturePublishedTimeIsTakenAsNow() {
    final ObjectNode create = create();
    note(create).put("published", "2999-01-01T00:00:00Z");

    service.create(bob, create, ALICE);

    final ArgumentCaptor<RemoteReply> captor = ArgumentCaptor.forClass(RemoteReply.class);
    verify(repository).saveAndFlush(captor.capture());
    assertFalse(captor.getValue().getPublished().after(new Date()));
  }

  @Test
  void refused_whenFederationIsOffForTheSite() {
    when(federationPolicy.isEnabled()).thenReturn(false);
    assertIgnored(create(), ALICE);
    verifyNoInteractions(fetcher);
  }

  @Test
  void refused_whenTheEntrysOwnerDoesNotFederate() {
    when(federationPolicy.federates(bob)).thenReturn(false);
    assertIgnored(create(), ALICE);
  }

  @Test
  void refused_whenTheEntryIsNotPublic() {
    entry.setSecurity(Security.FRIENDS);
    assertIgnored(create(), ALICE);
  }

  @Test
  void refused_whenTheEntryIsADraft() {
    entry.setDraft(PrefBool.Y);
    assertIgnored(create(), ALICE);
  }

  @Test
  void refused_whenTheJournalIsOwnerViewOnly() {
    journal.setOwnerViewOnly(true);
    assertIgnored(create(), ALICE);
  }

  @Test
  void refused_whenTheEntryTakesNoComments() {
    entry.setAllowComments(PrefBool.N);
    assertIgnored(create(), ALICE);
  }

  @Test
  void refused_whenTheSenderDidNotWriteIt() {
    final ObjectNode create = create();
    note(create).put("attributedTo", MALLORY);
    assertIgnored(create, ALICE);
  }

  @Test
  void refused_whenSomeoneElseSendsAlicesReply() {
    final ObjectNode create = create();
    create.put("actor", MALLORY);
    assertIgnored(create, MALLORY);
  }

  @Test
  void refused_whenTheReplyIdIsOnAnotherServer() {
    final ObjectNode create = create();
    note(create).put("id", "https://other.example/statuses/1");
    assertIgnored(create, ALICE);
  }

  @Test
  void refused_whenTheReplyIsNotPublic() {
    final ObjectNode create = create();
    note(create).putArray("to").add(BOB);
    note(create).putArray("cc");
    assertIgnored(create, ALICE);
  }

  @Test
  void refused_whenItRepliesToSomethingElse() {
    final ObjectNode toAReply = create();
    note(toAReply).put("inReplyTo", "https://remote.social/users/carol/statuses/9");
    assertIgnored(toAReply, ALICE);

    final ObjectNode toNothing = create();
    note(toNothing).remove("inReplyTo");
    assertIgnored(toNothing, ALICE);

    final ObjectNode notAnEntry = create();
    note(notAnEntry).put("inReplyTo", ENTRY + "/../6");
    assertIgnored(notAnEntry, ALICE);
  }

  @Test
  void refused_whenTheEntryIsMissingOrAnotherJournals() {
    final ObjectNode missing = create();
    note(missing).put("inReplyTo", BOB + "/entry/99");
    when(entryRepository.findById(99)).thenReturn(Optional.empty());
    assertIgnored(missing, ALICE);

    // the entry number of another journal's entry, under Bob's address
    final User carol = new User();
    carol.setId(9);
    carol.setUsername("carol");
    final Entry carols = new Entry();
    carols.setId(6);
    carols.setUser(carol);
    carols.setSecurity(Security.PUBLIC);
    when(entryRepository.findById(6)).thenReturn(Optional.of(carols));
    final ObjectNode other = create();
    note(other).put("inReplyTo", BOB + "/entry/6");
    assertIgnored(other, ALICE);
  }

  @Test
  void refused_whenItIsNotANoteOrArticle() {
    final ObjectNode question = create();
    note(question).put("type", "Question");
    assertIgnored(question, ALICE);

    final ObjectNode byId = create();
    byId.put("object", NOTE);
    assertIgnored(byId, ALICE);
  }

  @Test
  void refused_whenNoTextIsLeft() {
    assertIgnored(create("<img src=\"https://remote.social/x.png\"><script>x()</script>"), ALICE);
  }

  @Test
  void refused_whenTheAuthorsActorCannotBeUsed() throws Exception {
    when(fetcher.signedGet(any(), anyInt()))
        .thenThrow(new FetchException(410, true, "HTTP status 410"));
    assertIgnored(create(), ALICE);
  }

  @Test
  void retried_whenTheAuthorsActorCannotBeFetchedForNow() throws Exception {
    when(fetcher.signedGet(any(), anyInt())).thenThrow(new IOException("timed out"));

    assertEquals(Outcome.RETRY, service.create(bob, create(), ALICE));
    verify(repository, never()).saveAndFlush(any());
  }

  // ---- delivered again ----------------------------------------------------------------------

  @Test
  void aReplyDeliveredAgainIsStoredOnce() throws Exception {
    stored();

    assertEquals(Outcome.HANDLED, service.create(bob, create(), ALICE));

    verify(repository, never()).saveAndFlush(any());
    verify(fetcher, never()).signedGet(any(), anyInt());
    verify(commentMailService, never()).commentAdded(any(), any(), any(), any());
  }

  @Test
  void aReplyDeliveredTwiceAtOnceIsStoredOnce() {
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertEquals(Outcome.HANDLED, service.create(bob, create(), ALICE));
    verify(commentMailService, never()).commentAdded(any(), any(), any(), any());
  }

  // ---- the author -----------------------------------------------------------------------------

  @Test
  void anAuthorWithoutANameIsShownByUsername() {
    aliceActor.remove("name");
    aliceActor.remove("url");

    service.create(bob, create(), ALICE);

    final ArgumentCaptor<RemoteReply> captor = ArgumentCaptor.forClass(RemoteReply.class);
    verify(repository).saveAndFlush(captor.capture());
    assertEquals("alice", captor.getValue().getAuthorName());
    assertEquals(ALICE, captor.getValue().getAuthorUrl());
  }

  @Test
  void anOddUsernameOrProfileIsNotShown() {
    aliceActor.put("preferredUsername", "<b>alice</b>");
    aliceActor.put("url", "javascript:alert(1)");

    service.create(bob, create(), ALICE);

    final ArgumentCaptor<RemoteReply> captor = ArgumentCaptor.forClass(RemoteReply.class);
    verify(repository).saveAndFlush(captor.capture());
    assertNull(captor.getValue().getAuthorHandle());
    assertEquals(ALICE, captor.getValue().getAuthorUrl());
  }

  // ---- Update -------------------------------------------------------------------------------

  private ObjectNode update(final String sender, final String content) {
    final ObjectNode update = create(content);
    update.put("type", "Update");
    update.put("id", NOTE + "#updates/1");
    update.put("actor", sender);
    return update;
  }

  @Test
  void theAuthorCanChangeTheirReply() {
    final RemoteReply reply = stored();

    assertEquals(
        Outcome.HANDLED,
        service.update(update(ALICE, "<p>Edited <script>alert(1)</script></p>"), ALICE));

    assertEquals("<p>Edited </p>", reply.getContent());
    assertNotNull(reply.getUpdated());
    verify(repository).save(reply);
  }

  @Test
  void nobodyElseCanChangeIt() {
    final RemoteReply reply = stored();
    final ObjectNode update = update(MALLORY, "<p>Spam</p>");
    note(update).put("attributedTo", MALLORY);

    assertEquals(Outcome.IGNORED, service.update(update, MALLORY));

    assertEquals("<p>old</p>", reply.getContent());
    verify(repository, never()).save(any());
  }

  @Test
  void anUpdateOfSomethingNotStoredIsIgnored() {
    assertEquals(Outcome.IGNORED, service.update(update(ALICE, "<p>Hi</p>"), ALICE));
    verify(repository, never()).save(any());
  }

  // ---- Delete -------------------------------------------------------------------------------

  @Test
  void theAuthorCanDeleteTheirReply() {
    final RemoteReply reply = stored();

    assertEquals(Outcome.HANDLED, service.delete(NOTE, ALICE));
    verify(repository).delete(reply);
  }

  @Test
  void nobodyElseCanDeleteIt() {
    stored();

    assertEquals(Outcome.IGNORED, service.delete(NOTE, MALLORY));
    verify(repository, never()).delete(any());
  }

  @Test
  void aDeletedActorsRepliesGo() {
    when(repository.deleteByActorId(ALICE)).thenReturn(2L);
    when(repository.existsByActorId(ALICE)).thenReturn(true);

    assertTrue(service.hasReplies(ALICE));
    assertEquals(2, service.removeActor(ALICE));
    assertFalse(service.hasReplies(MALLORY));
    assertEquals(0, service.removeActor("not a url"));
    verify(repository, never()).deleteByActorId(eq("not a url"));
  }

  // ---- cleaning -----------------------------------------------------------------------------

  @Test
  void cleaningKeepsTextParagraphsBreaksAndLinks() {
    assertEquals(
        "<p>one<br>two "
            + "<a href=\"https://example.com/a\" rel=\"nofollow ugc noopener\">link</a></p>",
        RemoteReplyService.clean(
            "<p class=\"x\" style=\"color:red\">one<br/>two "
                + "<a href=\"https://example.com/a\" rel=\"me\" target=\"_blank\""
                + " onclick=\"x()\">link</a></p>"));
  }

  @Test
  void cleaningRemovesScriptsPicturesAndOtherTags() {
    final String cleaned =
        RemoteReplyService.clean(
            "<p>hi<script>alert(1)</script><img src=\"https://example.com/x.png\" onerror=\"x()\">"
                + "<iframe src=\"https://example.com\"></iframe><b>bold</b>"
                + "<style>p{}</style></p>");
    assertEquals("<p>hibold</p>", cleaned);
  }

  @Test
  void cleaningRemovesLinksThatAreNotHttp() {
    assertEquals(
        "<p><a rel=\"nofollow ugc noopener\">click</a> <a rel=\"nofollow ugc noopener\">x</a>"
            + " <a rel=\"nofollow ugc noopener\">y</a></p>",
        RemoteReplyService.clean(
            "<p><a href=\"javascript:alert(1)\">click</a> <a href=\"data:text/html,x\">x</a>"
                + " <a href=\"/relative\">y</a></p>"));
  }

  @Test
  void cleaningCutsALongReplyToPlainText() {
    final String html = "<p>" + "a&amp;".repeat(RemoteReplyService.MAX_CONTENT_LENGTH) + "</p>";
    final String cleaned = RemoteReplyService.clean(html);
    assertTrue(cleaned.startsWith("<p>a&amp;a&amp;"), cleaned);
    assertTrue(cleaned.endsWith("…</p>"), cleaned);
    assertEquals(
        RemoteReplyService.MAX_CONTENT_LENGTH,
        org.jsoup.Jsoup.parse(cleaned).text().length() - 1);
  }

  @Test
  void cleaningLeavesNothingWhenThereIsNoText() {
    assertNull(RemoteReplyService.clean(null));
    assertNull(RemoteReplyService.clean("<p> </p><img src=\"https://example.com/x.png\">"));
  }
}
