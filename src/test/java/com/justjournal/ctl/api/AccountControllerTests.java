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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Journal;
import com.justjournal.model.User;
import com.justjournal.model.api.PasswordChange;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.FavoriteRepository;
import com.justjournal.repository.FriendsRepository;
import com.justjournal.repository.RssSubscriptionsRepository;
import com.justjournal.repository.UserBioRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserLinkRepository;
import com.justjournal.repository.UserLocationRepository;
import com.justjournal.repository.UserPrefRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.AlbumImageService;
import com.justjournal.services.ImageStorageService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
class AccountControllerTests {

  @Mock private UserRepository userRepository;
  @Mock private Login webLogin;
  @Mock private CommentRepository commentRepository;
  @Mock private EntryRepository entryRepository;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private FriendsRepository friendsRepository;
  @Mock private UserBioRepository userBioRepository;
  @Mock private UserContactRepository userContactRepository;
  @Mock private UserLinkRepository userLinkRepository;
  @Mock private UserLocationRepository userLocationRepository;
  @Mock private UserPrefRepository userPrefRepository;
  @Mock private RssSubscriptionsRepository rssSubscriptionsRepository;
  @Mock private AlbumImageService albumImageService;
  @Mock private FavoriteRepository favoriteRepository;
  @Mock private ImageStorageService imageStorageService;

  @InjectMocks private AccountController controller;

