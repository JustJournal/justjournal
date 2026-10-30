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

import static com.justjournal.core.Constants.BAD_USER_ID;
import static com.justjournal.core.Constants.ERR_INVALID_LOGIN;

import com.justjournal.Login;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.UnauthorizedException;
import com.justjournal.model.User;
import com.justjournal.model.api.TotpEnrollment;
import com.justjournal.model.api.TwoFactorRequest;
import com.justjournal.model.api.TwoFactorStatus;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.TwoFactorService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two factor authentication preferences for the logged in user: turn it on with an e-mail code or
 * an authenticator app, manage recovery codes and remembered browsers, and turn it off.
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/account/2fa")
public class TwoFactorController {

  static final String WRONG_CODE = "That code is wrong or has expired. Please try again.";

  static final String WRONG_PASSWORD = "The password is wrong.";

  static final String ENABLED = "Two-factor authentication is now on.";

  static final String DISABLED = "Two-factor authentication is now off.";

  private final Login webLogin;

  private final UserRepository userRepository;

  private final TwoFactorService twoFactorService;

  public TwoFactorController(
      Login webLogin, UserRepository userRepository, TwoFactorService twoFactorService) {
    this.webLogin = webLogin;
    this.userRepository = userRepository;
    this.twoFactorService = twoFactorService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public TwoFactorStatus status(final HttpSession session) {
    return twoFactorService.status(currentUser(session));
  }

  /* ---- e-mail ---- */

  @PostMapping(value = "/email/start", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> startEmail(final HttpSession session) {
    final User user = currentUser(session);
    twoFactorService.startEmailEnrollment(user);
    return Collections.singletonMap("status", "A code has been sent to your e-mail address.");
  }

  @PostMapping(
      value = "/email/confirm",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> confirmEmail(
      @RequestBody final TwoFactorRequest body,
      final HttpSession session,
      final HttpServletResponse response) {
    if (!twoFactorService.confirmEmailEnrollment(currentUser(session), body.getCode())) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(WRONG_CODE);
    }
    return Collections.singletonMap("status", ENABLED);
  }

  /* ---- authenticator app ---- */

  @PostMapping(value = "/totp/start", produces = MediaType.APPLICATION_JSON_VALUE)
  public TotpEnrollment startTotp(final HttpSession session) {
    return twoFactorService.startTotpEnrollment(currentUser(session));
  }

  @GetMapping(value = "/totp/qr", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> qr(final HttpSession session) {
    return twoFactorService
        .enrollmentQrPng(currentUser(session))
        .map(
            png ->
                ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(png))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }

  @PostMapping(
      value = "/totp/confirm",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> confirmTotp(
      @RequestBody final TwoFactorRequest body,
      final HttpSession session,
      final HttpServletResponse response) {
    final List<String> codes =
        twoFactorService.confirmTotpEnrollment(currentUser(session), body.getCode());
    if (codes.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return Collections.singletonMap("error", WRONG_CODE);
    }
    return Map.of("status", ENABLED, "recoveryCodes", codes);
  }

  /* ---- management ---- */

  @PostMapping(
      value = "/recovery",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> regenerateRecoveryCodes(
      @RequestBody final TwoFactorRequest body,
      final HttpSession session,
      final HttpServletResponse response) {
    final User user = currentUser(session);
    if (!passwordMatches(user, body.getPassword())) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return Collections.singletonMap("error", WRONG_PASSWORD);
    }
    return Map.of("recoveryCodes", twoFactorService.regenerateRecoveryCodes(user));
  }

  @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> disable(
      @RequestBody final TwoFactorRequest body,
      final HttpSession session,
      final HttpServletResponse response) {
    final User user = currentUser(session);
    if (!passwordMatches(user, body.getPassword())) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError(WRONG_PASSWORD);
    }
    twoFactorService.disable(user);
    return Collections.singletonMap("status", DISABLED);
  }

  @DeleteMapping(value = "/trusted", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> forgetTrustedDevices(final HttpSession session) {
    twoFactorService.forgetTrustedDevices(currentUser(session).getId());
    return Collections.singletonMap("status", "Remembered browsers have been forgotten.");
  }

  /* ---- helpers ---- */

  private User currentUser(final HttpSession session) {
    if (!Login.isAuthenticated(session)) {
      webLogin.blockIp(5);
      throw new UnauthorizedException(ERR_INVALID_LOGIN);
    }
    return userRepository
        .findById(Login.currentLoginId(session))
        .orElseThrow(() -> new UnauthorizedException(ERR_INVALID_LOGIN));
  }

  private boolean passwordMatches(final User user, final String password) {
    if (password == null || password.isEmpty()) return false;
    final boolean ok = webLogin.validatePassword(user.getUsername(), password) > BAD_USER_ID;
    if (!ok) log.warn("Wrong password given on two factor settings for {}", user.getUsername());
    return ok;
  }
}
