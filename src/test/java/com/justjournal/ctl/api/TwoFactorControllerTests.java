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

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.exception.UnauthorizedException;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.model.User;
import com.justjournal.model.api.TwoFactorRequest;
import com.justjournal.model.api.TwoFactorStatus;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.TwoFactorService;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoFactorControllerTests {

  @Mock private Login webLogin;

  @Mock private UserRepository userRepository;

  @Mock private TwoFactorService twoFactorService;

  @InjectMocks private TwoFactorController controller;

  private MockHttpSession session;

  private MockHttpServletResponse response;

  private User user;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, 5);
    session.setAttribute(LOGIN_ATTRNAME, "jjsite");
    response = new MockHttpServletResponse();

    user = new User();
    user.setId(5);
    user.setUsername("jjsite");
    when(userRepository.findById(5)).thenReturn(Optional.of(user));
    when(webLogin.validatePassword("jjsite", "secret1")).thenReturn(5);
  }

  private TwoFactorRequest body(final String code, final String password) {
    final TwoFactorRequest body = new TwoFactorRequest();
    body.setCode(code);
    body.setPassword(password);
    return body;
  }

  @Test
  void loggedOutUsersAreRefused() {
    final MockHttpSession anonymous = new MockHttpSession();

    assertThrows(UnauthorizedException.class, () -> controller.status(anonymous));
    verify(webLogin).blockIp(5);
    verify(twoFactorService, never()).status(any());
  }

  @Test
  void statusComesFromTheService() {
    final TwoFactorStatus status = new TwoFactorStatus(true, TwoFactorMethod.TOTP, null, 10, 0);
    when(twoFactorService.status(user)).thenReturn(status);

    assertEquals(status, controller.status(session));
  }

  @Test
  void rightEmailCodeTurnsItOn() {
    when(twoFactorService.confirmEmailEnrollment(user, "123456")).thenReturn(true);

    final Map<String, String> result = controller.confirmEmail(body("123456", null), session, response);

    assertEquals(200, response.getStatus());
    assertEquals(TwoFactorController.ENABLED, result.get("status"));
  }

  @Test
  void wrongEmailCodeIsABadRequest() {
    when(twoFactorService.confirmEmailEnrollment(user, "000000")).thenReturn(false);

    final Map<String, String> result = controller.confirmEmail(body("000000", null), session, response);

    assertEquals(400, response.getStatus());
    assertEquals(TwoFactorController.WRONG_CODE, result.get("error"));
  }

  @Test
  void confirmingTheAppReturnsRecoveryCodes() {
    when(twoFactorService.confirmTotpEnrollment(user, "123456"))
        .thenReturn(List.of("AAAAA-BBBBB", "CCCCC-DDDDD"));

    final Map<String, Object> result = controller.confirmTotp(body("123456", null), session, response);

    assertEquals(200, response.getStatus());
    assertEquals(TwoFactorController.ENABLED, result.get("status"));
    assertEquals(List.of("AAAAA-BBBBB", "CCCCC-DDDDD"), result.get("recoveryCodes"));
  }

  @Test
  void wrongAppCodeIsABadRequest() {
    when(twoFactorService.confirmTotpEnrollment(user, "000000")).thenReturn(List.of());

    final Map<String, Object> result = controller.confirmTotp(body("000000", null), session, response);

    assertEquals(400, response.getStatus());
    assertEquals(TwoFactorController.WRONG_CODE, result.get("error"));
  }

  @Test
  void qrCodeIsServedUncached() {
    when(twoFactorService.enrollmentQrPng(user)).thenReturn(Optional.of(new byte[] {1, 2, 3}));

    final ResponseEntity<byte[]> result = controller.qr(session);

    assertEquals(200, result.getStatusCode().value());
    assertEquals(MediaType.IMAGE_PNG, result.getHeaders().getContentType());
    assertTrue(result.getHeaders().getCacheControl().contains("no-store"));
  }

  @Test
  void qrCodeIsNotFoundWithoutAnEnrollment() {
    when(twoFactorService.enrollmentQrPng(user)).thenReturn(Optional.empty());

    assertEquals(404, controller.qr(session).getStatusCode().value());
  }

  @Test
  void disablingNeedsTheRightPassword() {
    final Map<String, String> result = controller.disable(body(null, "wrong12"), session, response);

    assertEquals(403, response.getStatus());
    assertEquals(TwoFactorController.WRONG_PASSWORD, result.get("error"));
    verify(twoFactorService, never()).disable(any());
  }

  @Test
  void disablingWithoutAPasswordIsRefused() {
    controller.disable(body(null, ""), session, response);

    assertEquals(403, response.getStatus());
    verify(webLogin, never()).validatePassword(anyString(), anyString());
    verify(twoFactorService, never()).disable(any());
  }

  @Test
  void disablingWithTheRightPasswordWorks() {
    final Map<String, String> result = controller.disable(body(null, "secret1"), session, response);

    assertEquals(200, response.getStatus());
    assertEquals(TwoFactorController.DISABLED, result.get("status"));
    verify(twoFactorService).disable(user);
  }

  @Test
  void newRecoveryCodesNeedTheRightPassword() {
    when(twoFactorService.regenerateRecoveryCodes(user)).thenReturn(List.of("AAAAA-BBBBB"));

    final Map<String, Object> refused =
        controller.regenerateRecoveryCodes(body(null, "wrong12"), session, response);
    assertEquals(403, response.getStatus());
    assertEquals(TwoFactorController.WRONG_PASSWORD, refused.get("error"));

    response = new MockHttpServletResponse();
    final Map<String, Object> result =
        controller.regenerateRecoveryCodes(body(null, "secret1"), session, response);
    assertEquals(200, response.getStatus());
    assertEquals(List.of("AAAAA-BBBBB"), result.get("recoveryCodes"));
  }
}
