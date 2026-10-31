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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.Journal;
import com.justjournal.model.Location;
import com.justjournal.model.Mood;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.Style;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.LocationRepository;
import com.justjournal.repository.MoodRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.BingService;
import com.justjournal.services.TrackbackService;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * Posts entries through the legacy update servlet as the web form, the mobile page, the dashboard
 * and the desktop client do, and checks what gets saved and what each client is told.
 */
class UpdateJournalTests {

  private static final int USER_ID = 1;
  private static final String USERNAME = "bob";

  private Settings settings;
  private EntryRepository entryRepository;
  private UserRepository userRepository;
  private LocationRepository locationDao;
  private MoodRepository moodDao;
  private Login webLogin;
  private TrackbackService trackbackService;
  private BingService bingService;

  private UpdateJournal servlet;
  private User user;
  private Journal journal;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    settings = mock(Settings.class);
    entryRepository = mock(EntryRepository.class);
    userRepository = mock(UserRepository.class);
    locationDao = mock(LocationRepository.class);
    moodDao = mock(MoodRepository.class);
    webLogin = mock(Login.class);
    trackbackService = mock(TrackbackService.class);
    bingService = mock(BingService.class);
    servlet =
        new UpdateJournal(
            settings,
            entryRepository,
            userRepository,
            locationDao,
            moodDao,
            webLogin,
            trackbackService,
            bingService);

    user = new User();
    user.setId(USER_ID);
    user.setUsername(USERNAME);
    final Style style = new Style();
    style.setId(3);
    journal = new Journal();
    journal.setName("Bob's Blog");
    journal.setStyle(style);
    journal.setUser(user);
    journal.setPingServices(false); // keep the test off the network
    user.getJournals().add(journal);

    when(settings.getSiteName()).thenReturn("JustJournal");
    when(settings.getBaseUri()).thenReturn("http://localhost/");
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(userRepository.findByUsername(USERNAME)).thenReturn(user);
    final Location home = new Location();
    home.setId(2);
    when(locationDao.findById(anyInt())).thenReturn(Optional.of(home));
    final Mood mood = new Mood();
    mood.setId(12);
    when(moodDao.findById(anyInt())).thenReturn(mood);
    when(entryRepository.save(any(Entry.class)))
        .thenAnswer(
            inv -> {
              final Entry entry = inv.getArgument(0);
              entry.setId(42);
              return entry;
            });
    when(entryRepository.findById(42)).thenAnswer(inv -> Optional.of(new Entry(42, "saved")));

