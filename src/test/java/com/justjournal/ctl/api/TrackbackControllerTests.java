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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.Entry;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.model.api.TrackbackTo;
import com.justjournal.repository.EntryRepository;
import com.justjournal.services.TrackbackService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Trackbacks are listed per entry and removed only by the entry's owner. */
class TrackbackControllerTests {

  private EntryRepository entryRepository;
  private TrackbackService trackbackService;
  private TrackbackController controller;

  private MockHttpSession session;
  private MockHttpServletResponse response;
  private User owner;
  private Journal journal;
  private Entry entry;
  private TrackbackTo trackback;

  @BeforeEach
  void setUp() {
    entryRepository = mock(EntryRepository.class);
    trackbackService = mock(TrackbackService.class);
    controller = new TrackbackController(entryRepository, trackbackService);
    session = new MockHttpSession();
    response = new MockHttpServletResponse();

    owner = new User();
    owner.setId(1);
    owner.setUsername("ada");
    journal = new Journal();
    journal.setUser(owner);
    owner.getJournals().add(journal);

    entry = new Entry();
    entry.setId(10);
    entry.setUser(owner);
    entry.setSecurity(Security.PUBLIC);
    when(entryRepository.findById(10)).thenReturn(Optional.of(entry));

    trackback = new TrackbackTo();
    trackback.setId(7);
    trackback.setEntryId(10);
    when(trackbackService.getByEntry(10)).thenReturn(List.of(trackback));
  }

  private void loginAs(final int id, final String username) {
    session.setAttribute(LOGIN_ATTRID, id);
    session.setAttribute(LOGIN_ATTRNAME, username);
  }

  @Test
  void getTrackbacks_listsThoseOnAPublicEntry() {
    final List<TrackbackTo> result = controller.getTrackbacks(10, response);

    assertEquals(List.of(trackback), result);
    assertEquals(200, response.getStatus());
  }

  @Test
  void getTrackbacks_unknownEntry_isNotFoundAndEmpty() {
    when(entryRepository.findById(99)).thenReturn(Optional.empty());

    assertTrue(controller.getTrackbacks(99, response).isEmpty());
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }

  @Test
  void getTrackbacks_isForbiddenWhenTheEntryIsPrivate() {
    entry.setSecurity(Security.PRIVATE);

    controller.getTrackbacks(10, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
  }

  @Test
  void getTrackbacks_isForbiddenWhenCommentsAreOff() {
    entry.setAllowComments(PrefBool.N);

    controller.getTrackbacks(10, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
  }

  @Test
  void getTrackbacks_isForbiddenForOwnerOnlyJournals() {
    journal.setOwnerViewOnly(true);

    controller.getTrackbacks(10, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
  }

  @Test
  void getTrackbacks_withoutAJournal_isABadRequest() {
    owner.getJournals().clear();

    controller.getTrackbacks(10, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
  }

  @Test
  void delete_requiresALogin() {
    final Map<String, String> result = controller.delete(7, session, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertTrue(result.containsKey("error"));
    verify(trackbackService, never()).deleteById(anyInt());
  }

  @Test
  void delete_removesTheOwnersTrackback() {
    loginAs(1, "ada");
    when(trackbackService.getById(7)).thenReturn(Optional.of(trackback));

    final Map<String, String> result = controller.delete(7, session, response);

    assertEquals(Map.of("id", "7"), result);
    verify(trackbackService).deleteById(7);
  }

  @Test
  void delete_leavesSomeoneElsesTrackbackAlone() {
    loginAs(2, "bob");
    when(trackbackService.getById(7)).thenReturn(Optional.of(trackback));

    final Map<String, String> result = controller.delete(7, session, response);

    assertEquals(Map.of("id", "7"), result);
    verify(trackbackService, never()).deleteById(anyInt());
  }

  @Test
  void delete_unknownTrackbackOrEntry_isNotFound() {
    loginAs(1, "ada");
    when(trackbackService.getById(8)).thenReturn(Optional.empty());
    assertEquals("Trackback not found.", controller.delete(8, session, response).get("error"));
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());

    response = new MockHttpServletResponse();
    trackback.setEntryId(99);
    when(trackbackService.getById(7)).thenReturn(Optional.of(trackback));
    when(entryRepository.findById(99)).thenReturn(Optional.empty());
    assertEquals("Trackback entry not found.", controller.delete(7, session, response).get("error"));
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }

  @Test
  void delete_serviceFailure_isABadRequest() {
    loginAs(1, "ada");
    when(trackbackService.getById(7)).thenThrow(new IllegalStateException("db"));

    final Map<String, String> result = controller.delete(7, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Could not delete the trackback.", result.get("error"));
  }
}
