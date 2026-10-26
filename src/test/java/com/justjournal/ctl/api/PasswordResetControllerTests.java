/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.api.ForgotPassword;
import com.justjournal.model.api.PasswordReset;
import com.justjournal.repository.cache.PasswordResetRepository;
import com.justjournal.services.PasswordResetService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import reactor.core.publisher.Mono;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTests {

  @Mock private PasswordResetService passwordResetService;

  @Mock private PasswordResetRepository passwordResetRepository;

  @InjectMocks private PasswordResetController passwordResetController;

  private MockHttpServletRequest request;

  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");
    response = new MockHttpServletResponse();
  }

  private ForgotPassword forgot(final String username, final String email) {
    final ForgotPassword forgotPassword = new ForgotPassword();
    forgotPassword.setUsername(username);
    forgotPassword.setEmail(email);
    return forgotPassword;
  }

  @Test
  void forgotAnswersTheSameForAnyAccount() {
    when(passwordResetRepository.incrementIp(eq("192.0.2.10"), any(Duration.class)))
        .thenReturn(Mono.just(1L));

    final Map<String, String> result =
        passwordResetController.forgot(forgot("nobody", "x@example.com"), request, response);

    assertEquals(200, response.getStatus());
    assertEquals(PasswordResetController.REQUEST_ACCEPTED, result.get("status"));
    verify(passwordResetService).requestReset("nobody", "x@example.com");
  }

  @Test
  void forgotIsLimitedPerAddress() {
    when(passwordResetRepository.incrementIp(eq("192.0.2.10"), any(Duration.class)))
        .thenReturn(Mono.just((long) PasswordResetController.MAX_REQUESTS_PER_IP + 1));

    passwordResetController.forgot(forgot("jjsite", "x@example.com"), request, response);

    assertEquals(429, response.getStatus());
    verify(passwordResetService, never()).requestReset(anyString(), anyString());
  }

  @Test
  void resetWithInvalidLinkFails() {
    final PasswordReset reset = new PasswordReset();
    reset.setToken("bogus");
    reset.setPassword("newpass1");
    when(passwordResetService.resetPassword("bogus", "newpass1")).thenReturn(false);

    final Map<String, String> result = passwordResetController.reset(reset, response);

    assertEquals(400, response.getStatus());
    assertEquals(PasswordResetController.INVALID_LINK, result.get("error"));
  }

  @Test
  void resetWithInvalidPasswordFails() {
    final PasswordReset reset = new PasswordReset();
    reset.setToken("bogus");
    reset.setPassword("bad<>");

    passwordResetController.reset(reset, response);

    assertEquals(400, response.getStatus());
    verify(passwordResetService, never()).resetPassword(anyString(), anyString());
  }

  @Test
  void resetWithValidLinkSucceeds() {
    final PasswordReset reset = new PasswordReset();
    reset.setToken("token");
    reset.setPassword("newpass1");
    when(passwordResetService.resetPassword("token", "newpass1")).thenReturn(true);

    passwordResetController.reset(reset, response);

    assertEquals(200, response.getStatus());
  }
}
