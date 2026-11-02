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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.atom.AtomFeed;
import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.core.Settings;
import com.justjournal.core.UserContextService;
import com.justjournal.model.ActorKey;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.FormatType;
import com.justjournal.model.Friend;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.Tag;
import com.justjournal.model.User;
import com.justjournal.model.UserBio;
import com.justjournal.model.UserPic;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.FavoriteRepository;
import com.justjournal.repository.MoodThemeDataRepository;
import com.justjournal.repository.RemoteReplyRepository;
import com.justjournal.repository.RssSubscriptionsRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.rss.CachedHeadlineBean;
import com.justjournal.rss.Rss;
import com.justjournal.services.AvatarService;
import com.justjournal.services.BlogSearchService;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.EntryImageService;
import com.justjournal.services.EntryService;
import com.justjournal.services.MarkdownService;
import com.justjournal.services.PdfFormatService;
import com.justjournal.services.TrackbackService;
import com.justjournal.services.UserImageService;
import com.justjournal.services.activitypub.ActivityStreamsObjects;
import com.justjournal.services.activitypub.ActorKeyService;
import com.justjournal.services.activitypub.FollowerService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

/**
 * The read only ActivityPub actor, outbox and entries, served next to the HTML journal pages at the
 * same addresses. Both controllers are registered so the tests show which one Spring picks.
 */
class ActivityStreamControllerTests {

  private static final String BASE = "https://www.justjournal.com/";
  private static final String ACTOR = BASE + "users/bob";
  private static final String AS_PROFILE =
      "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";
  /** What Mastodon sends when someone pastes a URL into its search. */
  private static final String MASTODON_SEARCH =
      "application/activity+json, " + AS_PROFILE + ", text/html;q=0.1";
  private static final String BOB_PEM =
      "-----BEGIN PUBLIC KEY-----\nMIIBIjANBg...\n-----END PUBLIC KEY-----\n";
  private static final String BROWSER =
      "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";

  private final ObjectMapper mapper = new ObjectMapper();
  private UserRepository userRepository;
  private EntryRepository entryRepository;
  private EntryService entryService;
  private EntryAttachmentService entryAttachmentService;
  private AvatarService avatarService;
  private UserPicRepository userPicRepository;
  private FollowerService followerService;
  private ActorKeyService actorKeyService;
  private MockMvc mockMvc;
  private User bob;
  private Journal journal;
  private final List<Entry> published = new ArrayList<>();

