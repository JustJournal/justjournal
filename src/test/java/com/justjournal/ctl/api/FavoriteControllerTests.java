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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.Entry;
import com.justjournal.model.Favorite;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.FavoriteRepository;
import com.justjournal.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Favorites belong to the logged in user. */
class FavoriteControllerTests {

  private FavoriteRepository favoriteRepository;
  private EntryRepository entryRepository;
  private UserRepository userRepository;
  private FavoriteController controller;

  private MockHttpSession session;
  private MockHttpServletResponse response;
  private User user;
  private Entry entry;

  @BeforeEach
  void setUp() {
    favoriteRepository = mock(FavoriteRepository.class);
    entryRepository = mock(EntryRepository.class);
    userRepository = mock(UserRepository.class);
    controller = new FavoriteController(favoriteRepository, entryRepository, userRepository);
    session = new MockHttpSession();
    response = new MockHttpServletResponse();

    user = new User();
    user.setId(3);
    user.setUsername("ada");
    entry = new Entry();
    entry.setId(42);
    entry.setUser(user);
    when(entryRepository.findById(42)).thenReturn(Optional.of(entry));
  }

  private void login() {
    session.setAttribute(LOGIN_ATTRID, 3);
    session.setAttribute(LOGIN_ATTRNAME, "ada");
    when(userRepository.findById(3)).thenReturn(Optional.of(user));
  }

  @Test
  void getFavorites_listsTheEntriesTheUserMarked() {
    login();
    final Favorite favorite = new Favorite();
    favorite.setUser(user);
    favorite.setEntry(entry);
    when(favoriteRepository.findByUser(user)).thenReturn(List.of(favorite));

    final Collection<Entry> result = controller.getFavorites(session, response);

    assertEquals(List.of(entry), List.copyOf(result));
    assertEquals(200, response.getStatus());
  }

  @Test
  void getFavorites_withoutALogin_isNotFound() {
    when(userRepository.findById(0)).thenReturn(Optional.empty());

    assertTrue(controller.getFavorites(session, response).isEmpty());
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }

  @Test
  void getFavorites_repositoryFailure_isABadRequest() {
    login();
    when(favoriteRepository.findByUser(user)).thenThrow(new IllegalStateException("db"));

    assertTrue(controller.getFavorites(session, response).isEmpty());
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
  }

  @Test
  void create_savesAFavoriteForTheEntry() {
    login();

    final Map<String, String> result = controller.create(42, session, response);

    assertEquals(Map.of("id", "42"), result);
    final ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
    verify(favoriteRepository).saveAndFlush(captor.capture());
    assertEquals(user, captor.getValue().getUser());
    assertEquals(entry, captor.getValue().getEntry());
  }

  @Test
  void create_repositoryFailure_isABadRequest() {
    login();
    when(favoriteRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("db"));

    final Map<String, String> result = controller.create(42, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Could not add the favorite.", result.get("error"));
  }

  @Test
  void delete_requiresALogin() {
    final Map<String, String> result = controller.delete(42, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertTrue(result.containsKey("error"));
    verify(favoriteRepository, never()).delete(any());
  }

  @Test
  void delete_removesTheFavorite() {
    login();
    final Favorite favorite = new Favorite();
    when(favoriteRepository.findByUserAndEntry(user, entry)).thenReturn(favorite);

    final Map<String, String> result = controller.delete(42, session, response);

    assertEquals(Map.of("id", "42"), result);
    verify(favoriteRepository).delete(favorite);
    verify(favoriteRepository).flush();
  }

  @Test
  void delete_repositoryFailure_isABadRequest() {
    login();
    when(favoriteRepository.findByUserAndEntry(user, entry)).thenThrow(new IllegalStateException("db"));

    final Map<String, String> result = controller.delete(42, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Could not delete the favorite.", result.get("error"));
  }
}
