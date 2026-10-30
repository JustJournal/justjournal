/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2003-2023 Lucas Holt
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

import static com.justjournal.core.Constants.JJ_LOGIN_2FA;
import static com.justjournal.core.Constants.JJ_LOGIN_FAIL;
import static com.justjournal.core.Constants.JJ_LOGIN_OK;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static com.justjournal.core.Constants.TWO_FACTOR_TRUST_COOKIE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.model.LoginResponse;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.model.User;
import com.justjournal.model.UserTwoFactor;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.TwoFactorService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginControllerTests {

  @Mock private Login webLogin;

  @Mock private UserRepository userRepository;

  @Mock private TwoFactorService twoFactorService;

  @InjectMocks private LoginController loginController;

  private MockHttpServletRequest request;

  private MockHttpSession session;

  private User user;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    request = new MockHttpServletRequest();
    request.setSession(session);

    user = new User();
    user.setId(5);
    user.setUsername("jjsite");
    when(userRepository.findById(5)).thenReturn(Optional.of(user));
    when(webLogin.validatePassword("jjsite", "secret1")).thenReturn(5);
    when(twoFactorService.find(5)).thenReturn(Optional.empty());
  }

  private com.justjournal.core.Login credentials(final String username, final String password) {
    final com.justjournal.core.Login login = new com.justjournal.core.Login();
    login.setUsername(username);
    login.setPassword(password);
    return login;
  }

  private UserTwoFactor twoFactor(final TwoFactorMethod method) {
    final UserTwoFactor twoFactor = new UserTwoFactor();
    twoFactor.setUserId(5);
    twoFactor.setMethod(method);
    when(twoFactorService.find(5)).thenReturn(Optional.of(twoFactor));
    return twoFactor;
  }

  @Test
  void passwordAloneLogsInWhenTwoFactorIsOff() {
    final ResponseEntity<LoginResponse> response =
        loginController.post(credentials("jjsite", "secret1"), request);

    assertEquals(200, response.getStatusCode().value());
    assertEquals(JJ_LOGIN_OK, response.getBody().getStatus());
    assertEquals("jjsite", response.getBody().getUsername());
    assertNull(response.getBody().getTwoFactorMethod(), "field stays absent for plain logins");
    verify(webLogin).completeLogin(request, 5, "jjsite");
    verify(twoFactorService, never()).sendLoginCode(any());
  }

  @Test
  void wrongPasswordIsRefused() {
    final ResponseEntity<LoginResponse> response =
        loginController.post(credentials("jjsite", "wrong12"), request);

    assertEquals(401, response.getStatusCode().value());
    assertEquals(JJ_LOGIN_FAIL, response.getBody().getStatus());
    verify(webLogin, never()).completeLogin(any(), anyInt(), anyString());
    assertFalse(Login.isPendingSecondFactor(session));
  }

  @Test
  void twoFactorAccountWaitsForACode() {
    twoFactor(TwoFactorMethod.EMAIL);
    when(twoFactorService.isTrustedDevice(anyInt(), any())).thenReturn(false);

    final ResponseEntity<LoginResponse> response =
        loginController.post(credentials("jjsite", "secret1"), request);

    assertEquals(200, response.getStatusCode().value());
    assertEquals(JJ_LOGIN_2FA, response.getBody().getStatus());
    assertEquals("EMAIL", response.getBody().getTwoFactorMethod());
    assertEquals("jjsite", response.getBody().getUsername());

    // the session knows who is half way in, but is not logged in
    assertTrue(Login.isPendingSecondFactor(session));
    assertEquals(5, Login.pendingLoginId(session));
    assertEquals(TwoFactorMethod.EMAIL, Login.pendingMethod(session));
    assertFalse(Login.isAuthenticated(session));
    assertNull(session.getAttribute(LOGIN_ATTRNAME));

    verify(twoFactorService).sendLoginCode(user);
    verify(webLogin, never()).completeLogin(any(), anyInt(), anyString());
  }

  @Test
  void rememberedBrowserSkipsTheCode() {
    twoFactor(TwoFactorMethod.TOTP);
    request.setCookies(new Cookie(TWO_FACTOR_TRUST_COOKIE, "token"));
    when(twoFactorService.isTrustedDevice(5, "token")).thenReturn(true);

    final ResponseEntity<LoginResponse> response =
        loginController.post(credentials("jjsite", "secret1"), request);

    assertEquals(JJ_LOGIN_OK, response.getBody().getStatus());
    verify(webLogin).completeLogin(request, 5, "jjsite");
    assertFalse(Login.isPendingSecondFactor(session));
  }

  @Test
  void newPasswordAttemptDropsAnOlderPendingLogin() {
    Login.setPendingSecondFactor(session, 9, "someone", TwoFactorMethod.TOTP);

    loginController.post(credentials("jjsite", "wrong12"), request);

    assertFalse(Login.isPendingSecondFactor(session));
  }
}