    response = new MockHttpServletResponse();
  }

  /** A POST from the plain web form, with the fields a complete entry carries. */
  private static MockHttpServletRequest form() {
    final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/updateJournal");
    request.addHeader("User-Agent", "Mozilla/5.0");
    request.addHeader("Accept", "text/html");
    request.setParameter("security", "2");
    request.setParameter("location", "2");
    request.setParameter("mood", "12");
    request.setParameter("subject", "A day out");
    request.setParameter("body", "<p>We went to the <b>beach</b></p>");
    request.setParameter("date", "2024-03-10");
    request.setParameter("time", "T09:30:00");
    return request;
  }

  private static MockHttpServletRequest loggedIn(final MockHttpServletRequest request) {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, USER_ID);
    session.setAttribute(LOGIN_ATTRNAME, USERNAME);
    request.setSession(session);
    return request;
  }

  private String post(final MockHttpServletRequest request) throws Exception {
    servlet.service(request, response);
    return response.getContentAsString();
  }

  private Entry saved() {
    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    return captor.getValue();
  }

  // ---- authentication -----------------------------------------------------------------------

  @Test
  void webForm_wrongPassword_showsAuthenticationErrorPage() throws Exception {
    final MockHttpServletRequest request = form();
    request.setParameter("user", "Bob");
    request.setParameter("pass", "wrong");
    when(webLogin.validate("bob", "wrong")).thenReturn(0);

    final String html = post(request);

    assertTrue(html.contains("<title>Authentication Error</title>"), html);
    assertTrue(html.contains("Please check your username and password"), html);
    assertTrue(response.getContentType().startsWith("text/html"), response.getContentType());
    assertEquals("no-store, no-cache, must-revalidate", response.getHeader("Cache-Control"));
    verify(entryRepository, never()).save(any());
  }

  @Test
  void webForm_noCredentialsAtAll_isRefused() throws Exception {
    final String html = post(form());

    assertTrue(html.contains("Authentication Error"), html);
    verify(entryRepository, never()).save(any());
  }

  @Test
  void dashboardClient_wrongPassword_getsTheFailureToken() throws Exception {
    final MockHttpServletRequest request = form();
    request.setParameter("client", "dash");
    request.setParameter("user", "bob");
    request.setParameter("pass", "wrong");

    final String out = post(request);

    assertTrue(out.contains("JJ.LOGIN.FAIL"), out);
    assertFalse(out.contains("UPDATE"), out);
    assertEquals("text/plain", response.getContentType());
    verify(entryRepository, never()).save(any());
  }

  @Test
  void passwordLogin_withKeepLogin_startsTheSession() throws Exception {
    final MockHttpServletRequest request = form();
    request.setParameter("user", "BOB");
    request.setParameter("pass", "secret");
    request.setParameter("keeplogin", "checked");
    when(webLogin.validate(USERNAME, "secret")).thenReturn(USER_ID);

    final String html = post(request);

    assertTrue(html.contains("Entry added"), html);
    assertEquals(USER_ID, request.getSession().getAttribute(LOGIN_ATTRID));
    assertEquals(USERNAME, request.getSession().getAttribute(LOGIN_ATTRNAME));
    verify(webLogin).setLastLogin(USER_ID);
    assertEquals(user, saved().getUser());
  }

  @Test
  void passwordLogin_withoutKeepLogin_postsButLeavesNoSession() throws Exception {
    final MockHttpServletRequest request = form();
    request.setParameter("user", USERNAME);
    request.setParameter("pass", "secret");
    when(webLogin.validate(USERNAME, "secret")).thenReturn(USER_ID);

    post(request);

    assertNull(request.getSession().getAttribute(LOGIN_ATTRID));
    verify(webLogin, never()).setLastLogin(anyInt());
    verify(entryRepository).save(any(Entry.class));
  }

  // ---- what gets saved ----------------------------------------------------------------------

  @Test
  void webForm_savesAnHtmlEntryWithTheFormFields() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("music", "  Beach Boys ");
    request.setParameter("tags", "Summer, Beach; fun:2024");
    request.setParameter("trackback", " http://example.com/ping ");

    final String html = post(request);

    final Entry entry = saved();
    assertEquals(user, entry.getUser());
    assertEquals("A day out", entry.getSubject());
    assertEquals(Security.PUBLIC, entry.getSecurity());
    assertEquals(FormatType.HTML, entry.getFormat());
    assertEquals(PrefBool.N, entry.getAutoFormat());
    assertEquals(PrefBool.N, entry.getAllowComments());
    assertEquals(PrefBool.N, entry.getEmailComments());
    assertEquals(PrefBool.N, entry.getDraft());
    assertEquals("Beach Boys", entry.getMusic());
    assertEquals("http://example.com/ping", entry.getTrackback());
    assertEquals(2, entry.getLocation().getId());
    assertEquals(12, entry.getMood().getId());
    assertTrue(entry.getBody().contains("<b>beach</b>"), entry.getBody());

    final Calendar when = new GregorianCalendar();
    when.setTime(entry.getDate());
    assertEquals(2024, when.get(Calendar.YEAR));
    assertEquals(Calendar.MARCH, when.get(Calendar.MONTH));
    assertEquals(10, when.get(Calendar.DAY_OF_MONTH));
    assertEquals(9, when.get(Calendar.HOUR_OF_DAY));
    assertEquals(30, when.get(Calendar.MINUTE));

    // the confirmation page is styled like the journal and knows who is logged in
    assertTrue(html.contains("<title>Bob's Blog</title>"), html);
    assertTrue(html.contains("href=\"/styles/3.css\""), html);
    assertTrue(html.contains("You are logged in as"), html);
    assertTrue(html.contains("href=\"/logout.jsp\""), html);
    assertTrue(html.contains("title=\"JustJournal\""), html);
    assertTrue(html.contains("<strong>Entry added</strong>"), html);
  }

  @Test
  void webForm_checkboxesTurnOnAutoFormatCommentsAndMail() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("aformat", "checked");
    request.setParameter("allow_comment", "checked");
    request.setParameter("email_comment", "checked");
    request.setParameter("body", "plain text\nwith <b>tags</b> left alone");

    post(request);

    final Entry entry = saved();
    assertEquals(FormatType.TEXT, entry.getFormat());
    assertEquals(PrefBool.Y, entry.getAutoFormat());
    assertEquals(PrefBool.Y, entry.getAllowComments());
    assertEquals(PrefBool.Y, entry.getEmailComments());
    assertEquals("plain text\nwith <b>tags</b> left alone", entry.getBody());
  }

  @Test
  void webForm_privateEntry() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("security", "0");

    post(request);

    assertEquals(Security.PRIVATE, saved().getSecurity());
    verify(bingService, never()).indexNow(any(String.class));
  }

  @Test
  void subjectComesFromATitleTagWhenTheFieldIsEmpty() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.removeParameter("subject");
    request.setParameter("body", "<title>Hello there</title><p>body text</p>");

    post(request);

    final Entry entry = saved();
    assertEquals("Hello there", entry.getSubject());
    assertFalse(entry.getBody().contains("<title>"), entry.getBody());
    assertTrue(entry.getBody().contains("body text"), entry.getBody());
  }

  @Test
  void missingDate_defaultsToNow() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.removeParameter("date");
    request.removeParameter("time");
    final long before = System.currentTimeMillis();

    post(request);

    final long when = saved().getDate().getTime();
    // the stored time only keeps minutes, so allow up to a minute of slack
    assertTrue(when >= before - 61_000 && when <= System.currentTimeMillis() + 1_000);
  }

  @Test
  void unparseableDate_webForm_showsInputError() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("date", "yesterday");

    final String html = post(request);

    assertTrue(html.contains("<title>Input Error</title>"), html);
    assertTrue(html.contains("Date Parse failure"), html);
  }

  @Test
  void unparseableDate_dashboard_getsTheFailureToken() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("client", "dash");
    request.setParameter("date", "yesterday");

    final String out = post(request);

    assertTrue(out.contains("JJ.JOURNAL.UPDATE.FAIL"), out);
  }

  @Test
  void unknownSecurityLevel_webForm_showsInputError() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("security", "9");

    final String html = post(request);

    assertTrue(html.contains("<title>Input Error</title>"), html);
    verify(entryRepository, never()).save(any());
  }

  @Test
  void unknownSecurityLevel_dashboard_getsTheFailureToken() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("client", "dash");
    request.setParameter("security", "9");

    final String out = post(request);

    assertEquals("JJ.JOURNAL.UPDATE.FAIL", out.trim());
    verify(entryRepository, never()).save(any());
  }

  // ---- other clients ------------------------------------------------------------------------

  @Test
  void mobileClient_postsTextAndGetsTheMobilePage() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("client", "mobile");

    final String html = post(request);

    final Entry entry = saved();
    assertEquals(FormatType.TEXT, entry.getFormat());
    assertEquals(PrefBool.Y, entry.getAutoFormat());
    assertEquals(PrefBool.Y, entry.getAllowComments());
    assertEquals(PrefBool.Y, entry.getEmailComments());
    assertTrue(response.getContentType().startsWith("application/xhtml+xml"), response.getContentType());
    assertEquals("utf-8", response.getCharacterEncoding().toLowerCase());
    assertTrue(html.contains("<h2>Entry Posted</h2>"), html);
    assertTrue(html.contains("./mob/update.jsp"), html);
  }

  @Test
  void dashboardClient_getsTheSuccessToken() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("client", "dash");

    final String out = post(request);

    assertEquals("JJ.JOURNAL.UPDATE.OK", out.trim());
    assertEquals("text/plain", response.getContentType());
    assertEquals(FormatType.TEXT, saved().getFormat());
  }

  @Test
  void desktopClient_isRecognisedByUserAgent() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.removeHeader("User-Agent");
    request.addHeader("User-Agent", "JustJournal/2.0 (Mac)");

    final String out = post(request);

    assertEquals("JJ.JOURNAL.UPDATE.OK", out.trim());
    assertEquals("text/plain", response.getContentType());
    // the desktop client sends real HTML, which is tidied rather than auto-formatted
    assertEquals(FormatType.HTML, saved().getFormat());
  }

  @Test
  void webForm_browserThatPrefersXhtml_getsXhtml() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.removeHeader("Accept");
    request.addHeader("Accept", "application/xhtml+xml,text/html;q=0.9");

    post(request);

    assertTrue(response.getContentType().startsWith("application/xhtml+xml"), response.getContentType());
  }

  // ---- spell check --------------------------------------------------------------------------

  @Test
  void spellCheck_parksTheEntryInTheSessionAndGoesBackToTheForm() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setParameter("spellcheck", "checked");
    request.setParameter("tags", "beach");

    post(request);

    assertEquals("/update.jsp", response.getRedirectedUrl());
    final var session = request.getSession();
    assertEquals("true", session.getAttribute("spell.check"));
    assertEquals("A day out", session.getAttribute("spell.subject"));
    assertEquals(Security.PUBLIC, session.getAttribute("spell.security"));
    assertEquals("beach", session.getAttribute("spell.tags"));
    assertTrue(((String) session.getAttribute("spell.body")).contains("beach"));
    verify(entryRepository, never()).save(any());
  }

  @Test
  void normalPost_clearsAnyEarlierSpellCheck() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.getSession().setAttribute("spell.check", "true");
    request.getSession().setAttribute("spell.body", "old");

    post(request);

    assertEquals("", request.getSession().getAttribute("spell.check"));
    assertEquals("", request.getSession().getAttribute("spell.body"));
  }

  @Test
  void servletDescribesItself() {
    assertEquals("add a journal entry", servlet.getServletInfo());
  }

  @Test
  void getWorksLikePost() throws Exception {
    final MockHttpServletRequest request = loggedIn(form());
    request.setMethod("GET");
    request.setParameter("client", "dash");

    assertEquals("JJ.JOURNAL.UPDATE.OK", post(request).trim());
  }
}