  private MockHttpSession session;
  private MockHttpServletResponse response;
  private User user;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    response = new MockHttpServletResponse();
    user = new User();
    user.setId(7);
    user.setUsername("bob");
  }

  private void login() {
    session.setAttribute(LOGIN_ATTRID, user.getId());
    session.setAttribute(LOGIN_ATTRNAME, user.getUsername());
  }

  private static PasswordChange change(final String current, final String fresh) {
    final PasswordChange change = new PasswordChange();
    change.setPassCurrent(current);
    change.setPassNew(fresh);
    return change;
  }

  /** An unknown username is a plain 404, not an exception. */
  @Test
  void unknownUserIsNotFound() {
    final AccountController controller =
        new AccountController(
            mock(Login.class),
            userRepository,
            mock(CommentRepository.class),
            mock(EntryRepository.class),
            mock(JdbcTemplate.class),
            mock(FriendsRepository.class),
            mock(UserBioRepository.class),
            mock(UserContactRepository.class),
            mock(UserLinkRepository.class),
            mock(UserLocationRepository.class),
            mock(UserPrefRepository.class),
            mock(RssSubscriptionsRepository.class),
            mock(AlbumImageService.class),
            mock(FavoriteRepository.class),
            mock(ImageStorageService.class));
    when(userRepository.findByUsername("nobody")).thenReturn(null);
    final MockHttpServletResponse response = new MockHttpServletResponse();

    assertNull(controller.getByUsername("nobody", new MockHttpSession(), response));
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }
  // ---- looking up an account ----------------------------------------------------------------

  @Test
  void ownerOnlyJournal_hidesTheAccountFromOthers() {
    user.getJournals().add(Journal.builder().ownerViewOnly(true).build());
    when(userRepository.findByUsername("bob")).thenReturn(user);

    assertNull(controller.getByUsername("bob", session, response));
    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());

    session.setAttribute(LOGIN_ATTRID, 2);
    session.setAttribute(LOGIN_ATTRNAME, "alice");
    response = new MockHttpServletResponse();
    assertNull(controller.getByUsername("bob", session, response));
    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
  }

  @Test
  void ownerOnlyJournal_isStillVisibleToItsOwner() {
    user.getJournals().add(Journal.builder().ownerViewOnly(true).build());
    when(userRepository.findByUsername("bob")).thenReturn(user);
    login();

    assertEquals(user, controller.getByUsername("bob", session, response));
    assertEquals(200, response.getStatus());
  }

  @Test
  void publicAccount_isVisibleToAnyone() {
    user.getJournals().add(Journal.builder().ownerViewOnly(false).build());
    when(userRepository.findByUsername("bob")).thenReturn(user);

    assertEquals(user, controller.getByUsername("bob", session, response));
  }

  @Test
  void lookupFailure_readsAsNotFound() {
    when(userRepository.findByUsername("bob")).thenThrow(new IllegalStateException("db"));

    assertNull(controller.getByUsername("bob", session, response));
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }

  // ---- changing the password ----------------------------------------------------------------

  @Test
  void passwordChange_requiresALogin_andCountsAgainstTheCaller() {
    final Map<String, String> result = controller.post(change("oldpass1", "N3w_password"), session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(webLogin).blockIp(5);
    verify(webLogin, never()).changePass(anyString(), anyString(), anyString());
  }

  @Test
  void passwordChange_checksBothPasswords() {
    login();

    assertEquals(
        "The current password is invalid.",
        controller.post(change("abc", "N3w_password"), session, response).get("error"));
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());

    response = new MockHttpServletResponse();
    assertEquals(
        Login.NEW_PASSWORD_RULE,
        controller.post(change("oldpass1", "short"), session, response).get("error"));
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    verify(webLogin, never()).changePass(anyString(), anyString(), anyString());
  }

  @Test
  void passwordChange_withTheWrongCurrentPassword_isRefused() {
    login();
    when(webLogin.changePass("bob", "oldpass1", "N3w_password")).thenReturn(false);

    final Map<String, String> result = controller.post(change("oldpass1", "N3w_password"), session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertTrue(result.get("error").startsWith("Error changing password."), result.toString());
  }

  @Test
  void passwordChange_succeeds() {
    login();
    when(webLogin.changePass("bob", "oldpass1", "N3w_password")).thenReturn(true);

    final Map<String, String> result = controller.post(change("oldpass1", "N3w_password"), session, response);

    assertEquals(200, response.getStatus());
    assertEquals(Map.of("status", "Password Changed."), result);
  }

  // ---- editing the account ------------------------------------------------------------------

  @Test
  void accountUpdate_requiresALogin() {
    final Map<String, String> result = controller.post(user, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(webLogin).blockIp(5);
    verify(userRepository, never()).save(any());
  }

  @Test
  void accountUpdate_onlyForTheLoggedInAccount() {
    login();
    final User other = new User();
    other.setId(8);
    other.setUsername("bob");

    final Map<String, String> result = controller.post(other, session, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertEquals("Could not edit account.", result.get("error"));
    verify(webLogin).blockIp(5);
    verify(userRepository, never()).save(any());
  }

  @Test
  void accountUpdate_savesTheLoggedInAccount() {
    login();

    final Map<String, String> result = controller.post(user, session, response);

    assertEquals(Map.of("id", "7"), result);
    verify(userRepository).save(user);
  }

  // ---- deleting the account -----------------------------------------------------------------

  @Test
  void accountDelete_requiresALogin() {
    final Map<String, String> result = controller.delete(response, session);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(webLogin).blockIp(5);
    verify(userRepository, never()).deleteById(anyInt());
  }

  @Test
  void accountDelete_removesEverythingTheAccountOwns() throws ServiceException {
    login();
    when(userRepository.findById(7)).thenReturn(Optional.of(user));
    when(commentRepository.findByUser(user)).thenReturn(List.of());
    when(entryRepository.findByUser(user)).thenReturn(List.of());
    when(favoriteRepository.findByUser(user)).thenReturn(List.of());
    when(rssSubscriptionsRepository.findByUser(user)).thenReturn(List.of());

    final Map<String, String> result = controller.delete(response, session);

    assertEquals(Map.of("status", "Account Deleted"), result);
    assertEquals(200, response.getStatus());
    verify(imageStorageService).deleteAvatar(7);
    verify(albumImageService).deleteAll(7);
    verify(userLinkRepository).deleteByUser(user);
    verify(userLocationRepository).deleteByUser(user);
    verify(jdbcTemplate).execute("DELETE FROM journal WHERE user_id=7;");
    verify(jdbcTemplate).execute("DELETE FROM user_two_factor WHERE user_id=7;");
    verify(userRepository).deleteById(7);
  }

  @Test
  void accountDelete_stopsBeforeTouchingDataIfTheAvatarCannotBeRemoved() throws ServiceException {
    login();
    when(userRepository.findById(7)).thenReturn(Optional.of(user));
    doThrow(new ServiceException("storage down")).when(imageStorageService).deleteAvatar(7);

    final Map<String, String> result = controller.delete(response, session);

    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
    assertEquals("Error deleting account", result.get("error"));
    verify(entryRepository, never()).deleteAllInBatch(any());
    verify(userRepository, never()).deleteById(anyInt());
  }

  @Test
  void accountDelete_forAMissingAccount_isAServerError() {
    login();
    when(userRepository.findById(7)).thenReturn(Optional.empty());

    final Map<String, String> result = controller.delete(response, session);

    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
    assertEquals("Error deleting account", result.get("error"));
  }
}
