/*
 * Copyright (c) 2003-2021 Lucas Holt
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

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.justjournal.atom.AtomFeed;
import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.core.Settings;
import com.justjournal.core.UserContext;
import com.justjournal.core.UserContextService;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Comment;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.Favorite;
import com.justjournal.model.FormatType;
import com.justjournal.model.Friend;
import com.justjournal.model.Journal;
import com.justjournal.model.Location;
import com.justjournal.model.Mood;
import com.justjournal.model.MoodThemeData;
import com.justjournal.model.RssSubscription;
import com.justjournal.model.Security;
import com.justjournal.model.Tag;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.model.UserImage;
import com.justjournal.model.api.TrackbackTo;
import com.justjournal.model.search.BlogEntry;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.FavoriteRepository;
import com.justjournal.repository.MoodThemeDataRepository;
import com.justjournal.repository.RssSubscriptionsRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.rss.CachedHeadlineBean;
import com.justjournal.rss.Rss;
import com.justjournal.services.AvatarService;
import com.justjournal.services.BlogSearchService;
import com.justjournal.services.EntryService;
import com.justjournal.services.MarkdownService;
import com.justjournal.services.PdfFormatService;
import com.justjournal.services.TrackbackService;
import com.justjournal.services.UserImageService;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

/**
 * Drives the journal viewer through MockMvc with every collaborator mocked. The HTML the
 * controller builds for each page goes into the model, so the tests read it back from there.
 */
class UsersControllerTests {

  private static final String BLOG = "bob";
  private static final int BLOG_ID = 7;
  private static final String BASE = "http://localhost/users/" + BLOG;

  private CommentRepository commentDao;
  private EntryRepository entryDao;
  private EntryService entryService;
  private FavoriteRepository favoriteRepository;
  private MoodThemeDataRepository emoticonDao;
  private UserRepository userRepository;
  private RssSubscriptionsRepository rssSubscriptionsDAO;
  private UserImageService userImageService;
  private AvatarService avatarService;
  private BlogSearchService blogSearchService;
  private Rss rss;
  private TrackbackService trackbackService;
  private CachedHeadlineBean cachedHeadlineBean;
  private UserContextService userContextService;
  private PdfFormatService pdfFormatService;
  private AtomFeed atom;
  private MarkdownService markdownService;
  private Settings settings;

  private MockMvc mockMvc;
  private User blogUser;
  private Journal journal;

  @BeforeEach
  void setUp() {
    commentDao = mock(CommentRepository.class);
    entryDao = mock(EntryRepository.class);
    entryService = mock(EntryService.class);
    favoriteRepository = mock(FavoriteRepository.class);
    emoticonDao = mock(MoodThemeDataRepository.class);
    userRepository = mock(UserRepository.class);
    rssSubscriptionsDAO = mock(RssSubscriptionsRepository.class);
    userImageService = mock(UserImageService.class);
    avatarService = mock(AvatarService.class);
    blogSearchService = mock(BlogSearchService.class);
    rss = mock(Rss.class);
    trackbackService = mock(TrackbackService.class);
    cachedHeadlineBean = mock(CachedHeadlineBean.class);
    userContextService = mock(UserContextService.class);
    pdfFormatService = mock(PdfFormatService.class);
    atom = mock(AtomFeed.class);
    markdownService = mock(MarkdownService.class);
    settings = mock(Settings.class);

    when(settings.getBlogBaseUrl(anyString()))
        .thenAnswer(inv -> "http://localhost/users/" + inv.getArgument(0));
    when(settings.getBaseUri()).thenReturn("http://localhost/");
    when(settings.getWebmaster()).thenReturn("webmaster@example.com");
    when(markdownService.convertToHtml(anyString()))
        .thenAnswer(inv -> "<md>" + inv.getArgument(0) + "</md>");

    final UsersController controller =
        new UsersController(
            entryService,
            commentDao,
            entryDao,
            favoriteRepository,
            emoticonDao,
            userRepository,
            rssSubscriptionsDAO,
            userImageService,
            avatarService,
            blogSearchService,
            rss,
            markdownService,
            atom,
            trackbackService,
            cachedHeadlineBean,
            userContextService,
            pdfFormatService,
            settings);

    // the real pages are Thymeleaf templates; here the view name and model are what matter
    final View noView = (m, request, response) -> {};
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ExceptionHandlerConfig())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setViewResolvers((name, locale) -> noView)
            .build();

