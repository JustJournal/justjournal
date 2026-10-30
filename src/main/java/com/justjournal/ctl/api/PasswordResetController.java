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

import com.justjournal.Login;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.model.api.ForgotPassword;
import com.justjournal.model.api.PasswordReset;
import com.justjournal.repository.cache.PasswordResetRepository;
import com.justjournal.services.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forgotten password reset. The request endpoint answers the same way whether or not the account
 * exists so it can't be used to discover usernames or e-mail addresses.
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/password")
public class PasswordResetController {

  static final String REQUEST_ACCEPTED =
      "If the username and e-mail address match an account, a password reset link has been sent"
          + " to that address.";

  static final String INVALID_LINK =
      "This password reset link is invalid or has expired. Please request a new one.";

  static final int MAX_REQUESTS_PER_IP = 5;

  static final Duration IP_WINDOW = Duration.ofMinutes(15);

  private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(10);

  private final PasswordResetService passwordResetService;

  private final PasswordResetRepository passwordResetRepository;

  public PasswordResetController(
      PasswordResetService passwordResetService,
      PasswordResetRepository passwordResetRepository) {
    this.passwordResetService = passwordResetService;
    this.passwordResetRepository = passwordResetRepository;
  }

  @PostMapping(
      value = "/forgot",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> forgot(
      @RequestBody final ForgotPassword forgotPassword,
      final HttpServletRequest request,
      final HttpServletResponse response) {

    // remote address rather than client supplied headers, which could be rotated to dodge the limit
    final Long count =
        passwordResetRepository
            .incrementIp(request.getRemoteAddr(), IP_WINDOW)
            .block(REDIS_TIMEOUT);
    if (count != null && count > MAX_REQUESTS_PER_IP) {
      log.warn("Too many password reset requests from {}", request.getRemoteAddr());
      response.setStatus(429);
      return ErrorHandler.modelError("Too many requests. Please try again later.");
    }

    // lookup and mail run in the background so response time doesn't reveal a match
    passwordResetService.requestReset(forgotPassword.getUsername(), forgotPassword.getEmail());
    return Collections.singletonMap("status", REQUEST_ACCEPTED);
  }

  @PostMapping(
      value = "/reset",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> reset(
      @RequestBody final PasswordReset passwordReset, final HttpServletResponse response) {

    if (!Login.isNewPassword(passwordReset.getPassword())) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Login.NEW_PASSWORD_RULE);
    }

    if (!passwordResetService.resetPassword(
        passwordReset.getToken(), passwordReset.getPassword())) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(INVALID_LINK);
    }

    return Collections.singletonMap("status", "Password Changed.");
  }
}
