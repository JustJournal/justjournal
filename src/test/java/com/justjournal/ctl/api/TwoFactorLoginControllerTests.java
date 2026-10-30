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
import static com.justjournal.core.Constants.JJ_LOGIN_NONE;
import static com.justjournal.core.Constants.JJ_LOGIN_OK;
import static com.justjournal.core.Constants.LOGIN_PENDING_SINCE;
import static com.justjournal.core.Constants.TWO_FACTOR_TRUST_COOKIE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.justjournal.model.api.TwoFactorRequest;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.TwoFactorService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoFactorLoginControllerTests {

  @Mock private Login webLogin;

  @Mock private UserRepository userRepository;

  @Mock private TwoFactorService twoFactorService;

  @InjectMocks private TwoFactorLoginController controller;

  private MockHttpServletRequest request;

  private MockHttpServletResponse response;

  private MockHttpSession session;

  private User user;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    request = new MockHttpServletRequest();
    request.setSession(session);
    response = new MockHttpServletResponse();

    user = new User();
    user.setId(5);
    user.setUsername("jjsite");
    when(userRepository.findById(5)).thenReturn(Optional.of(user));
  }

  private void pending(final TwoFactorMethod method) {
    Login.setPendingSecondFactor(session, 5, "jjsite", method);
  }

  private TwoFactorRequest code(final String code, final boolean remember) {
    final TwoFactorRequest body = new TwoFactorRequest();
    body.setCode(code);
    body.setRememberDevice(remember);
    return body;
  }

  @Test
  void statusWithoutAPendingLoginIsUnauthorized() {
    final ResponseEntity<LoginResponse> result = controller.status(session);

    assertEquals(401, result.getStatusCode().value());
    assertEquals(JJ_LOGIN_NONE, result.getBody().getStatus());
  }

  @Test
  void statusReportsThePendingLogin() {
    pending(TwoFactorMethod.EMAIL);

    final ResponseEntity<LoginResponse> result = controller.status(session);

    assertEquals(200, result.getStatusCode().value());
    assertEquals(JJ_LOGIN_2FA, result.getBody().getStatus());
    assertEquals("jjsite", result.getBody().getUsername());
    assertEquals("EMAIL", result.getBody().getTwoFactorMethod());
  }

  @Test
  void rightCodeFinishesTheLogin() {
    pending(TwoFactorMethod.TOTP);
    when(twoFactorService.verify(5, "123456")).thenReturn(TwoFactorService.VerifyResult.OK);

    final ResponseEntity<LoginResponse> result =
        controller.verify(code("123456", false), request, response);

    assertEquals(200, result.getStatusCode().value());
    assertEquals(JJ_LOGIN_OK, result.getBody().getStatus());
    assertEquals("jjsite", result.getBody().getUsername());
    verify(webLogin).completeLogin(request, 5, "jjsite");
    verify(twoFactorService, never()).rememberDevice(anyInt());
    assertNull(response.getCookie(TWO_FACTOR_TRUST_COOKIE));
  }

  @Test
  void rememberingTheBrowserSetsACookie() {
    pending(TwoFactorMethod.TOTP);
    when(twoFactorService.verify(5, "123456")).thenReturn(TwoFactorService.VerifyResult.OK);
    when(twoFactorService.rememberDevice(5)).thenReturn("tok");
    request.setSecure(true);

    controller.verify(code("123456", true), request, response);

    final Cookie cookie = response.getCookie(TWO_FACTOR_TRUST_COOKIE);
    assertNotNull(cookie);
    assertEquals("tok", cookie.getValue());
    assertTrue(cookie.isHttpOnly());
    assertTrue(cookie.getSecure());
    assertEquals("/", cookie.getPath());
    assertEquals((int) TwoFactorService.TRUST_TTL.toSeconds(), cookie.getMaxAge());
  }

  @Test
  void wrongCodeKeepsTheLoginPending() {
    pending(TwoFactorMethod.TOTP);
    when(twoFactorService.verify(5, "000000")).thenReturn(TwoFactorService.VerifyResult.INVALID);

    final ResponseEntity<LoginResponse> result =
        controller.verify(code("000000", false), request, response);

    assertEquals(401, result.getStatusCode().value());
    assertEquals(JJ_LOGIN_FAIL, result.getBody().getStatus());
    assertTrue(Login.isPendingSecondFactor(session), "user may try again");
    verify(webLogin, never()).completeLogin(any(), anyInt(), anyString());
  }

  @Test
  void tooManyWrongCodesEndsThePendingLogin() {
    pending(TwoFactorMethod.TOTP);
    when(twoFactorService.verify(5, "000000")).thenReturn(TwoFactorService.VerifyResult.LOCKED);

    final ResponseEntity<LoginResponse> result =
        controller.verify(code("000000", false), request, response);

    assertEquals(429, result.getStatusCode().value());
    assertFalse(Login.isPendingSecondFactor(session));
    verify(webLogin).blockIp(30);
    verify(webLogin, never()).completeLogin(any(), anyInt(), anyString());
  }

  @Test
  void verifyWithoutAPendingLoginIsUnauthorized() {
    final ResponseEntity<LoginResponse> result =
        controller.verify(code("123456", false), request, response);

    assertEquals(401, result.getStatusCode().value());
    assertEquals(JJ_LOGIN_NONE, result.getBody().getStatus());
    verify(twoFactorService, never()).verify(anyInt(), anyString());
  }

  @Test
  void stalePendingLoginIsUnauthorized() {
    pending(TwoFactorMethod.TOTP);
    session.setAttribute(
        LOGIN_PENDING_SINCE, System.currentTimeMillis() - Login.PENDING_LOGIN_TTL.toMillis() - 1000);

    final ResponseEntity<LoginResponse> result =
        controller.verify(code("123456", false), request, response);

    assertEquals(401, result.getStatusCode().value());
    verify(twoFactorService, never()).verify(anyInt(), anyString());
  }

  @Test
  void resendMailsAnotherCode() {
    pending(TwoFactorMethod.EMAIL);
    when(twoFactorService.sendLoginCode(user)).thenReturn(true);

    final Map<String, String> result = controller.resend(session, response);

    assertEquals(200, response.getStatus());
    assertEquals(TwoFactorLoginController.CODE_SENT, result.get("status"));
  }

  @Test
  void resendIsThrottled() {
    pending(TwoFactorMethod.EMAIL);
    when(twoFactorService.sendLoginCode(user)).thenReturn(false);

    final Map<String, String> result = controller.resend(session, response);

    assertEquals(429, response.getStatus());
    assertEquals(TwoFactorLoginController.CODE_NOT_SENT, result.get("error"));
  }

  @Test
  void resendOnlyAppliesToEmail() {
    pending(TwoFactorMethod.TOTP);

    controller.resend(session, response);

    assertEquals(401, response.getStatus());
    verify(twoFactorService, never()).sendLoginCode(any());
  }
}