    blogUser = user(BLOG_ID, BLOG);
    journal = journal(blogUser, "Bob's Blog");
  }

  // ---- fixtures -------------------------------------------------------------------------

  private static User user(final int id, final String username) {
    final User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setName(username.substring(0, 1).toUpperCase() + username.substring(1));
    user.setSince(2020);
    return user;
  }

  private static Journal journal(final User owner, final String name) {
    final Journal journal = new Journal();
    journal.setName(name);
    journal.setUser(owner);
    owner.getJournals().add(journal);
    return journal;
  }

  private static UserContact contact(final User user, final String email) {
    final UserContact contact = new UserContact();
    contact.setEmail(email);
    ReflectionTestUtils.setField(user, "userContact", contact);
    return contact;
  }

  private static void befriend(final User user, final User other) {
    final Friend friend = new Friend();
    friend.setUser(user);
    friend.setFriend(other);
    user.getFriends().add(friend);
  }

  private static Date at(final int year, final int month, final int day, final int hour) {
    return new GregorianCalendar(year, month - 1, day, hour, 30).getTime();
  }

  /** The day heading the pages print, in whatever locale the JVM runs with. */
  private static String day(final Date date) {
    return new SimpleDateFormat("EEE, d MMM yyyy").format(date);
  }

  private static Entry entry(
      final int id, final User owner, final Security security, final Date date) {
    final Entry entry = new Entry();
    entry.setId(id);
    entry.setUser(owner);
    entry.setSecurity(security);
    entry.setDate(date);
    entry.setModified(date);
    entry.setSubject("Entry " + id);
    entry.setBody("Body of " + id);
    entry.setFormat(FormatType.TEXT);
    return entry;
  }

  private static Entry publicEntry(final int id, final User owner) {
    return entry(id, owner, Security.PUBLIC, at(2024, 3, 10, 9));
  }

  private static Set<EntryTag> tags(final String... names) {
    final Set<EntryTag> tags = new LinkedHashSet<>();
    for (final String name : names) {
      final EntryTag entryTag = new EntryTag();
      entryTag.setTag(new Tag(name));
      tags.add(entryTag);
    }
    return tags;
  }

  private static Mood mood(final int id, final String title) {
    final Mood mood = new Mood();
    mood.setId(id);
    mood.setTitle(title);
    return mood;
  }

  private static Location location(final int id, final String title) {
    final Location location = new Location();
    location.setId(id);
    location.setTitle(title);
    return location;
  }

  private static Comment comment(final int id, final User author, final FormatType format) {
    final Comment comment = new Comment();
    comment.setId(id);
    comment.setUser(author);
    comment.setDate(at(2024, 3, 11, 8));
    comment.setSubject("Re: " + id);
    comment.setBody("Comment body " + id);
    comment.setFormat(format);
    return comment;
  }

  private static MockHttpSession loggedInAs(final User user) {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, user.getId());
    session.setAttribute(LOGIN_ATTRNAME, user.getUsername());
    return session;
  }

  /** The blog is viewed by an anonymous visitor. */
  private void anonymous() {
    when(userContextService.getUserContext(eq(BLOG), any()))
        .thenReturn(new UserContext(blogUser, null));
  }

  /** The blog is viewed by {@code viewer}, who is logged in. */
  private MockHttpSession viewedBy(final User viewer) {
    when(userContextService.getUserContext(eq(BLOG), any()))
        .thenReturn(new UserContext(blogUser, viewer));
    when(userRepository.findByUsername(viewer.getUsername())).thenReturn(viewer);
    return loggedInAs(viewer);
  }

  private static String modelString(
      final org.springframework.test.web.servlet.MvcResult result, final String name) {
    return (String) result.getModelAndView().getModel().get(name);
  }

  // ---- common page behaviour ----------------------------------------------------------------

  @Test
  void unknownUser_isNotFoundOnEveryPage() throws Exception {
    when(userContextService.getUserContext(eq("nobody"), any())).thenReturn(null);
    when(userRepository.findByUsername("nobody")).thenReturn(null);

    for (final String path :
        List.of(
            "",
            "/entry/1",
            "/favorites",
            "/friends",
            "/calendar",
            "/2024",
            "/2024/3",
            "/2024/3/10",
            "/pictures",
            "/pictures/1",
            "/search?max=5&bquery=x",
            "/subscriptions",
            "/tag/foo",
            "/atom",
            "/rss",
            "/rsspics")) {
      mockMvc.perform(get("/users/nobody" + path)).andExpect(status().isNotFound());
    }
    mockMvc.perform(get("/users/nobody/json")).andExpect(status().isNotFound());
    mockMvc.perform(get("/users/nobody/pdf")).andExpect(status().isNotFound());
  }

  @Test
  void ownerOnlyJournal_isForbiddenToVisitorsOnEveryPage() throws Exception {
    journal.setOwnerViewOnly(true);
    anonymous();
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);

    for (final String path :
        List.of(
            "",
            "/entry/1",
            "/favorites",
            "/friends",
            "/calendar",
            "/2024",
            "/2024/3",
            "/2024/3/10",
            "/pictures",
            "/pictures/1",
            "/search?max=5&bquery=x",
            "/subscriptions",
            "/tag/foo",
            "/atom",
            "/rss",
            "/rsspics",
            "/json",
            "/pdf")) {
      mockMvc.perform(get("/users/" + BLOG + path)).andExpect(status().isForbidden());
    }
  }

  @Test
  void ownerOnlyJournal_isShownToItsOwner() throws Exception {
    journal.setOwnerViewOnly(true);
    final MockHttpSession session = viewedBy(blogUser);
    when(entryDao.findByUserOrderByDateDesc(eq(blogUser), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/users/" + BLOG).session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("users"))
        .andExpect(model().attribute("authenticatedUsername", BLOG))
        .andExpect(model().attribute("journal", journal));
  }

  // ---- entries ------------------------------------------------------------------------------

  @Test
  void entries_visitorSeesPublicEntriesGroupedByDay() throws Exception {
    anonymous();
    final Entry first = entry(1, blogUser, Security.PUBLIC, at(2024, 3, 10, 9));
    first.setBody("line one\nline two");
    first.setTags(tags("java", "spring"));
    first.setMood(mood(3, "happy"));
    first.setLocation(location(2, "Home"));
    first.setMusic("Song & Dance");
    final Entry second = entry(2, blogUser, Security.PUBLIC, at(2024, 3, 10, 14));
    second.setFormat(FormatType.HTML);
    second.setBody("<p>html body</p>");
    second.getComments().add(comment(50, blogUser, FormatType.TEXT));
    final Entry third = entry(3, blogUser, Security.PUBLIC, at(2024, 3, 9, 8));
    third.setFormat(FormatType.MARKDOWN);
    third.setBody("*md*");
    third.getComments().add(comment(51, blogUser, FormatType.TEXT));
    third.getComments().add(comment(52, blogUser, FormatType.TEXT));

    final MoodThemeData emoto = new MoodThemeData();
    emoto.setFileName("happy.gif");
    emoto.setWidth(16);
    emoto.setHeight(16);
    when(emoticonDao.findByThemeIdAndMoodId(1, 3)).thenReturn(emoto);
    when(entryDao.findByUserAndSecurityOrderByDateDesc(
            eq(blogUser), eq(Security.PUBLIC), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(first, second, third)));
    when(avatarService.isAvatarAvailable(blogUser)).thenReturn(true);

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG).param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(view().name("users"))
                .andExpect(model().attribute("avatar", true))
                .andExpect(model().attribute("authenticatedUsername", nullValue()))
                .andExpect(model().attribute("calendarMini", containsString("could not render")))
                .andReturn(),
            "entries");

    // one heading per day, in the order the repository returned them
    assertEquals(2, html.split("<h2>").length - 1, html);
    assertTrue(html.indexOf(day(at(2024, 3, 10, 9))) < html.indexOf(day(at(2024, 3, 9, 8))), html);
    // text bodies get line breaks, html passes through, markdown is converted
    assertTrue(html.contains("<p>line one<br>line two</p>"), html);
    assertTrue(html.contains("<p>html body</p>"), html);
    assertTrue(html.contains("<md>*md*</md>"), html);
    // metadata
    assertTrue(html.contains("/users/bob/tag/java\">java</a>"), html);
    assertTrue(html.contains("emoticons/1/happy.gif"), html);
    assertTrue(html.contains("location: Home"), html);
    assertTrue(html.contains("music: Song &amp; Dance"), html);
    // comment counts
    assertTrue(html.contains("title=\"View Comment\">1 comment</a>"), html);
    assertTrue(html.contains("title=\"View Comments\">2 comments</a>"), html);
    // a visitor gets no edit controls
    assertFalse(html.contains("Delete Entry"), html);
    verify(entryDao, never()).findByUserOrderByDateDesc(any(), any());
  }

  @Test
  void entries_ownerSeesEveryEntryWithEditControls() throws Exception {
    final MockHttpSession session = viewedBy(blogUser);
    final Entry secret = entry(1, blogUser, Security.PRIVATE, at(2024, 3, 10, 9));
    secret.setBody("mac\rline");
    secret.setMood(mood(4, "sad"));
    final Entry friends = entry(2, blogUser, Security.FRIENDS, at(2024, 3, 10, 10));
    friends.setBody("one long line");
    when(emoticonDao.findByThemeIdAndMoodId(1, 4)).thenReturn(null);
    when(entryDao.findByUserOrderByDateDesc(eq(blogUser), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(secret, friends)));

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG).session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("authenticatedUsername", BLOG))
                .andReturn(),
            "entries");

    assertTrue(html.contains("<p>mac<br>line</p>"), html);
    assertTrue(html.contains("<p>one long line</p>"), html);
    assertTrue(html.contains("alt=\"private\""), html);
    assertTrue(html.contains("alt=\"friends\""), html);
    // a mood without theme art still shows its name
    assertTrue(html.contains("mood: sad</span>"), html);
    assertTrue(html.contains("href=\"/entry/1\" title=\"Edit Entry\""), html);
    assertTrue(html.contains("deleteEntry(2)"), html);
    verify(entryDao, never()).findByUserAndSecurityOrderByDateDesc(any(), any(), any());
  }

  @Test
  void entries_repositoryFailure_isReportedAsServiceError() throws Exception {
    anonymous();
    when(entryDao.findByUserAndSecurityOrderByDateDesc(any(), any(), any(Pageable.class)))
        .thenThrow(new IllegalStateException("db down"));

    mockMvc.perform(get("/users/" + BLOG)).andExpect(status().isInternalServerError());
  }

  @Test
  void miniCalendar_rendersCurrentMonthWhenItHasEntries() throws Exception {
    anonymous();
    final Calendar now = Calendar.getInstance();
    final Entry today = entry(9, blogUser, Security.PUBLIC, now.getTime());
    when(entryDao.findByUsernameAndYearAndMonthAndSecurity(
            BLOG, now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, Security.PUBLIC))
        .thenReturn(List.of(today));
    when(entryDao.findByUserAndSecurityOrderByDateDesc(any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    final String mini =
        modelString(
            mockMvc.perform(get("/users/" + BLOG)).andExpect(status().isOk()).andReturn(),
            "calendarMini");

    assertTrue(mini.contains("minicalendar"), mini);
    assertTrue(mini.contains("/users/bob/"), mini);
  }

  // ---- single entry -------------------------------------------------------------------------

  @Test
  void entry_publicEntryWithCommentsAndTrackbacks() throws Exception {
    anonymous();
    final User alice = user(8, "alice");
    final Entry entry = publicEntry(5, blogUser);
    entry.setTags(tags("one"));
    when(entryDao.findById(5)).thenReturn(Optional.of(entry));
    when(commentDao.findByEntryId(5))
        .thenReturn(List.of(comment(1, alice, FormatType.TEXT), comment(2, alice, FormatType.MARKDOWN)));
    final TrackbackTo trackback = new TrackbackTo();
    trackback.setSubject("Pinged <you>");
    trackback.setAuthorName("Carol");
    trackback.setAuthorEmail("carol@example.com");
    trackback.setUrl("http://example.com/post");
    trackback.setBody("excerpt");
    trackback.setDate(at(2024, 3, 12, 1));
    when(trackbackService.getByEntry(5)).thenReturn(List.of(trackback));

    final var result =
        mockMvc
            .perform(get("/users/" + BLOG + "/entry/5"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("entry", entry))
            .andReturn();
    final String html = modelString(result, "entry_format");

    assertTrue(html.contains("<h2>" + day(at(2024, 3, 10, 9)) + "</h2>"), html);
    assertTrue(html.contains("trackback:ping=\"http://localhost//trackback?entryID=5\""), html);
    assertTrue(html.contains("dc:identifier=\"" + BASE + "/entry/5#e5\""), html);
    assertTrue(html.contains("2 comments</div>"), html);
    assertTrue(html.contains("<p>Comment body 1</p>"), html);
    assertTrue(html.contains("<md>Comment body 2</md>"), html);
    assertTrue(html.contains("<h3>Trackbacks</h3>"), html);
    assertTrue(html.contains("Pinged &lt;you&gt;"), html);
    assertTrue(html.contains("mailto:carol&#x40;example.com\">carol&#x40;example.com</a>"), html);
    assertTrue(html.contains("<p>excerpt</p>"), html);
    // nobody is logged in, so no comment edit actions
    assertFalse(html.contains("Delete Comment"), html);
  }

  @Test
  void entry_commentAuthorCanEditOwnComment() throws Exception {
    final User alice = user(8, "alice");
    final MockHttpSession session = viewedBy(alice);
    final Entry entry = publicEntry(5, blogUser);
    when(entryDao.findById(5)).thenReturn(Optional.of(entry));
    when(commentDao.findByEntryId(5)).thenReturn(List.of(comment(1, alice, FormatType.TEXT)));
    when(trackbackService.getByEntry(5)).thenReturn(List.of());

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/entry/5").session(session))
                .andExpect(status().isOk())
                .andReturn(),
            "entry_format");

    assertTrue(html.contains("/comment/5/edit/1\" title=\"Edit Comment\""), html);
    assertTrue(html.contains("deleteComment(1)"), html);
    assertFalse(html.contains("Delete Entry"), html);
  }

  @Test
  void entry_friendsOnlyEntry_sharedOnFacebookByOwner() throws Exception {
    final MockHttpSession session = viewedBy(blogUser);
    final Entry entry = entry(6, blogUser, Security.FRIENDS, at(2024, 3, 10, 9));
    when(entryDao.findById(6)).thenReturn(Optional.of(entry));
    when(commentDao.findByEntryId(6)).thenReturn(List.of());
    when(trackbackService.getByEntry(6)).thenReturn(List.of());

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/entry/6").session(session))
                .andExpect(status().isOk())
                .andReturn(),
            "entry_format");

    assertTrue(html.contains("fb-share-button"), html);
    assertTrue(html.contains("Delete Entry"), html);
    assertTrue(html.contains("0 comments</div>"), html);
  }

  @Test
  void entry_hiddenEntries_renderNothing() throws Exception {
    final User alice = user(8, "alice");
    final User carol = user(9, "carol");
    befriend(blogUser, alice);

    final Entry missing = null;
    final Entry someoneElses = publicEntry(10, alice);
    final Entry secret = entry(11, blogUser, Security.PRIVATE, at(2024, 1, 1, 1));
    final Entry forFriends = entry(12, blogUser, Security.FRIENDS, at(2024, 1, 1, 1));
    when(entryDao.findById(1)).thenReturn(Optional.ofNullable(missing));
    when(entryDao.findById(10)).thenReturn(Optional.of(someoneElses));
    when(entryDao.findById(11)).thenReturn(Optional.of(secret));
    when(entryDao.findById(12)).thenReturn(Optional.of(forFriends));

    anonymous();
    for (final int id : new int[] {1, 10, 11, 12}) {
      mockMvc
          .perform(get("/users/" + BLOG + "/entry/" + id))
          .andExpect(status().isOk())
          .andExpect(model().attribute("entry", nullValue()))
          .andExpect(model().attribute("entry_format", ""));
    }

    // carol is logged in but not a friend: friends-only stays hidden
    MockHttpSession session = viewedBy(carol);
    mockMvc
        .perform(get("/users/" + BLOG + "/entry/12").session(session))
        .andExpect(model().attribute("entry", nullValue()));
    mockMvc
        .perform(get("/users/" + BLOG + "/entry/11").session(session))
        .andExpect(model().attribute("entry", nullValue()));

    // alice is a friend: she sees the friends-only entry but not the private one
    session = viewedBy(alice);
    when(commentDao.findByEntryId(12)).thenReturn(List.of());
    when(trackbackService.getByEntry(12)).thenReturn(List.of());
    mockMvc
        .perform(get("/users/" + BLOG + "/entry/12").session(session))
        .andExpect(model().attribute("entry", forFriends));
    mockMvc
        .perform(get("/users/" + BLOG + "/entry/11").session(session))
        .andExpect(model().attribute("entry", nullValue()));

    // the owner sees everything
    session = viewedBy(blogUser);
    when(commentDao.findByEntryId(11)).thenReturn(List.of());
    when(trackbackService.getByEntry(11)).thenReturn(List.of());
    mockMvc
        .perform(get("/users/" + BLOG + "/entry/11").session(session))
        .andExpect(model().attribute("entry", secret));
  }

  @Test
  void entry_formatterFailure_showsErrorPage() throws Exception {
    anonymous();
    final Entry entry = publicEntry(5, blogUser);
    when(entryDao.findById(5)).thenReturn(Optional.of(entry));
    when(commentDao.findByEntryId(5)).thenThrow(new IllegalStateException("boom"));

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/entry/5"))
                .andExpect(status().isOk())
                .andReturn(),
            "entry_format");

    assertTrue(html.contains("Unable to retrieve journal entry"), html);
  }

  // ---- favorites ----------------------------------------------------------------------------

  private static Favorite favorite(final User user, final Entry entry) {
    final Favorite favorite = new Favorite();
    favorite.setUser(user);
    favorite.setEntry(entry);
    return favorite;
  }

  @Test
  void favorites_visitorSeesOnlyPublicOnes() throws Exception {
    anonymous();
    final User alice = user(8, "alice");
    final Entry pub = publicEntry(1, alice);
    pub.setBody("a\nb");
    pub.setTags(tags("t"));
    pub.setMood(mood(3, "happy"));
    pub.setLocation(location(2, "Home"));
    pub.setMusic("tune");
    pub.getComments().add(comment(1, alice, FormatType.TEXT));
    final Entry priv = entry(2, alice, Security.PRIVATE, at(2024, 3, 10, 9));
    final Entry forFriends = entry(3, alice, Security.FRIENDS, at(2024, 3, 10, 9));
    when(favoriteRepository.findByUser(blogUser))
        .thenReturn(List.of(favorite(blogUser, pub), favorite(blogUser, priv), favorite(blogUser, forFriends)));
    when(avatarService.isAvatarAvailable(alice)).thenReturn(true);
    final MoodThemeData emoto = new MoodThemeData();
    emoto.setFileName("happy.gif");
    when(emoticonDao.findByThemeIdAndMoodId(1, 3)).thenReturn(emoto);

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/favorites"))
                .andExpect(status().isOk())
                .andReturn(),
            "favorites");

    assertTrue(html.startsWith("<h2>Favorites</h2>"), html);
    assertTrue(html.contains("<p>a<br />b</p>"), html);
    assertTrue(html.contains("src=\"/Avatar/8\""), html);
    assertTrue(html.contains("tags: t</p>"), html);
    assertTrue(html.contains("happy.gif"), html);
    assertTrue(html.contains("location: Home"), html);
    assertTrue(html.contains("music: tune"), html);
    assertTrue(html.contains("1 comment</a>"), html);
    assertFalse(html.contains("Entry 2"), html);
    assertFalse(html.contains("Entry 3"), html);
    assertFalse(html.contains("Favorite\""), html);
  }

  @Test
  void favorites_ownerAndFriendsSeeMore() throws Exception {
    final User alice = user(8, "alice");
    befriend(blogUser, alice);
    befriend(alice, blogUser);
    final MockHttpSession session = viewedBy(blogUser);

    final Entry mine = entry(1, blogUser, Security.PRIVATE, at(2024, 3, 10, 9));
    mine.setFormat(FormatType.MARKDOWN);
    mine.setBody("*m*");
    final Entry friendsOnly = entry(2, alice, Security.FRIENDS, at(2024, 3, 11, 9));
    friendsOnly.setFormat(FormatType.HTML);
    friendsOnly.setBody("<b>h</b>");
    for (int i = 0; i < 2; i++) friendsOnly.getComments().add(comment(i, alice, FormatType.TEXT));
    when(favoriteRepository.findByUser(blogUser))
        .thenReturn(List.of(favorite(blogUser, mine), favorite(blogUser, friendsOnly)));

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/favorites").session(session))
                .andExpect(status().isOk())
                .andReturn(),
            "favorites");

    assertTrue(html.contains("<md>*m*</md>"), html);
    assertTrue(html.contains("<b>h</b>"), html);
    assertTrue(html.contains("alt=\"private\""), html);
    assertTrue(html.contains("alt=\"friends\""), html);
    assertTrue(html.contains("2 comments</a>"), html);
    assertTrue(html.contains("deleteFavorite(1)"), html);
    assertTrue(html.contains("addFavorite(2)"), html);
  }

  @Test
  void favorites_noneYet() throws Exception {
    anonymous();
    when(favoriteRepository.findByUser(blogUser)).thenReturn(List.of());

    mockMvc
        .perform(get("/users/" + BLOG + "/favorites"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("favorites", containsString("No favorite entries found")));
  }

  @Test
  void favorites_renderFailure_leavesTheAttributeOut() throws Exception {
    anonymous();
    final Entry broken = publicEntry(1, user(8, "alice"));
    broken.setMood(mood(3, "happy"));
    when(favoriteRepository.findByUser(blogUser)).thenReturn(List.of(favorite(blogUser, broken)));
    when(emoticonDao.findByThemeIdAndMoodId(1, 3)).thenThrow(new IllegalStateException("boom"));

    mockMvc
        .perform(get("/users/" + BLOG + "/favorites"))
        .andExpect(status().isOk())
        .andExpect(model().attributeDoesNotExist("favorites"));
  }

  // ---- friends ------------------------------------------------------------------------------

  private static Entry friendsPageEntry(final int id, final User owner) {
    // the friends page reads every field, so give it something for each
    final Entry entry = publicEntry(id, owner);
    entry.setLocation(location(0, ""));
    entry.setMood(mood(12, "Not specified"));
    entry.setMusic("");
    return entry;
  }

  @Test
  void friends_listsEntriesWithActionsForTheViewer() throws Exception {
    final User alice = user(8, "alice");
    final MockHttpSession session = viewedBy(blogUser);
    final Entry own = friendsPageEntry(1, blogUser);
    own.setBody("x\ny");
    own.setSecurity(Security.PRIVATE);
    final Entry theirs = friendsPageEntry(2, alice);
    theirs.setFormat(FormatType.MARKDOWN);
    theirs.setBody("*t*");
    theirs.setSecurity(Security.FRIENDS);
    theirs.setLocation(location(3, "Cafe"));
    theirs.setMood(mood(5, "tired"));
    theirs.setMusic("jazz");
    theirs.setTags(tags("a", "b"));
    theirs.getComments().add(comment(1, alice, FormatType.TEXT));
    final Entry html = friendsPageEntry(3, alice);
    html.setFormat(FormatType.HTML);
    html.setBody("<i>raw</i>");
    html.setDate(at(2024, 3, 9, 9));
    for (int i = 0; i < 3; i++) html.getComments().add(comment(10 + i, alice, FormatType.TEXT));
    final MoodThemeData emoto = new MoodThemeData();
    emoto.setFileName("tired.gif");
    when(emoticonDao.findByThemeIdAndMoodId(1, 5)).thenReturn(emoto);
    when(entryService.getFriendsEntries(BLOG)).thenReturn(List.of(own, theirs, html));
    when(avatarService.isAvatarAvailable(alice)).thenReturn(true);

    final String out =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/friends").session(session))
                .andExpect(status().isOk())
                .andReturn(),
            "friends");

    assertTrue(out.startsWith("<h2>Friends</h2>"), out);
    assertTrue(out.contains("<p>x<br />y</p>"), out);
    assertTrue(out.contains("<md>*t*</md>"), out);
    assertTrue(out.contains("<i>raw</i>"), out);
    assertTrue(out.contains("alt=\"private\""), out);
    assertTrue(out.contains("alt=\"friends\""), out);
    assertTrue(out.contains("location: Cafe"), out);
    assertTrue(out.contains("tired.gif"), out);
    assertTrue(out.contains("music: jazz"), out);
    assertTrue(out.contains("tags: a b</p>"), out);
    assertTrue(out.contains("src=\"/Avatar/8\""), out);
    assertTrue(out.contains("deleteEntry(1)"), out);
    assertTrue(out.contains("addFavorite(2)"), out);
    assertTrue(out.contains("1 comment</a>"), out);
    assertTrue(out.contains("3 comments</a>"), out);
  }

  @Test
  void friends_noEntries() throws Exception {
    anonymous();
    when(entryService.getFriendsEntries(BLOG)).thenReturn(List.of());

    mockMvc
        .perform(get("/users/" + BLOG + "/friends"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("friends", containsString("No friends entries found")));
  }

  @Test
  void friends_lookupFailure_leavesTheAttributeOut() throws Exception {
    anonymous();
    when(entryService.getFriendsEntries(BLOG)).thenThrow(new ServiceException("nope"));

    mockMvc
        .perform(get("/users/" + BLOG + "/friends"))
        .andExpect(status().isOk())
        .andExpect(model().attributeDoesNotExist("friends"));
  }

  @Test
  void friends_renderFailure_showsErrorPage() throws Exception {
    anonymous();
    final Entry broken = publicEntry(1, blogUser); // no location: the friends page can't cope
    when(entryService.getFriendsEntries(BLOG)).thenReturn(List.of(broken));

    mockMvc
        .perform(get("/users/" + BLOG + "/friends"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("friends", containsString("Error retrieving the friends entries")));
  }

  // ---- calendars ----------------------------------------------------------------------------

  @Test
  void calendar_listsYearsSinceTheBlogStarted() throws Exception {
    anonymous();
    final int thisYear = Calendar.getInstance().get(Calendar.YEAR);
    blogUser.setSince(thisYear - 2);

    mockMvc
        .perform(get("/users/" + BLOG + "/calendar"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("startYear", thisYear - 2))
        .andExpect(model().attribute("currentYear", thisYear))
        .andExpect(model().attribute("years", List.of(thisYear - 2, thisYear - 1, thisYear)));
  }

  @Test
  void calendarYear_rendersMonthsWithEntries() throws Exception {
    anonymous();
    when(entryDao.findByUsernameAndYearAndSecurity(BLOG, 2024, Security.PUBLIC))
        .thenReturn(List.of(publicEntry(1, blogUser), entry(2, blogUser, Security.PUBLIC, at(2024, 5, 2, 1))));

    final String html =
        modelString(
            mockMvc.perform(get("/users/" + BLOG + "/2024")).andExpect(status().isOk()).andReturn(),
            "calendar");

    assertTrue(html.contains("<h2>Calendar: 2024</h2>"), html);
    assertTrue(html.contains("href=\"/users/bob/2024\">2024</a>"), html);
    assertTrue(html.contains("fullcalendar"), html);
    assertFalse(html.contains("not available"), html);
  }

  @Test
  void calendarYear_ownerSeesPrivateEntries_andEmptyYearsSaySo() throws Exception {
    final MockHttpSession session = viewedBy(blogUser);
    when(entryDao.findByUsernameAndYear(BLOG, 2021)).thenReturn(List.of());

    mockMvc
        .perform(get("/users/" + BLOG + "/2021").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("calendar", containsString("Calendar data not available")));
    verify(entryDao, never()).findByUsernameAndYearAndSecurity(any(), anyInt(), any());
  }

  @Test
  void calendarYear_lookupFailure_isAServiceError() throws Exception {
    anonymous();
    when(entryDao.findByUsernameAndYearAndSecurity(BLOG, 2024, Security.PUBLIC))
        .thenThrow(new IllegalStateException("db"));

    mockMvc.perform(get("/users/" + BLOG + "/2024")).andExpect(status().isInternalServerError());
  }

  @Test
  void calendarMonth_listsEntriesByDay() throws Exception {
    anonymous();
    when(entryDao.findByUsernameAndYearAndMonthAndSecurity(BLOG, 2024, 3, Security.PUBLIC))
        .thenReturn(
            List.of(
                entry(1, blogUser, Security.PUBLIC, at(2024, 3, 10, 9)),
                entry(2, blogUser, Security.PUBLIC, at(2024, 3, 10, 15)),
                entry(3, blogUser, Security.PUBLIC, at(2024, 3, 12, 8))));

    final String html =
        modelString(
            mockMvc.perform(get("/users/" + BLOG + "/2024/3")).andExpect(status().isOk()).andReturn(),
            "calendar");

    assertTrue(html.contains("<h2>Calendar: 3/2024</h2>"), html);
    assertEquals(2, html.split("<strong>").length - 1, html);
    assertTrue(html.contains("href=\"03/10\">Entry 1</a>"), html);
    assertTrue(html.contains("href=\"03/12\">Entry 3</a>"), html);
  }

  @Test
  void calendarMonth_ownerAndLaterMonths() throws Exception {
    final MockHttpSession session = viewedBy(blogUser);
    when(entryDao.findByUsernameAndYearAndMonth(BLOG, 2024, 11))
        .thenReturn(List.of(entry(1, blogUser, Security.PRIVATE, at(2024, 11, 3, 9))));
    when(entryDao.findByUsernameAndYearAndMonth(BLOG, 2024, 12)).thenReturn(List.of());

    mockMvc
        .perform(get("/users/" + BLOG + "/2024/11").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("calendar", containsString("href=\"11/03\">Entry 1</a>")));
    mockMvc
        .perform(get("/users/" + BLOG + "/2024/12").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("calendar", containsString("Calendar data not available")));
  }

  @Test
  void calendarMonth_lookupFailure_isAServiceError() throws Exception {
    anonymous();
    when(entryDao.findByUsernameAndYearAndMonthAndSecurity(BLOG, 2024, 3, Security.PUBLIC))
        .thenThrow(new IllegalStateException("db"));

    mockMvc.perform(get("/users/" + BLOG + "/2024/3")).andExpect(status().isInternalServerError());
  }

  @Test
  void calendarDay_rendersTheDaysEntries() throws Exception {
    anonymous();
    when(entryDao.findByUsernameAndYearAndMonthAndDayAndSecurity(BLOG, 2024, 3, 10, Security.PUBLIC))
        .thenReturn(List.of(publicEntry(1, blogUser), entry(2, blogUser, Security.PUBLIC, at(2024, 3, 10, 12))));

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/2024/3/10"))
                .andExpect(status().isOk())
                .andReturn(),
            "calendar");

    assertEquals(1, html.split("<h2>").length - 1, html);
    assertTrue(html.contains("Entry 1"), html);
    assertTrue(html.contains("Entry 2"), html);
  }

  @Test
  void calendarDay_ownerWithNothingThatDay() throws Exception {
    final MockHttpSession session = viewedBy(blogUser);
    when(entryDao.findByUsernameAndYearAndMonthAndDay(BLOG, 2024, 3, 10)).thenReturn(List.of());

    mockMvc
        .perform(get("/users/" + BLOG + "/2024/3/10").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("calendar", containsString("Calendar data not available")));
  }

  @Test
  void calendarDay_lookupFailure_isAServiceError() throws Exception {
    anonymous();
    when(entryDao.findByUsernameAndYearAndMonthAndDayAndSecurity(BLOG, 2024, 3, 10, Security.PUBLIC))
        .thenThrow(new IllegalStateException("db"));

    mockMvc.perform(get("/users/" + BLOG + "/2024/3/10")).andExpect(status().isInternalServerError());
  }

  // ---- feeds --------------------------------------------------------------------------------

  @Test
  void atom_feedForPublicEntries() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    final List<Entry> entries = List.of(publicEntry(1, blogUser));
    when(entryDao.findByUserAndSecurityOrderByDateDesc(eq(blogUser), eq(Security.PUBLIC), any(Pageable.class)))
        .thenReturn(new PageImpl<>(entries));
    when(atom.toXml()).thenReturn("<feed/>");

    mockMvc
        .perform(get("/users/" + BLOG + "/atom"))
        .andExpect(status().isOk())
        .andExpect(content().string("<feed/>"));

    verify(atom).setTitle("Bob's Blog");
    verify(atom).setAlternateLink(BASE);
    verify(atom).setId(BASE + "/atom");
    verify(atom).setSelfLink("/users/bob/atom");
    verify(atom).populate(entries);
  }

  @Test
  void atom_failure_isAServerError() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenThrow(new IllegalStateException("db"));

    mockMvc.perform(get("/users/" + BLOG + "/atom")).andExpect(status().isInternalServerError());
  }

  @Test
  void rss_feedForPublicEntries() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    contact(blogUser, "bob@example.com");
    final List<Entry> entries = List.of(publicEntry(1, blogUser));
    when(entryDao.findByUserAndSecurityOrderByDateDesc(eq(blogUser), eq(Security.PUBLIC), any(Pageable.class)))
        .thenReturn(new PageImpl<>(entries));
    when(rss.toXml()).thenReturn("<rss/>");

    mockMvc
        .perform(get("/users/" + BLOG + "/rss"))
        .andExpect(status().isOk())
        .andExpect(content().string("<rss/>"));

    verify(rss).setTitle(BLOG);
    verify(rss).setLink(BASE);
    verify(rss).setSelfLink(BASE + "/rss");
    verify(rss).setWebMaster("webmaster@example.com");
    verify(rss).setManagingEditor("bob@example.com (Bob)");
    verify(rss).populate(entries);
  }

  @Test
  void rss_withoutContactDetails_isAServerError() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);

    mockMvc.perform(get("/users/" + BLOG + "/rss")).andExpect(status().isInternalServerError());
    mockMvc.perform(get("/users/" + BLOG + "/rsspics")).andExpect(status().isInternalServerError());
  }

  @Test
  void rssPictures_feed() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    contact(blogUser, "bob@example.com");
    when(rss.toXml()).thenReturn("<rss><pics/></rss>");

    mockMvc
        .perform(get("/users/" + BLOG + "/rsspics"))
        .andExpect(status().isOk())
        .andExpect(content().string("<rss><pics/></rss>"));

    verify(rss).setTitle("bob's pictures");
    verify(rss).setLink(BASE + "/pictures");
    verify(rss).setSelfLink(BASE + "/rsspics");
    verify(rss).populateImageList(BLOG_ID, BLOG);
  }

  @Test
  void jsonFeed_listsPublicEntriesWithPaging() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    final Entry entry = publicEntry(1, blogUser);
    entry.setTags(tags("java"));
    when(entryDao.findByUserAndSecurityOrderByDateDesc(eq(blogUser), eq(Security.PUBLIC), eq(PageRequest.of(2, 15))))
        .thenReturn(new PageImpl<>(List.of(entry)));
    when(entryService.convertBody(FormatType.TEXT, "Body of 1")).thenReturn("text body");
    when(entryService.convertBodyToHtml(FormatType.TEXT, "Body of 1")).thenReturn("<p>html body</p>");
    when(avatarService.isAvatarAvailable(blogUser)).thenReturn(true);

    mockMvc
        .perform(get("/users/" + BLOG + "/json").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(header().string("Link", "<" + BASE + "/json>; rel=\"canonical\""))
        .andExpect(jsonPath("$.title").value("Bob's Blog"))
        .andExpect(jsonPath("$.home_page_url").value(BASE))
        .andExpect(jsonPath("$.feed_url").value(BASE + "/json"))
        .andExpect(jsonPath("$.next_url").value(BASE + "/json?page=3"))
        .andExpect(jsonPath("$.icon").value("http://localhost/Avatar/7"))
        .andExpect(jsonPath("$.authors[0].name").value("Bob"))
        .andExpect(jsonPath("$.items[0].id").value("1"))
        .andExpect(jsonPath("$.items[0].url").value(BASE + "/entry/1"))
        .andExpect(jsonPath("$.items[0].title").value("Entry 1"))
        .andExpect(jsonPath("$.items[0].content_text").value("text body"))
        .andExpect(jsonPath("$.items[0].content_html").value("<p>html body</p>"))
        .andExpect(jsonPath("$.items[0].tags[0]").value("java"));
  }

  @Test
  void jsonFeed_noAvatar_andBadPage() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    when(entryDao.findByUserAndSecurityOrderByDateDesc(eq(blogUser), eq(Security.PUBLIC), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/users/" + BLOG + "/json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icon").doesNotExist())
        .andExpect(jsonPath("$.items").isEmpty());
    mockMvc.perform(get("/users/" + BLOG + "/json").param("page", "-1")).andExpect(status().isNotFound());
  }

  @Test
  void jsonFeed_failure_isAServerError() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    when(entryDao.findByUserAndSecurityOrderByDateDesc(any(), any(), any(Pageable.class)))
        .thenThrow(new IllegalStateException("db"));

    mockMvc.perform(get("/users/" + BLOG + "/json")).andExpect(status().isInternalServerError());
  }

  // ---- exports ------------------------------------------------------------------------------

  @Test
  void pdf_downloadsTheJournal() throws Exception {
    anonymous();
    doAnswer(
            inv -> {
              ((OutputStream) inv.getArgument(1)).write("%PDF".getBytes(StandardCharsets.US_ASCII));
              return null;
            })
        .when(pdfFormatService)
        .write(any(UserContext.class), any(OutputStream.class));

    mockMvc
        .perform(get("/users/" + BLOG + "/pdf"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"bob.pdf\""))
        .andExpect(header().string("Link", "<" + BASE + "/pdf>; rel=\"canonical\""))
        .andExpect(content().bytes("%PDF".getBytes(StandardCharsets.US_ASCII)));
  }

  @Test
  void pdf_ownerOnlyJournal_stillAvailableToOwner() throws Exception {
    journal.setOwnerViewOnly(true);
    final MockHttpSession session = viewedBy(blogUser);

    mockMvc.perform(get("/users/" + BLOG + "/pdf").session(session)).andExpect(status().isOk());
    verify(pdfFormatService).write(any(UserContext.class), any(OutputStream.class));
  }

  @Test
  void rtf_isGone() throws Exception {
    mockMvc.perform(get("/users/" + BLOG + "/rtf")).andExpect(status().isNotFound());
  }

  // ---- pictures -----------------------------------------------------------------------------

  @Test
  void pictures_listsTheAlbum() throws Exception {
    anonymous();
    final List<UserImage> images = List.of(new UserImage());
    when(userImageService.getUserImages(BLOG, false)).thenReturn(images);

    mockMvc
        .perform(get("/users/" + BLOG + "/pictures"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("pictures", images));
  }

  @Test
  void pictures_ownerSeesUnreviewedOnes() throws Exception {
    final MockHttpSession session = viewedBy(blogUser);
    when(userImageService.getUserImages(BLOG, true)).thenReturn(List.of());

    mockMvc.perform(get("/users/" + BLOG + "/pictures").session(session)).andExpect(status().isOk());
    verify(userImageService).getUserImages(BLOG, true);
  }

  @Test
  void picture_showsOnePictureWithNeighbours() throws Exception {
    anonymous();
    final UserImageService.PicturePage page =
        new UserImageService.PicturePage(new UserImage(), null, new UserImage());
    when(userImageService.getPicturePage(BLOG, 3, false)).thenReturn(Optional.of(page));
    when(avatarService.isAvatarAvailable(blogUser)).thenReturn(false);

    mockMvc
        .perform(get("/users/" + BLOG + "/pictures/3"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("picture", page))
        .andExpect(model().attribute("avatar", false));
  }

  @Test
  void picture_unknownId_isNotFound() throws Exception {
    anonymous();
    when(userImageService.getPicturePage(BLOG, 3, false)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/users/" + BLOG + "/pictures/3"))
        .andExpect(status().isNotFound())
        .andExpect(view().name("notfound"));
  }

  // ---- search -------------------------------------------------------------------------------

  private static BlogEntry hit(final int id, final String subject) {
    final BlogEntry hit = new BlogEntry();
    hit.setId(id);
    hit.setAuthor(BLOG);
    hit.setSubject(subject);
    hit.setBody("found " + id);
    hit.setDate(at(2024, 3, 10, 9));
    return hit;
  }

  @Test
  void search_visitorSearchesPublicEntries() throws Exception {
    anonymous();
    when(blogSearchService.publicSearch("coffee", BLOG, PageRequest.of(0, 5)))
        .thenReturn(new PageImpl<>(List.of(hit(1, "Coffee <time>"), hit(2, "More coffee"))));

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/search").param("max", "5").param("bquery", "coffee"))
                .andExpect(status().isOk())
                .andReturn(),
            "search");

    assertTrue(html.contains("Searching for <i>coffee</i>"), html);
    assertTrue(html.contains("href=\"/users/bob/entry/1\">Coffee &lt;time&gt;</a>"), html);
    assertTrue(html.contains("found 2"), html);
    verify(blogSearchService, never()).search(anyString(), anyString(), any());
  }

  @Test
  void search_ownerSearchesEverything_andBadMaxFallsBack() throws Exception {
    final MockHttpSession session = viewedBy(blogUser);
    when(blogSearchService.search("tea", BLOG, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(get("/users/" + BLOG + "/search").session(session).param("max", "lots").param("bquery", "tea"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("search", containsString("No items were found")));
  }

  @Test
  void search_blankTerm_showsNothing() throws Exception {
    anonymous();

    mockMvc
        .perform(get("/users/" + BLOG + "/search").param("max", "").param("bquery", " "))
        .andExpect(status().isOk())
        .andExpect(model().attribute("search", ""));
    verify(blogSearchService, never()).publicSearch(anyString(), anyString(), any());
  }

  @Test
  void search_nullResult_isTreatedAsNoHits() throws Exception {
    anonymous();
    when(blogSearchService.publicSearch(eq("x"), eq(BLOG), any())).thenReturn(null);

    mockMvc
        .perform(get("/users/" + BLOG + "/search").param("max", "5").param("bquery", "x"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("search", containsString("No items were found")));
  }

  // ---- subscriptions ------------------------------------------------------------------------

  @Test
  void subscriptions_rendersEachFeed() throws Exception {
    anonymous();
    final RssSubscription a = new RssSubscription();
    a.setUri("http://a.example/rss");
    final RssSubscription b = new RssSubscription();
    b.setUri("http://b.example/rss");
    when(rssSubscriptionsDAO.findByUser(blogUser)).thenReturn(List.of(a, b));
    when(cachedHeadlineBean.parse(anyString())).thenAnswer(inv -> "<feed>" + inv.getArgument(0) + "</feed>");

    mockMvc
        .perform(get("/users/" + BLOG + "/subscriptions"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("subscriptions", containsString("<h2>RSS Reader</h2>")))
        .andExpect(model().attribute("subscriptions", containsString("<feed>http://a.example/rss</feed>")))
        .andExpect(model().attribute("subscriptions", containsString("<feed>http://b.example/rss</feed>")));
  }

  @Test
  void subscriptions_failure_rendersNothing() throws Exception {
    anonymous();
    when(rssSubscriptionsDAO.findByUser(blogUser)).thenThrow(new IllegalStateException("db"));

    mockMvc
        .perform(get("/users/" + BLOG + "/subscriptions"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("subscriptions", nullValue()));
  }

  // ---- tags ---------------------------------------------------------------------------------

  @Test
  void tag_visitorSeesPublicEntriesWithTheTag() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    when(entryDao.findByUsernameAndSecurityAndTag(BLOG, Security.PUBLIC, "java"))
        .thenReturn(List.of(publicEntry(1, blogUser), entry(2, blogUser, Security.PUBLIC, at(2024, 2, 1, 1))));

    final String html =
        modelString(
            mockMvc
                .perform(get("/users/" + BLOG + "/tag/java"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("user", blogUser))
                .andExpect(model().attribute("authenticatedUsername", nullValue()))
                .andReturn(),
            "tags");

    assertEquals(2, html.split("<h2>").length - 1, html);
    assertTrue(html.contains("Entry 1"), html);
    assertTrue(html.contains("Entry 2"), html);
  }

  @Test
  void tag_ownerSeesAllEntriesWithTheTag() throws Exception {
    final MockHttpSession session = loggedInAs(blogUser);
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    when(entryDao.findByUsernameAndTag(BLOG, "java"))
        .thenReturn(List.of(entry(1, blogUser, Security.PRIVATE, at(2024, 2, 1, 1))));

    mockMvc
        .perform(get("/users/" + BLOG + "/tag/java").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("authenticatedUsername", BLOG))
        .andExpect(model().attribute("tags", containsString("Entry 1")));
  }

  @Test
  void tag_ownerOnlyJournal_stillShownToOwner() throws Exception {
    journal.setOwnerViewOnly(true);
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    when(entryDao.findByUsernameAndTag(BLOG, "java")).thenReturn(List.of());

    mockMvc
        .perform(get("/users/" + BLOG + "/tag/java").session(loggedInAs(blogUser)))
        .andExpect(status().isOk())
        .andExpect(model().attribute("tags", ""));
  }

  @Test
  void tag_lookupFailure_rendersNoEntries() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    when(entryDao.findByUsernameAndSecurityAndTag(BLOG, Security.PUBLIC, "java"))
        .thenThrow(new IllegalStateException("db"));

    mockMvc
        .perform(get("/users/" + BLOG + "/tag/java"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("tags", ""));
  }

  @Test
  void tag_journalLookupFailure_isAServerError() throws Exception {
    when(userRepository.findByUsername(BLOG)).thenReturn(user(BLOG_ID, BLOG)); // no journal

    mockMvc
        .perform(get("/users/" + BLOG + "/tag/java"))
        .andExpect(status().isInternalServerError())
        .andExpect(model().attributeDoesNotExist("tags"));
  }

  @Test
  void tag_viewerLookupFailure_isIgnored() throws Exception {
    final MockHttpSession session = loggedInAs(user(8, "alice"));
    when(userRepository.findByUsername("alice")).thenThrow(new IllegalStateException("db"));
    when(userRepository.findByUsername(BLOG)).thenReturn(blogUser);
    when(entryDao.findByUsernameAndSecurityAndTag(BLOG, Security.PUBLIC, "java")).thenReturn(List.of());

    mockMvc
        .perform(get("/users/" + BLOG + "/tag/java").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("tags", not(nullValue())));
  }
}
