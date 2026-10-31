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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Journal;
import com.justjournal.model.Style;
import com.justjournal.model.User;
import com.justjournal.model.api.JournalTo;
import com.justjournal.repository.JournalRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.StyleService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Journals can be listed by anyone and created, changed and removed only by their owner. */
class JournalControllerTests {

  private static final int OWNER_ID = 1;

  private JournalRepository journalRepository;
  private UserRepository userRepository;
  private StyleService styleService;
  private JournalController controller;

  private MockHttpSession session;
  private MockHttpServletResponse response;
  private User owner;
  private Journal journal;
  private Style style;

  @BeforeEach
  void setUp() {
    journalRepository = mock(JournalRepository.class);
    userRepository = mock(UserRepository.class);
    styleService = mock(StyleService.class);
    controller = new JournalController(journalRepository, userRepository, styleService);

    session = new MockHttpSession();
    response = new MockHttpServletResponse();

    owner = new User();
    owner.setId(OWNER_ID);
    owner.setUsername("owner");
    journal = Journal.builder().id(5).slug("mine").name("My Journal").user(owner).build();
    style = new Style();
    style.setId(2);
    when(styleService.get(2)).thenReturn(style);
    when(journalRepository.saveAndFlush(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private void loginAs(final int id, final String username) {
    session.setAttribute(LOGIN_ATTRID, id);
    session.setAttribute(LOGIN_ATTRNAME, username);
  }

  private static JournalTo journalTo(final String slug) {
    return JournalTo.builder()
        .slug(slug)
        .name("Renamed")
        .styleId(2)
        .allowSpider(false)
        .ownerViewOnly(true)
        .pingServices(false)
        .build();
  }

  // ---- reading ------------------------------------------------------------------------------

  @Test
  void listByUser_returnsTheUsersJournals() {
    when(journalRepository.findByUsername("owner")).thenReturn(List.of(journal));

    final ResponseEntity<List<Journal>> result = controller.listByUser("owner");

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals(List.of(journal), result.getBody());
  }

  @Test
  void listByUser_rejectsBadUsernames_andReportsFailures() {
    assertThrows(NotFoundException.class, () -> controller.listByUser("no spaces!"));

    when(journalRepository.findByUsername("owner")).thenThrow(new IllegalStateException("db"));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.listByUser("owner").getStatusCode());
  }

  @Test
  void get_returnsTheJournalForASlug_orAFailure() {
    when(journalRepository.findOneBySlug("mine")).thenReturn(journal);
    assertEquals(journal, controller.get("mine").getBody());

    when(journalRepository.findOneBySlug("broken")).thenThrow(new IllegalStateException("db"));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.get("broken").getStatusCode());
  }

  // ---- creating and updating ----------------------------------------------------------------

  @Test
  void put_requiresALogin() {
    final Map<String, String> result = controller.put("mine", journalTo("mine"), session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(journalRepository, never()).saveAndFlush(any());
  }

  @Test
  void post_createsANewJournalForTheLoggedInUser() {
    loginAs(OWNER_ID, "owner");
    when(journalRepository.findOneBySlug("fresh")).thenReturn(null);
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

    final Map<String, String> result = controller.post(journalTo("fresh"), session, response);

    assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());
    assertEquals(Map.of("slug", "fresh"), result);
    final ArgumentCaptor<Journal> captor = ArgumentCaptor.forClass(Journal.class);
    verify(journalRepository).saveAndFlush(captor.capture());
    final Journal created = captor.getValue();
    assertEquals(0, created.getId());
    assertEquals("Renamed", created.getName());
    assertEquals(owner, created.getUser());
    assertEquals(style, created.getStyle());
    assertEquals(2, created.getStyleId());
    assertTrue(created.isOwnerViewOnly());
    assertTrue(!created.isAllowSpider());
    assertTrue(!created.isPingServices());
    assertNotNull(created.getSince());
    assertNotNull(created.getModified());
  }

  @Test
  void put_updatesTheOwnersJournal() {
    loginAs(OWNER_ID, "owner");
    when(journalRepository.findOneBySlug("mine")).thenReturn(journal);

    final Map<String, String> result = controller.put("mine", journalTo("mine"), session, response);

    assertEquals(200, response.getStatus());
    assertEquals(Map.of("slug", "mine"), result);
    verify(journalRepository).saveAndFlush(journal);
    assertEquals("Renamed", journal.getName());
    assertEquals(style, journal.getStyle());
    assertTrue(journal.isOwnerViewOnly());
    assertTrue(!journal.isAllowSpider());
    assertTrue(!journal.isPingServices());
    assertNotNull(journal.getModified());
  }

  @Test
  void put_someoneElsesJournal_isForbidden() {
    loginAs(2, "other");
    when(journalRepository.findOneBySlug("mine")).thenReturn(journal);

    final Map<String, String> result = controller.put("mine", journalTo("mine"), session, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertEquals("You do not have permission to update this journal.", result.get("error"));
    verify(journalRepository, never()).saveAndFlush(any());
  }

  @Test
  void put_needsAStyle() {
    loginAs(OWNER_ID, "owner");
    when(journalRepository.findOneBySlug("mine")).thenReturn(journal);
    final JournalTo noStyle = journalTo("mine");
    noStyle.setStyleId(0);

    final Map<String, String> result = controller.put("mine", noStyle, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Missing style id.", result.get("error"));
    verify(journalRepository, never()).saveAndFlush(any());
  }

  @Test
  void put_repositoryFailure_isABadRequest() {
    loginAs(OWNER_ID, "owner");
    when(journalRepository.findOneBySlug("mine")).thenThrow(new IllegalStateException("db"));

    final Map<String, String> result = controller.put("mine", journalTo("mine"), session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Error adding journal.", result.get("error"));
  }

  // ---- deleting -----------------------------------------------------------------------------

  @Test
  void delete_requiresALoginAndASlug() {
    assertTrue(controller.delete("mine", session, response).containsKey("error"));
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());

    loginAs(OWNER_ID, "owner");
    response = new MockHttpServletResponse();
    assertEquals(
        "Error deleting your journal. Slug is missing.",
        controller.delete("", session, response).get("error"));
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    verify(journalRepository, never()).delete(any());
  }

  @Test
  void delete_unknownSlug_isNotFound() {
    loginAs(OWNER_ID, "owner");
    when(journalRepository.findOneBySlug("gone")).thenReturn(null);

    final Map<String, String> result = controller.delete("gone", session, response);

    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    assertEquals("Error deleting your journal. Slug not found.", result.get("error"));
  }

  @Test
  void delete_someoneElsesJournal_isForbidden() {
    loginAs(2, "other");
    when(journalRepository.findOneBySlug("mine")).thenReturn(journal);

    final Map<String, String> result = controller.delete("mine", session, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertEquals("You do not have permission to delete this journal.", result.get("error"));
    verify(journalRepository, never()).delete(any());
  }

  @Test
  void delete_removesTheOwnersJournal() {
    loginAs(OWNER_ID, "owner");
    when(journalRepository.findOneBySlug("mine")).thenReturn(journal);

    final Map<String, String> result = controller.delete("mine", session, response);

    assertEquals(200, response.getStatus());
    assertEquals(Map.of("slug", "mine"), result);
    verify(journalRepository).delete(journal);
    verify(journalRepository).flush();
  }
}