  @BeforeEach
  void setUp() {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    userRepository = mock(UserRepository.class);
    entryRepository = mock(EntryRepository.class);
    entryService = mock(EntryService.class);
    entryAttachmentService = mock(EntryAttachmentService.class);
    avatarService = mock(AvatarService.class);
    userPicRepository = mock(UserPicRepository.class);
    followerService = mock(FollowerService.class);
    actorKeyService = mock(ActorKeyService.class);

    when(entryService.convertBodyToHtml(any(), anyString()))
        .thenAnswer(inv -> "<p>" + inv.getArgument(1) + "</p>");
    when(entryAttachmentService.forFeed(anyCollection())).thenReturn(Map.of());
    when(entryRepository.findById(any())).thenReturn(Optional.empty());
    when(entryRepository.findByUserAndSecurityAndDraft(
            any(User.class), eq(Security.PUBLIC), eq(PrefBool.N), any(Pageable.class)))
        .thenAnswer(
            inv -> {
              final Pageable pageable = inv.getArgument(3);
              final int from = (int) Math.min(pageable.getOffset(), published.size());
              final int to = Math.min(from + pageable.getPageSize(), published.size());
              return new PageImpl<>(published.subList(from, to), pageable, published.size());
            });

    final ActivityStreamController controller =
        new ActivityStreamController(
            userRepository,
            entryRepository,
            new EntryAccessService(),
            entryAttachmentService,
            followerService,
            new ActivityStreamsObjects(
                settings, entryService, avatarService, userPicRepository, actorKeyService));

    final UserContextService userContextService = mock(UserContextService.class);
    final UsersController usersController =
        new UsersController(
            entryService,
            mock(CommentRepository.class),
            entryRepository,
            mock(FavoriteRepository.class),
            mock(MoodThemeDataRepository.class),
            userRepository,
            mock(RssSubscriptionsRepository.class),
            mock(UserImageService.class),
            avatarService,
            mock(BlogSearchService.class),
            mock(Rss.class),
            mock(MarkdownService.class),
            mock(AtomFeed.class),
            mock(TrackbackService.class),
            mock(CachedHeadlineBean.class),
            userContextService,
            mock(PdfFormatService.class),
            settings,
            entryAttachmentService,
            new EntryAttachmentRenderer(new EntryAccessService(), mock(EntryImageService.class)),
            mock(RemoteReplyRepository.class));

    final View noView = (m, request, response) -> {};
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller, usersController)
            .setControllerAdvice(new ExceptionHandlerConfig())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setViewResolvers((name, locale) -> noView)
            .build();

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    bob.setName("Bob");
    journal = new Journal();
    journal.setName("Bob's Blog");
    journal.setSince(date(2004, 5, 6));
    journal.setUser(bob);
    bob.getJournals().add(journal);
    when(userRepository.findByUsername("bob")).thenReturn(bob);
    final ActorKey key = new ActorKey();
    key.setUserId(7);
    key.setPublicKeyPem(BOB_PEM);
    key.setPrivateKeyPem("never shown");
    when(actorKeyService.forUser(bob)).thenReturn(key);
  }

  // ---- fixtures -------------------------------------------------------------------------

  private static Date date(final int year, final int month, final int day) {
    return Date.from(LocalDateTime.of(year, month, day, 12, 0).toInstant(ZoneOffset.UTC));
  }

  private Entry entry(final int id, final String subject) {
    final Entry entry = new Entry();
    entry.setId(id);
    entry.setUser(bob);
    entry.setSecurity(Security.PUBLIC);
    entry.setDraft(PrefBool.N);
    entry.setDate(date(2024, 3, 10));
    entry.setModified(date(2024, 3, 10));
    entry.setSubject(subject);
    entry.setBody("Body of " + id);
    entry.setFormat(FormatType.HTML);
    when(entryRepository.findById(id)).thenReturn(Optional.of(entry));
    return entry;
  }

  private static void tag(final Entry entry, final String... names) {
    final Set<EntryTag> tags = new LinkedHashSet<>();
    for (final String name : names) {
      final EntryTag entryTag = new EntryTag();
      entryTag.setTag(new Tag(name));
      tags.add(entryTag);
    }
    entry.setTags(tags);
  }

  private static FeedAttachments pictureAndLink(final String linkUrl, final String linkTitle) {
    return new FeedAttachments(
        "http://www.justjournal.com/EntryImage/3",
        "http://www.justjournal.com/EntryImage/3/display",
        "image/png",
        2048,
        640,
        480,
        "A cat",
        linkUrl,
        linkTitle,
        "About it",
        "http://www.justjournal.com/EntryLinkImage/9",
        1200,
        630);
  }

  private void attach(final Entry entry, final FeedAttachments attachments) {
    when(entryAttachmentService.forFeed(List.of(entry)))
        .thenReturn(Map.of(entry.getId(), attachments));
  }

  private ResultActions as(final String path, final String accept) throws Exception {
    return mockMvc.perform(get(path).header(HttpHeaders.ACCEPT, accept));
  }

  private JsonNode json(final String path) throws Exception {
    final String body =
        as(path, ActivityStreamController.ACTIVITY_JSON_VALUE)
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/activity+json"))
            .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  // ---- content negotiation --------------------------------------------------------------

  @Test
  void actorAddress_servesActivityStreamsToFediverseClients() throws Exception {
    for (final String accept :
        List.of(
            ActivityStreamController.ACTIVITY_JSON_VALUE,
            AS_PROFILE,
            "application/ld+json",
            MASTODON_SEARCH)) {
      as("/users/bob", accept)
          .andExpect(status().isOk())
          .andExpect(handler().handlerType(ActivityStreamController.class))
          .andExpect(handler().methodName("actor"))
          .andExpect(content().contentTypeCompatibleWith("application/activity+json"))
          .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
    }
  }

  @Test
  void actorAddress_servesTheJournalPageToEveryoneElse() throws Exception {
    for (final String accept : List.of(MediaType.TEXT_HTML_VALUE, BROWSER, MediaType.ALL_VALUE)) {
      as("/users/bob", accept)
          .andExpect(handler().handlerType(UsersController.class))
          .andExpect(handler().methodName("entries"))
          .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
    }
    mockMvc
        .perform(get("/users/bob"))
        .andExpect(handler().handlerType(UsersController.class))
        .andExpect(handler().methodName("entries"));
  }

  @Test
  void entryAddress_negotiatesTheSameWay() throws Exception {
    entry(5, "Hello");
    for (final String accept :
        List.of(ActivityStreamController.ACTIVITY_JSON_VALUE, AS_PROFILE, MASTODON_SEARCH)) {
      as("/users/bob/entry/5", accept)
          .andExpect(status().isOk())
          .andExpect(handler().handlerType(ActivityStreamController.class))
          .andExpect(handler().methodName("entry"))
          .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
    }
    for (final String accept : List.of(MediaType.TEXT_HTML_VALUE, BROWSER, MediaType.ALL_VALUE)) {
      as("/users/bob/entry/5", accept)
          .andExpect(handler().handlerType(UsersController.class))
          .andExpect(handler().methodName("entry"))
          .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
    }
  }

  @Test
  void oldActorAddress_redirectsForGood() throws Exception {
    mockMvc
        .perform(get("/@/bob"))
        .andExpect(status().isMovedPermanently())
        .andExpect(header().string(HttpHeaders.LOCATION, ACTOR));
    mockMvc.perform(get("/@/not a user")).andExpect(status().isNotFound());
  }

  // ---- actor ----------------------------------------------------------------------------

  @Test
  void actor_describesThePublicJournal() throws Exception {
    final UserBio bio = new UserBio();
    bio.setBio("I <3 \"tea\"\nand cake");
    bob.setBio(bio);
    when(avatarService.isAvatarAvailable(bob)).thenReturn(true);
    final UserPic pic = new UserPic();
    pic.setMimeType("image/png");
    when(userPicRepository.findById(7)).thenReturn(Optional.of(pic));

    final JsonNode actor = json("/users/bob");

    // the security context defines publicKey
    assertEquals(2, actor.get("@context").size());
    assertEquals("https://www.w3.org/ns/activitystreams", actor.get("@context").get(0).asText());
    assertEquals("https://w3id.org/security/v1", actor.get("@context").get(1).asText());
    assertEquals(ACTOR, actor.get("id").asText());
    assertEquals("Person", actor.get("type").asText());
    assertEquals("bob", actor.get("preferredUsername").asText());
    assertEquals("Bob's Blog", actor.get("name").asText());
    assertEquals("<p>I &lt;3 &quot;tea&quot;<br>and cake</p>", actor.get("summary").asText());
    assertEquals(ACTOR, actor.get("url").asText());
    assertEquals("Image", actor.get("icon").get("type").asText());
    assertEquals("image/png", actor.get("icon").get("mediaType").asText());
    assertEquals(BASE + "Avatar/7", actor.get("icon").get("url").asText());
    assertEquals(ACTOR + "/inbox", actor.get("inbox").asText());
    assertEquals(ACTOR + "/outbox", actor.get("outbox").asText());
    assertEquals(ACTOR + "/followers", actor.get("followers").asText());
    assertEquals(ACTOR + "/following", actor.get("following").asText());
    assertEquals("2004-05-06T12:00:00.000Z", actor.get("published").asText());
    assertFalse(actor.get("manuallyApprovesFollowers").asBoolean());
    assertEquals(ACTOR + "#main-key", actor.get("publicKey").get("id").asText());
    assertEquals(ACTOR, actor.get("publicKey").get("owner").asText());
    assertEquals(BOB_PEM, actor.get("publicKey").get("publicKeyPem").asText());
    assertFalse(actor.toString().contains("never shown"));
    assertFalse(actor.has("discoverable"));
  }

  @Test
  void actor_leavesOutWhatTheUserDoesNotHave() throws Exception {
    final JsonNode actor = json("/users/bob");

    assertFalse(actor.has("summary"));
    assertFalse(actor.has("icon"));
  }

  @Test
  void actor_isNotFoundForUnknownOrPrivateJournals() throws Exception {
    as("/users/nobody", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());

    journal.setOwnerViewOnly(true);
    as("/users/bob", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound())
        .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
    as("/users/bob/outbox", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());

    bob.getJournals().clear();
    as("/users/bob", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());
    // no key is made for a journal nobody may see
    verify(actorKeyService, never()).forUser(any());
  }

  @Test
  void followersAndFollowing_areEmpty() throws Exception {
    for (final String name : List.of("followers", "following")) {
      final JsonNode collection = json("/users/bob/" + name);
      assertEquals(ACTOR + "/" + name, collection.get("id").asText());
      assertEquals("OrderedCollection", collection.get("type").asText());
      assertEquals(0, collection.get("totalItems").asInt());
    }
  }

  @Test
  void followers_countsThemButDoesNotListThem() throws Exception {
    when(followerService.count(bob)).thenReturn(3L);

    final JsonNode collection = json("/users/bob/followers");

    assertEquals(ACTOR + "/followers", collection.get("id").asText());
    assertEquals("OrderedCollection", collection.get("type").asText());
    assertEquals(3, collection.get("totalItems").asInt());
    assertFalse(collection.has("orderedItems"));
    assertFalse(collection.has("items"));
    assertFalse(collection.has("first"));
  }

  @Test
  void followers_ofAnOwnerOnlyJournal_areNotFound() throws Exception {
    journal.setOwnerViewOnly(true);

    as("/users/bob/followers", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());
    verify(followerService, never()).count(any());
  }

  // ---- outbox ---------------------------------------------------------------------------

  @Test
  void outbox_countsThePublicEntriesAndPointsAtTheFirstPage() throws Exception {
    for (int i = 16; i > 0; i--) published.add(entry(i, "Entry " + i));

    final JsonNode outbox = json("/users/bob/outbox");

    assertEquals(ACTOR + "/outbox", outbox.get("id").asText());
    assertEquals("OrderedCollection", outbox.get("type").asText());
    assertEquals(16, outbox.get("totalItems").asInt());
    assertEquals(ACTOR + "/outbox?page=1", outbox.get("first").asText());
    assertFalse(outbox.has("orderedItems"));
  }

  @Test
  void outbox_pagesNewestFirstInFifteens() throws Exception {
    for (int i = 16; i > 0; i--) published.add(entry(i, "Entry " + i));
    published.get(1).setSubject(" ");

    final JsonNode first = json("/users/bob/outbox?page=1");
    assertEquals(ACTOR + "/outbox?page=1", first.get("id").asText());
    assertEquals("OrderedCollectionPage", first.get("type").asText());
    assertEquals(ACTOR + "/outbox", first.get("partOf").asText());
    assertEquals(15, first.get("orderedItems").size());
    assertEquals(ACTOR + "/outbox?page=2", first.get("next").asText());
    assertFalse(first.has("prev"));

    final JsonNode create = first.get("orderedItems").get(0);
    assertEquals("Create", create.get("type").asText());
    assertEquals(ACTOR + "/entry/16#create", create.get("id").asText());
    assertEquals(ACTOR, create.get("actor").asText());
    assertEquals("2024-03-10T12:00:00.000Z", create.get("published").asText());
    assertEquals(ActivityStreamController.PUBLIC, create.get("to").get(0).asText());
    assertEquals(ACTOR + "/followers", create.get("cc").get(0).asText());
    assertEquals(ACTOR + "/entry/16", create.get("object").get("id").asText());
    assertEquals("Article", create.get("object").get("type").asText());
    assertFalse(create.get("object").has("@context"));
    assertEquals("Note", first.get("orderedItems").get(1).get("object").get("type").asText());

    final JsonNode second = json("/users/bob/outbox?page=2");
    assertEquals(1, second.get("orderedItems").size());
    assertEquals(
        ACTOR + "/entry/1", second.get("orderedItems").get(0).get("object").get("id").asText());
    assertEquals(ACTOR + "/outbox?page=1", second.get("prev").asText());
    assertFalse(second.has("next"));

    as("/users/bob/outbox?page=3", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());
    as("/users/bob/outbox?page=0", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());
  }

  @Test
  void outbox_asksForPublicFinishedEntriesNewestFirst() throws Exception {
    json("/users/bob/outbox?page=1");

    final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(entryRepository)
        .findByUserAndSecurityAndDraft(
            eq(bob), eq(Security.PUBLIC), eq(PrefBool.N), pageable.capture());
    assertEquals(15, pageable.getValue().getPageSize());
    assertEquals(
        Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("date").getDirection());
  }

  @Test
  void outbox_carriesAttachmentsFromTheFeedRules() throws Exception {
    final Entry entry = entry(4, "Pictured");
    published.add(entry);
    when(entryAttachmentService.forFeed(List.of(entry)))
        .thenReturn(Map.of(4, pictureAndLink("https://example.com/a", "Example")));

    final JsonNode object =
        json("/users/bob/outbox?page=1").get("orderedItems").get(0).get("object");

    assertEquals(2, object.get("attachment").size());
  }

  // ---- entries --------------------------------------------------------------------------

  @Test
  void entry_withASubjectIsAnArticle() throws Exception {
    final Entry entry = entry(5, "Hello & welcome");
    entry.setModified(date(2024, 3, 12));
    tag(entry, "travel", "cats");
    attach(entry, pictureAndLink("https://example.com/a?b=1&c=2", "Example <Site> & \"Co\""));

    final JsonNode object = json("/users/bob/entry/5");

    assertEquals("https://www.w3.org/ns/activitystreams", object.get("@context").asText());
    assertEquals(ACTOR + "/entry/5", object.get("id").asText());
    assertEquals("Article", object.get("type").asText());
    assertEquals("Hello & welcome", object.get("name").asText());
    assertEquals(ACTOR + "/entry/5", object.get("url").asText());
    assertEquals(ACTOR, object.get("attributedTo").asText());
    assertEquals(ActivityStreamController.PUBLIC, object.get("to").get(0).asText());
    assertEquals(ACTOR + "/followers", object.get("cc").get(0).asText());
    assertEquals("2024-03-10T12:00:00.000Z", object.get("published").asText());
    assertEquals("2024-03-12T12:00:00.000Z", object.get("updated").asText());
    assertFalse(object.has("summary"));
    assertEquals(
        "<p>Body of 5</p><p><a href=\"https://example.com/a?b=1&amp;c=2\">"
            + "Example &lt;Site&gt; &amp; &quot;Co&quot;</a></p>",
        object.get("content").asText());

    assertEquals(2, object.get("tag").size());
    final JsonNode tag = object.get("tag").get(0);
    assertEquals("Hashtag", tag.get("type").asText());
    assertEquals("#cats", tag.get("name").asText());
    assertEquals(ACTOR + "/tag/cats", tag.get("href").asText());

    final JsonNode picture = object.get("attachment").get(0);
    assertEquals("Image", picture.get("type").asText());
    assertEquals("image/png", picture.get("mediaType").asText());
    assertEquals("http://www.justjournal.com/EntryImage/3", picture.get("url").asText());
    assertEquals("A cat", picture.get("name").asText());

    final JsonNode link = object.get("attachment").get(1);
    assertEquals("Link", link.get("type").asText());
    assertEquals("https://example.com/a?b=1&c=2", link.get("href").asText());
    assertEquals("Example <Site> & \"Co\"", link.get("name").asText());
    assertEquals("text/html", link.get("mediaType").asText());
    assertFalse(link.has("preview"));
    assertFalse(object.toString().contains("EntryLinkImage"));
  }

  @Test
  void entry_withoutASubjectIsANote() throws Exception {
    final Entry entry = entry(6, "  ");
    tag(entry, "short");
    attach(entry, pictureAndLink("https://example.com/b", "B"));

    final JsonNode object = json("/users/bob/entry/6");

    assertEquals("Note", object.get("type").asText());
    assertFalse(object.has("name"));
    assertFalse(object.has("summary"));
    assertFalse(object.has("updated"));
    assertEquals(
        "<p>Body of 6</p><p><a href=\"https://example.com/b\">B</a></p>",
        object.get("content").asText());
    assertEquals("#short", object.get("tag").get(0).get("name").asText());
    assertEquals("Image", object.get("attachment").get(0).get("type").asText());
    assertEquals("Link", object.get("attachment").get(1).get("type").asText());

    entry.setSubject(null);
    assertEquals("Note", json("/users/bob/entry/6").get("type").asText());
  }

  @Test
  void entry_doesNotRepeatALinkTheBodyAlreadyHas() throws Exception {
    final Entry entry = entry(5, "Hello");
    entry.setBody("See <a href=\"https://example.com/a?b=1&amp;c=2\">this</a>");
    attach(entry, pictureAndLink("https://example.com/a?b=1&c=2", "Example"));

    final JsonNode object = json("/users/bob/entry/5");

    assertEquals(
        "<p>See <a href=\"https://example.com/a?b=1&amp;c=2\">this</a></p>",
        object.get("content").asText());
  }

  @Test
  void entry_hasNoAttachmentsTheFeedsWouldNotShow() throws Exception {
    // forFeed leaves out unapproved pictures and blocked links
    entry(5, "Hello");

    final JsonNode object = json("/users/bob/entry/5");

    assertFalse(object.has("attachment"));
    assertFalse(object.has("tag"));
    assertEquals("<p>Body of 5</p>", object.get("content").asText());
  }

  @Test
  void entry_showsOnlyTheLinkWhenThePictureIsHeldBack() throws Exception {
    final Entry entry = entry(5, "Hello");
    attach(
        entry,
        new FeedAttachments(
            null, null, null, 0, 0, 0, null, "https://example.com/c", "C", null, null, 0, 0));

    final JsonNode object = json("/users/bob/entry/5");

    assertEquals(1, object.get("attachment").size());
    assertEquals("Link", object.get("attachment").get(0).get("type").asText());
  }

  @Test
  void entry_isNotFoundUnlessAnyoneMayReadIt() throws Exception {
    final Entry draft = entry(1, "Draft");
    draft.setDraft(PrefBool.Y);
    entry(2, "Private").setSecurity(Security.PRIVATE);
    entry(3, "Friends").setSecurity(Security.FRIENDS);
    final User alice = new User();
    alice.setId(9);
    alice.setUsername("alice");
    final Friend friend = new Friend();
    friend.setUser(bob);
    friend.setFriend(alice);
    bob.getFriends().add(friend);
    entry(4, "Public");

    for (final int id : List.of(1, 2, 3, 99)) {
      as("/users/bob/entry/" + id, ActivityStreamController.ACTIVITY_JSON_VALUE)
          .andExpect(status().isNotFound())
          .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT));
    }
    // someone else's address for bob's entry
    as("/users/alice/entry/4", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());

    as("/users/bob/entry/4", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isOk());
    journal.setOwnerViewOnly(true);
    as("/users/bob/entry/4", ActivityStreamController.ACTIVITY_JSON_VALUE)
        .andExpect(status().isNotFound());
  }

  @Test
  void objectType_followsTheSubject() {
    final Entry entry = new Entry();
    entry.setSubject("Title");
    assertEquals("Article", ActivityStreamController.objectType(entry));
    entry.setSubject("");
    assertEquals("Note", ActivityStreamController.objectType(entry));
    entry.setSubject(null);
    assertEquals("Note", ActivityStreamController.objectType(entry));
  }
}
