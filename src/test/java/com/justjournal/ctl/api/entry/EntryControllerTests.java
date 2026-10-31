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
package com.justjournal.ctl.api.entry;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Comment;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.Journal;
import com.justjournal.model.Location;
import com.justjournal.model.RecentEntry;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.model.api.EntryTo;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.LocationRepository;
import com.justjournal.repository.MoodRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.services.EntryService;
import com.justjournal.services.TrackbackService;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Verifies entry bodies are cleaned before they are saved through the REST API. */
@ExtendWith(MockitoExtension.class)
class EntryControllerTests {

  private static final String BROKEN_HTML = "<p>hello <textarea>oops";

  @Mock private CommentRepository commentRepository;
  @Mock private EntryRepository entryRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private MoodRepository moodRepository;
  @Mock private UserRepository userRepository;
  @Mock private EntryService entryService;
  @Mock private TrackbackService trackbackService;
  @Mock private Settings settings;
  @Mock private RecentBlogsRepository recentBlogsRepository;

  @InjectMocks private EntryController entryController;

  private MockHttpSession session;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRNAME, "laffer1");
    session.setAttribute(LOGIN_ATTRID, 1);
    response = new MockHttpServletResponse();

    final User user = new User();
    user.setId(1);
    lenient().when(userRepository.findById(1)).thenReturn(Optional.of(user));
    lenient().when(locationRepository.findById(anyInt())).thenReturn(Optional.of(new Location()));
    lenient().when(recentBlogsRepository.delete()).thenReturn(Mono.just(true));
  }

  private EntryTo entryTo(final String format) {
    return EntryTo.builder()
        .subject("test")
        .body(BROKEN_HTML)
        .format(format)
        .security(Security.PRIVATE)
        .build();
  }

  private Entry postAndCapture(final String format) {
    when(entryRepository.saveAndFlush(any(Entry.class)))
        .thenAnswer(
            inv -> {
              final Entry e = inv.getArgument(0);
              e.setId(42);
              return e;
            });

    entryController.post(entryTo(format), session, response, new ExtendedModelMap());

    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  @Test
  void postCleansHtmlBody() {
    final Entry saved = postAndCapture("HTML");

    assertEquals(200, response.getStatus());
    assertEquals(FormatType.HTML, saved.getFormat());
    assertTrue(saved.getBody().contains("</textarea>"), saved.getBody());
  }

  @Test
  void postLeavesTextBodyAlone() {
    final Entry saved = postAndCapture("TEXT");

    assertEquals(FormatType.TEXT, saved.getFormat());
    assertEquals(BROKEN_HTML, saved.getBody());
  }

  @Test
  void postLeavesMarkdownBodyAlone() {
    final Entry saved = postAndCapture("MARKDOWN");

    assertEquals(FormatType.MARKDOWN, saved.getFormat());
    assertFalse(saved.getBody().contains("</textarea>"), saved.getBody());
  }

  @Test
  void putCleansHtmlBody() {
    when(entryRepository.findById(0)).thenReturn(Optional.empty());
    when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

    entryController.put(entryTo("HTML"), session, response);

    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    assertTrue(captor.getValue().getBody().contains("</textarea>"), captor.getValue().getBody());
  }

  @Test
  void postWithNullMusicSavesEmptyMusic() {
    when(entryRepository.saveAndFlush(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));
    final EntryTo to = entryTo("TEXT");
    to.setMusic(null);

    entryController.post(to, session, response, new ExtendedModelMap());

    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).saveAndFlush(captor.capture());
    assertEquals("", captor.getValue().getMusic());
  }

  @Test
  void pagedEntriesUseRequestedPageNewestFirst() throws Exception {
    final Entry entry = new Entry();
    entry.setId(7);
    entry.setSubject("hi");
    final Pageable expected = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "date"));
    when(entryService.getPublicEntries(eq("someone"), eq(expected)))
        .thenReturn(new PageImpl<>(java.util.List.of(entry), expected, 11));

    final PagedModel<EntryTo> result =
        entryController.getEntries(
            "someone", 5, 2, new MockHttpServletRequest("GET", "/api/entry/someone/size/5/page/2"), response, session);

    assertEquals(1, result.getContent().size());
    assertEquals(7, result.getContent().iterator().next().getEntryId());
    assertEquals(5, result.getMetadata().getSize());
    assertEquals(2, result.getMetadata().getNumber());
    assertEquals(11, result.getMetadata().getTotalElements());
    assertEquals(3, result.getMetadata().getTotalPages());
  }

  @Test
  void pagedEntriesForOwnerIncludeAllEntries() throws Exception {
    final Pageable expected = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "date"));
    when(entryService.getEntries(eq("laffer1"), eq(expected)))
        .thenReturn(new PageImpl<>(java.util.List.of(), expected, 0));

    entryController.getEntries("laffer1", 20, 0, new MockHttpServletRequest("GET", "/api/entry/someone/size/5/page/2"), response, session);

    verify(entryService, never()).getPublicEntries(any(), any());
  }

  @Test
  void pagedEntriesRejectBadPaging() throws Exception {
    for (final int[] sp : new int[][] {{0, 0}, {-1, 0}, {101, 0}, {20, -1}}) {
      response = new MockHttpServletResponse();
      entryController.getEntries(
          "laffer1", sp[0], sp[1], new MockHttpServletRequest("GET", "/api/entry/someone/size/5/page/2"), response, session);
      assertEquals(400, response.getStatus(), "size=" + sp[0] + " page=" + sp[1]);
    }
    verify(entryService, never()).getEntries(any(), any());
  }

  // ---- the rest of the API ---------------------------------------------------------------------

  private static User owner() {
    final User user = new User();
    user.setId(1);
    user.setUsername("laffer1");
    final Journal journal = new Journal();
    journal.setName("Luke's Journal");
    journal.setUser(user);
    user.getJournals().add(journal);
    return user;
  }

  private static Entry entryOf(final User user, final Security security) {
    final Entry entry = new Entry();
    entry.setId(7);
    entry.setUser(user);
    entry.setSecurity(security);
    entry.setSubject("hi");
    entry.setBody("body");
    return entry;
  }

  private static MockHttpServletRequest request() {
    return new MockHttpServletRequest("GET", "/api/entry/someone/page/1");
  }

  @Test
  void recentEntries_ownerSeesEverything_othersOnlyPublic() throws Exception {
    final RecentEntry recent = new RecentEntry();
    recent.setId(3);
    final Flux<RecentEntry> mine = Flux.just(recent);
    final Flux<RecentEntry> theirs = Flux.empty();
    when(entryService.getRecentEntries("laffer1")).thenReturn(mine);
    when(entryService.getRecentEntriesPublic("someone")).thenReturn(theirs);

    assertSame(mine, entryController.getRecentEntries("laffer1", session).getBody());
    assertSame(theirs, entryController.getRecentEntries("someone", session).getBody());
    assertSame(theirs, entryController.getRecentEntries("someone", new MockHttpSession()).getBody());
  }

  @Test
  void recentEntries_badNameOrServiceFailure() throws Exception {
    assertThrows(NotFoundException.class, () -> entryController.getRecentEntries("no!", session));

    when(entryService.getRecentEntriesPublic("someone")).thenThrow(new ServiceException("boom"));
    assertEquals(
        HttpStatus.INTERNAL_SERVER_ERROR,
        entryController.getRecentEntries("someone", session).getStatusCode());
  }

  @Test
  void pagedEntries_defaultToTwentyPerPage() throws Exception {
    final Pageable expected = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "date"));
    when(entryService.getPublicEntries(eq("someone"), eq(expected)))
        .thenReturn(new PageImpl<>(java.util.List.of(), expected, 0));

    final PagedModel<EntryTo> result = entryController.getEntries("someone", 1, request(), response, session);

    assertEquals(20, result.getMetadata().getSize());
    assertEquals(1, result.getMetadata().getNumber());
    assertEquals("/api/entry/someone/page/1", result.getLinks().toList().get(0).getHref());
  }

  @Test
  void pagedEntries_missingUserAndServiceFailure() throws Exception {
    when(entryService.getPublicEntries(eq("someone"), any(Pageable.class))).thenReturn(null);
    assertNull(entryController.getEntries("someone", 1, request(), response, session));
    assertEquals(404, response.getStatus());

    response = new MockHttpServletResponse();
    when(entryService.getPublicEntries(eq("someone"), any(Pageable.class)))
        .thenThrow(new ServiceException("boom"));
    assertNull(entryController.getEntries("someone", 1, request(), response, session));
    assertEquals(500, response.getStatus());

    assertThrows(
        NotFoundException.class, () -> entryController.getEntries("no!", 1, request(), response, session));
    assertThrows(
        NotFoundException.class,
        () -> entryController.getEntries("no!", 5, 1, request(), response, session));
  }

  @Test
  void getById_publicEntriesAreVisibleToAnyone() {
    when(entryRepository.findById(7)).thenReturn(Optional.of(entryOf(owner(), Security.PUBLIC)));

    final EntryTo result = entryController.getById("LAFFER1", 7, response, new MockHttpSession());

    assertEquals(7, result.getEntryId());
    assertEquals("laffer1", result.getUser());
    assertEquals(200, response.getStatus());
  }

  @Test
  void getById_privateEntriesOnlyForTheOwner() {
    when(entryRepository.findById(7)).thenReturn(Optional.of(entryOf(owner(), Security.PRIVATE)));

    assertNull(entryController.getById("laffer1", 7, response, new MockHttpSession()));
    assertEquals(403, response.getStatus());

    response = new MockHttpServletResponse();
    final MockHttpSession other = new MockHttpSession();
    other.setAttribute(LOGIN_ATTRNAME, "someone");
    other.setAttribute(LOGIN_ATTRID, 2);
    assertNull(entryController.getById("laffer1", 7, response, other));
    assertEquals(403, response.getStatus());

    response = new MockHttpServletResponse();
    assertEquals(7, entryController.getById("laffer1", 7, response, session).getEntryId());
    assertEquals(200, response.getStatus());
  }

  @Test
  void getById_wrongUserMissingEntryOrFailure() {
    when(entryRepository.findById(7)).thenReturn(Optional.of(entryOf(owner(), Security.PUBLIC)));
    assertNull(entryController.getById("someone", 7, response, session));
    assertEquals(404, response.getStatus());

    response = new MockHttpServletResponse();
    when(entryRepository.findById(8)).thenReturn(Optional.empty());
    assertNull(entryController.getById("laffer1", 8, response, session));
    assertEquals(404, response.getStatus());

    response = new MockHttpServletResponse();
    when(entryRepository.findById(9)).thenThrow(new IllegalStateException("db"));
    assertNull(entryController.getById("laffer1", 9, response, session));
    assertEquals(500, response.getStatus());
  }

  @Test
  void publicEntriesByPath_listOrNotFound() throws Exception {
    when(entryService.getPublicEntries("laffer1"))
        .thenReturn(java.util.List.of(entryOf(owner(), Security.PUBLIC)));
    final Collection<EntryTo> found = entryController.getEntries("laffer1", response);
    assertEquals(1, found.size());
    assertEquals(7, found.iterator().next().getEntryId());

    response = new MockHttpServletResponse();
    when(entryService.getPublicEntries("empty")).thenReturn(java.util.List.of());
    assertTrue(entryController.getEntries("empty", response).isEmpty());
    assertEquals(404, response.getStatus());

    when(entryService.getPublicEntries("broken")).thenThrow(new ServiceException("boom"));
    assertNull(entryController.getEntries("broken", new MockHttpServletResponse()));

    assertThrows(NotFoundException.class, () -> entryController.getEntries("no!", response));
  }

  @Test
  void publicEntriesByParameter_listOrNotFound() throws Exception {
    when(entryService.getPublicEntries("laffer1"))
        .thenReturn(java.util.List.of(entryOf(owner(), Security.PUBLIC)));
    assertEquals(1, entryController.getEntriesByUsername("laffer1", response).size());
    assertEquals(200, response.getStatus());

    response = new MockHttpServletResponse();
    when(entryService.getPublicEntries("empty")).thenReturn(java.util.List.of());
    assertTrue(entryController.getEntriesByUsername("empty", response).isEmpty());
    assertEquals(404, response.getStatus());

    response = new MockHttpServletResponse();
    when(entryService.getPublicEntries("broken")).thenThrow(new ServiceException("boom"));
    assertTrue(entryController.getEntriesByUsername("broken", response).isEmpty());
    assertEquals(200, response.getStatus());

    assertThrows(NotFoundException.class, () -> entryController.getEntriesByUsername("no!", response));
  }

  @Test
  void post_requiresALoginAndAKnownUser() {
    Map<String, String> result =
        entryController.post(entryTo("TEXT"), new MockHttpSession(), response, new ExtendedModelMap());
    assertEquals(403, response.getStatus());
    assertTrue(result.containsKey("error"));

    response = new MockHttpServletResponse();
    final MockHttpSession unknown = new MockHttpSession();
    unknown.setAttribute(LOGIN_ATTRNAME, "ghost");
    unknown.setAttribute(LOGIN_ATTRID, 99);
    when(userRepository.findById(99)).thenReturn(Optional.empty());
    result = entryController.post(entryTo("TEXT"), unknown, response, new ExtendedModelMap());
    assertEquals(400, response.getStatus());
    assertEquals("User not found", result.get("error"));
    verify(entryRepository, never()).saveAndFlush(any());
  }

  @Test
  void post_needsABody_andReportsAFailedSave() {
    final EntryTo blank = entryTo("TEXT");
    blank.setBody("   ");
    Map<String, String> result = entryController.post(blank, session, response, new ExtendedModelMap());
    assertEquals(400, response.getStatus());
    assertEquals("Entry does not contain a body.", result.get("error"));

    response = new MockHttpServletResponse();
    when(entryRepository.saveAndFlush(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));
    result = entryController.post(entryTo("TEXT"), session, response, new ExtendedModelMap());
    assertEquals(500, response.getStatus());
    assertEquals("Could not save entry", result.get("error"));
    verify(entryService, never()).applyTags(any(), any());
  }

  @Test
  void post_publicEntry_appliesTagsAndPingsTrackbacks() {
    final User user = owner();
    when(userRepository.findById(1)).thenReturn(Optional.of(user));
    when(entryRepository.saveAndFlush(any(Entry.class)))
        .thenAnswer(
            inv -> {
              final Entry e = inv.getArgument(0);
              e.setId(42);
              return e;
            });
    final EntryTo entryTo =
        EntryTo.builder()
            .subject("links")
            .body("see https://a.example.com/x and https://b.example.com/y now")
            .format("TEXT")
            .security(Security.PUBLIC)
            .trackback("https://a.example.com/x")
            .tags(Set.of("java"))
            .build();
    final ExtendedModelMap model = new ExtendedModelMap();

    final Map<String, String> result = entryController.post(entryTo, session, response, model);

    assertEquals("ok", result.get("status"));
    assertEquals("42", result.get("id"));
    assertEquals(42, model.getAttribute("id"));
    verify(entryService).applyTags(any(Entry.class), eq(Set.of("java")));
    verify(trackbackService)
        .sendForBlog("https://a.example.com/x", 42, "laffer1", "Luke's Journal", "links", entryTo.getBody());
    verify(trackbackService)
        .sendForBlog("https://b.example.com/y", 42, "laffer1", "Luke's Journal", "links", entryTo.getBody());
    verify(recentBlogsRepository).delete();
  }

  @Test
  void post_trackbackFailuresDoNotStopTheSave() {
    when(userRepository.findById(1)).thenReturn(Optional.of(owner()));
    when(entryRepository.saveAndFlush(any(Entry.class)))
        .thenAnswer(
            inv -> {
              final Entry e = inv.getArgument(0);
              e.setId(42);
              return e;
            });
    doThrow(new IllegalStateException("down"))
        .when(trackbackService)
        .sendForBlog(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    final EntryTo entryTo =
        EntryTo.builder()
            .subject("links")
            .body("see https://a.example.com/x now")
            .format("TEXT")
            .security(Security.PUBLIC)
            .trackback("https://t.example.com/ping")
            .build();

    final Map<String, String> result = entryController.post(entryTo, session, response, new ExtendedModelMap());

    assertEquals("ok", result.get("status"));
    verify(trackbackService, times(2))
        .sendForBlog(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void put_requiresALoginAndAKnownUser_andABody() {
    Map<String, String> result = entryController.put(entryTo("TEXT"), new MockHttpSession(), response);
    assertEquals(403, response.getStatus());
    assertTrue(result.containsKey("error"));

    response = new MockHttpServletResponse();
    final MockHttpSession unknown = new MockHttpSession();
    unknown.setAttribute(LOGIN_ATTRNAME, "ghost");
    unknown.setAttribute(LOGIN_ATTRID, 99);
    when(userRepository.findById(99)).thenReturn(Optional.empty());
    result = entryController.put(entryTo("TEXT"), unknown, response);
    assertEquals(500, response.getStatus());
    assertEquals("Invalid user or session.", result.get("error"));

    response = new MockHttpServletResponse();
    final EntryTo blank = entryTo("TEXT");
    blank.setBody(" ");
    result = entryController.put(blank, session, response);
    assertEquals(400, response.getStatus());
    assertEquals("Entry does not contain a body.", result.get("error"));
    verify(entryRepository, never()).save(any());
  }

  @Test
  void put_updatesTheOwnersEntry_andChangesTheTrackback() {
    final User user = owner();
    when(userRepository.findById(1)).thenReturn(Optional.of(user));
    final Entry existing = entryOf(user, Security.PRIVATE);
    existing.setTrackback("https://old.example.com/");
    when(entryRepository.findById(7)).thenReturn(Optional.of(existing));
    when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));
    final EntryTo entryTo = entryTo("TEXT");
    entryTo.setEntryId(7);
    entryTo.setBody("new body");
    entryTo.setTrackback("https://new.example.com/");

    final Map<String, String> result = entryController.put(entryTo, session, response);

    assertEquals(Map.of("id", "7"), result);
    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    assertEquals(7, captor.getValue().getId());
    assertEquals("new body", captor.getValue().getBody());
    assertEquals("https://new.example.com/", captor.getValue().getTrackback());
    verify(entryService).applyTags(eq(captor.getValue()), any());
    verify(trackbackService, never())
        .sendForBlog(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    verify(recentBlogsRepository).delete();
  }

  @Test
  void put_doesNotTakeOverSomeoneElsesEntry() {
    final User user = owner();
    when(userRepository.findById(1)).thenReturn(Optional.of(user));
    final User other = new User();
    other.setId(2);
    final Entry theirs = entryOf(other, Security.PRIVATE);
    theirs.setTrackback("https://same.example.com/");
    when(entryRepository.findById(7)).thenReturn(Optional.of(theirs));
    when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));
    final EntryTo entryTo = entryTo("TEXT");
    entryTo.setEntryId(7);
    entryTo.setTrackback("https://same.example.com/");

    final Map<String, String> result = entryController.put(entryTo, session, response);

    assertEquals(Map.of("id", "0"), result);
    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    assertEquals(0, captor.getValue().getId());
    assertEquals("https://same.example.com/", captor.getValue().getTrackback());
  }

  @Test
  void put_newPublicEntry_pingsTheTrackback() {
    final User user = owner();
    when(userRepository.findById(1)).thenReturn(Optional.of(user));
    when(entryRepository.findById(0)).thenReturn(Optional.empty());
    when(entryRepository.save(any(Entry.class)))
        .thenAnswer(
            inv -> {
              final Entry e = inv.getArgument(0);
              e.setId(50);
              return e;
            });
    final EntryTo entryTo =
        EntryTo.builder()
            .subject("s")
            .body("plain")
            .format("TEXT")
            .security(Security.PUBLIC)
            .trackback("https://t.example.com/ping")
            .build();

    final Map<String, String> result = entryController.put(entryTo, session, response);

    assertEquals(Map.of("id", "50"), result);
    verify(trackbackService)
        .sendForBlog("https://t.example.com/ping", 50, "laffer1", "Luke's Journal", "s", "plain");
  }

  @Test
  void delete_requiresALoginAndAValidId() {
    Map<String, String> result = entryController.delete(7, new MockHttpSession(), response);
    assertEquals(403, response.getStatus());
    assertTrue(result.containsKey("error"));

    response = new MockHttpServletResponse();
    result = entryController.delete(0, session, response);
    assertEquals(404, response.getStatus());
    assertEquals("The entry id was invalid.", result.get("error"));
    verify(entryRepository, never()).deleteById(anyInt());
  }

  @Test
  void delete_removesTheOwnersEntryWithItsComments() {
    final User user = owner();
    when(userRepository.findById(1)).thenReturn(Optional.of(user));
    final Entry entry = entryOf(user, Security.PUBLIC);
    final Comment comment = new Comment();
    entry.getComments().add(comment);
    when(entryRepository.findById(7)).thenReturn(Optional.of(entry));

    final Map<String, String> result = entryController.delete(7, session, response);

    assertEquals(Map.of("id", "7"), result);
    verify(commentRepository).deleteAll(entry.getComments());
    verify(entryRepository).deleteById(7);
    verify(recentBlogsRepository).delete();
  }

  @Test
  void delete_someoneElsesOrMissingEntry_isABadRequest() {
    final User other = new User();
    other.setId(2);
    when(entryRepository.findById(7)).thenReturn(Optional.of(entryOf(other, Security.PUBLIC)));
    Map<String, String> result = entryController.delete(7, session, response);
    assertEquals(400, response.getStatus());
    assertEquals("Could not delete entry.", result.get("error"));

    response = new MockHttpServletResponse();
    when(entryRepository.findById(8)).thenReturn(Optional.empty());
    result = entryController.delete(8, session, response);
    assertEquals(400, response.getStatus());
    assertEquals("Could not delete entry.", result.get("error"));
    verify(entryRepository, never()).deleteById(anyInt());
  }

  @Test
  void delete_repositoryFailure_isABadRequest() {
    when(entryRepository.findById(7)).thenThrow(new IllegalStateException("db"));

    final Map<String, String> result = entryController.delete(7, session, response);

    assertEquals(400, response.getStatus());
    assertEquals("Could not delete the comment.", result.get("error"));
  }
}
