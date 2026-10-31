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
package com.justjournal;

import com.justjournal.model.PasswordType;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.UserTwoFactorRepository;
import com.justjournal.repository.cache.TrackBackIpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import reactor.core.publisher.Mono;

import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static com.justjournal.core.Constants.BAD_USER_ID;
import static com.justjournal.core.Constants.LOGIN_PENDING_ATTRNAME;
import static com.justjournal.core.Constants.LOGIN_PENDING_SINCE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** @author Lucas Holt */
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class LoginTests {

  @Mock UserRepository userRepository;

  @Mock
  TrackBackIpRepository trackBackIpRepository;

  @Mock
  RequestContextHolder requestContextHolder;

  @Mock
  UserTwoFactorRepository userTwoFactorRepository;

  @InjectMocks private Login login;

  @ParameterizedTest
  @ValueSource(strings = { "abcdefg", "ABCDEFG", "ABCDEFGkadkdk39393", "_@.!&*#$?^ " })
  void testIsPasswordValid(String password) {
    boolean result = Login.isPassword(password);
    assertTrue(result);
  }

  /** We don't allow % */
  @ParameterizedTest
  @ValueSource(strings = { "%_@.!&*#$?^ "})
  void testIsPasswordInvalid(String password) {
    boolean result = Login.isPassword(password);
    assertFalse(result);
  }

  @Test
  void testIsNewPasswordNeedsEightCharacters() {
    assertTrue(Login.isNewPassword("eight888"));
    assertFalse(Login.isNewPassword("seven77"));
    assertFalse(Login.isNewPassword("has-dash-8"));
    assertFalse(Login.isNewPassword(null));
    assertTrue(Login.isPassword("seven77"), "existing short passwords still log in");
  }

  @ParameterizedTest
  @ValueSource(strings = { "%_@.!&*#$?^ ", "foo bar"})
  void testIsUserNameInvalid(String username) {
    boolean result = Login.isUserName(username);
    assertFalse(result);
  }

  @Test
  void testIsUserName() {
    boolean result = Login.isUserName("testing");
    assertTrue(result);
  }

  @Test
  void testIsUserNameWeirdButValid() {
    boolean result = Login.isUserName("testing123_");
    assertTrue(result);
  }

  @Test
  void testSha1() throws NoSuchAlgorithmException {
    String result = login.sha1("foo");
    assertEquals("0beec7b5ea3f0fdbc95d0dd47f3c5bc275da8a33", result);
  }

  @Test
  void testSha256() throws NoSuchAlgorithmException {
    String result = login.sha256("foo");
    assertEquals("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae", result);
  }

  @Test
  void testValidateShortUser() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    when(trackBackIpRepository.saveIpAddress(anyString(), anyInt())).thenReturn(Mono.fromSupplier(() -> true));
    int result = login.validate("a", "basic");
    assertEquals(BAD_USER_ID, result);
  }

  @Test
  void testValidateShortPass() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    when(trackBackIpRepository.saveIpAddress(anyString(), anyInt())).thenReturn(Mono.fromSupplier(() -> true));
    int result = login.validate("abcdef", "a");
    assertEquals(BAD_USER_ID, result);
  }

  @Test
  void testValidateSketchyIpUser() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.fromSupplier(() -> "8.8.8.8")); // sorry google dns
    when(trackBackIpRepository.saveIpAddress(anyString(), anyInt())).thenReturn(Mono.fromSupplier(() -> true));
    int result = login.validate("abcdef", "abcdefgad");
    assertEquals(BAD_USER_ID, result);
  }

  @Test
  void testValidateBadUser() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    when(trackBackIpRepository.saveIpAddress(anyString(), anyInt())).thenReturn(Mono.fromSupplier(() -> true));
    int result = login.validate("a@b", "basic");
    assertEquals(BAD_USER_ID, result);
  }

  @Test
  void testValidateBadPass() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    when(trackBackIpRepository.saveIpAddress(anyString(), anyInt())).thenReturn(Mono.fromSupplier(() -> true));
    int result = login.validate("abb", "basic%");
    assertEquals(BAD_USER_ID, result);
  }

  @Test
  void testValidate() {
    User user = new User();
    user.setId(1);
    user.setUsername("abc");
    user.setPassword(login.getHashedPassword("abc", "basic"));
    user.setPasswordType(PasswordType.ARGON2);

    when(userRepository.findByUsername(anyString())).thenReturn(user);
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());

    int result = login.validate("abc", "basic");
    assertEquals(1, result, "Login validation should return 1 for successful login");

    verify(userRepository).findByUsername(anyString());
    verify(trackBackIpRepository).getIpAddress(anyString());

  }

  private User userWithTwoFactor() {
    User user = new User();
    user.setId(1);
    user.setUsername("abc");
    user.setPassword(login.getHashedPassword("abc", "basic"));
    user.setPasswordType(PasswordType.ARGON2);

    when(userRepository.findByUsername(anyString())).thenReturn(user);
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    return user;
  }

  /** XML-RPC and the old clients call validate(); the password alone must not get a 2FA user in. */
  @Test
  void testValidateRefusesTwoFactorUsers() {
    userWithTwoFactor();
    when(userTwoFactorRepository.existsByUserId(1)).thenReturn(true);

    int result = login.validate("abc", "basic");
    assertEquals(BAD_USER_ID, result);

    verify(userRepository, never()).findById(anyInt());
  }

  @Test
  void testValidatePasswordAcceptsTwoFactorUsers() {
    userWithTwoFactor();

    int result = login.validatePassword("abc", "basic");
    assertEquals(1, result);

    // the login isn't recorded until the second factor is done
    verify(userRepository, never()).findById(anyInt());
  }

  @Test
  void testHasSecondFactor() {
    when(userTwoFactorRepository.existsByUserId(1)).thenReturn(true);
    assertTrue(login.hasSecondFactor(1));
    assertFalse(login.hasSecondFactor(2));
    assertFalse(login.hasSecondFactor(BAD_USER_ID));
  }

  @Test
  void testChangePassWorksForTwoFactorUsers() {
    User user = userWithTwoFactor();
    when(userRepository.saveAndFlush(any(User.class))).thenReturn(user);

    assertTrue(login.changePass("abc", "basic", "newpass1"));
    assertFalse(login.changePass("abc", "basic", "seven77"), "new passwords need 8 characters");
    assertEquals(PasswordType.ARGON2, user.getPasswordType());
  }

  @Test
  void pendingSecondFactor_roundTripsThroughTheSession() {
    final MockHttpSession session = new MockHttpSession();
    assertFalse(Login.isPendingSecondFactor(session));
    assertEquals(BAD_USER_ID, Login.pendingLoginId(session));
    assertNull(Login.pendingLoginName(session));
    assertNull(Login.pendingMethod(session));

    Login.setPendingSecondFactor(session, 7, "ada", TwoFactorMethod.TOTP);
    assertTrue(Login.isPendingSecondFactor(session));
    assertEquals(7, Login.pendingLoginId(session));
    assertEquals("ada", Login.pendingLoginName(session));
    assertEquals(TwoFactorMethod.TOTP, Login.pendingMethod(session));

    session.setAttribute(
        LOGIN_PENDING_SINCE, System.currentTimeMillis() - Login.PENDING_LOGIN_TTL.toMillis() - 1000);
    assertFalse(Login.isPendingSecondFactor(session), "a pending login expires");

    Login.setPendingSecondFactor(session, 7, "ada", TwoFactorMethod.EMAIL);
    Login.clearPendingSecondFactor(session);
    assertFalse(Login.isPendingSecondFactor(session));
    assertNull(session.getAttribute(LOGIN_PENDING_ATTRNAME));
  }

  @Test
  void completeLogin_marksTheSession_andRecordsTheLogin() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpSession session = new MockHttpSession();
    request.setSession(session);
    Login.setPendingSecondFactor(session, 7, "ada", TwoFactorMethod.EMAIL);
    final User user = new User();
    user.setId(7);
    when(userRepository.findById(7)).thenReturn(Optional.of(user));

    login.completeLogin(request, 7, "ada");

    assertTrue(Login.isAuthenticated(request.getSession()));
    assertEquals(7, Login.currentLoginId(request.getSession()));
    assertEquals("ada", Login.currentLoginName(request.getSession()));
    assertFalse(Login.isPendingSecondFactor(request.getSession()));
    assertNotNull(user.getLastLogin());
    verify(userRepository).saveAndFlush(user);

    Login.logout(request.getSession());
    assertFalse(Login.isAuthenticated(request.getSession()));
    assertEquals(BAD_USER_ID, Login.currentLoginId(request.getSession()));
    assertNull(Login.currentLoginName(request.getSession()));
  }

  @Test
  void setLastLogin_ignoresBadIdsAndMissingUsers() {
    login.setLastLogin(0);
    verify(userRepository, never()).findById(anyInt());

    when(userRepository.findById(9)).thenReturn(Optional.empty());
    login.setLastLogin(9);
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void resetPass_setsAnArgon2PasswordThatThenValidates() {
    final User user = new User();
    user.setId(7);
    user.setUsername("ada");
    user.setPassword("oldhash");
    when(userRepository.findById(7)).thenReturn(Optional.of(user));

    assertTrue(login.resetPass(7, "N3w_password"));

    assertTrue(user.getPassword().startsWith("$argon2id$v=19$m=65536,t=3,p=4$"), user.getPassword());
    assertEquals(PasswordType.ARGON2, user.getPasswordType());
    verify(userRepository).saveAndFlush(user);

    when(userRepository.findByUsername("ada")).thenReturn(user);
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    assertEquals(7, login.validatePassword("ada", "N3w_password"));
  }

  @Test
  void resetPass_refusesBadInput_missingUsers_andFailures() {
    assertFalse(login.resetPass(0, "N3w_password"));
    assertFalse(login.resetPass(7, "short"));

    when(userRepository.findById(7)).thenReturn(Optional.empty());
    assertFalse(login.resetPass(7, "N3w_password"));

    when(userRepository.findById(8)).thenThrow(new IllegalStateException("db"));
    assertFalse(login.resetPass(8, "N3w_password"));
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void exists_checksTheNameAndTheRepository() {
    when(userRepository.findByUsername("ada")).thenReturn(new User());
    when(userRepository.findByUsername("nobody")).thenReturn(null);

    assertTrue(login.exists("ada"));
    assertFalse(login.exists("nobody"));
    assertFalse(login.exists("no!"));
  }

  @Test
  void validatePassword_upgradesOldHashesToArgon2() throws NoSuchAlgorithmException {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    final User sha256User = new User();
    sha256User.setId(2);
    sha256User.setUsername("bob");
    sha256User.setPassword(login.sha256("bobbasic1"));
    when(userRepository.findByUsername("bob")).thenReturn(sha256User);
    assertEquals(2, login.validatePassword("bob", "basic1"));
    assertTrue(sha256User.getPassword().startsWith("$argon2id$"), sha256User.getPassword());
    assertEquals(PasswordType.ARGON2, sha256User.getPasswordType());

    final User sha1User = new User();
    sha1User.setId(3);
    sha1User.setUsername("cat");
    sha1User.setPassword(login.sha1("basic1"));
    when(userRepository.findByUsername("cat")).thenReturn(sha1User);
    assertEquals(3, login.validatePassword("cat", "basic1"));
    assertTrue(sha1User.getPassword().startsWith("$argon2id$"), sha1User.getPassword());
  }

  @Test
  void validatePassword_wrongPasswordOrUnknownUser_blocksTheIp() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    when(trackBackIpRepository.saveIpAddress(anyString(), anyInt())).thenReturn(Mono.just(true));
    final User user = new User();
    user.setId(2);
    user.setUsername("bob");
    user.setPassword(login.getHashedPassword("bob", "basic1"));
    when(userRepository.findByUsername("bob")).thenReturn(user);
    when(userRepository.findByUsername("nobody")).thenReturn(null);

    assertEquals(BAD_USER_ID, login.validatePassword("bob", "wrong1"));
    assertEquals(BAD_USER_ID, login.validatePassword("nobody", "basic1"));

    verify(trackBackIpRepository, times(2)).saveIpAddress(anyString(), eq(5));
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  void isIpSketch_ignoresTheUnknownAddress() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.just("0.0.0.0"));

    assertFalse(login.isIpSketch());
  }

  @Test
  void changePass_refusesAWrongPassword() {
    when(trackBackIpRepository.getIpAddress(anyString())).thenReturn(Mono.empty());
    when(trackBackIpRepository.saveIpAddress(anyString(), anyInt())).thenReturn(Mono.just(true));
    final User user = new User();
    user.setId(2);
    user.setUsername("bob");
    user.setPassword(login.getHashedPassword("bob", "basic1"));
    when(userRepository.findByUsername("bob")).thenReturn(user);

    assertFalse(login.changePass("bob", "wrong1", "N3w_password"));
    verify(userRepository, never()).saveAndFlush(any());
  }
}
