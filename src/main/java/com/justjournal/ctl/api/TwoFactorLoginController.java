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
import static com.justjournal.core.Constants.TWO_FACTOR_TRUST_COOKIE;

import com.justjournal.Login;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.model.LoginResponse;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.model.User;
import com.justjournal.model.api.TwoFactorRequest;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.TwoFactorService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Second step of a login for accounts with two factor authentication. Only works on a session
 * that just passed the password check at {@code POST /api/login}.
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/login/2fa")
public class TwoFactorLoginController {

  static final String CODE_SENT = "A new code has been sent.";

  static final String CODE_NOT_SENT =
      "A code was sent recently. Please wait a minute before asking for another.";

  private final Login webLogin;

  private final UserRepository userRepository;

  private final TwoFactorService twoFactorService;

  public TwoFactorLoginController(
      Login webLogin, UserRepository userRepository, TwoFactorService twoFactorService) {
    this.webLogin = webLogin;
    this.userRepository = userRepository;
    this.twoFactorService = twoFactorService;
  }

  /**
   * What the verification page needs: is a login waiting, for whom, and by which method.
   *
   * @return JJ.LOGIN.2FA with the method, or 401 JJ.LOGIN.NONE
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LoginResponse> status(final HttpSession session) {
    final LoginResponse response = new LoginResponse();
    if (!Login.isPendingSecondFactor(session)) {
      response.setStatus(JJ_LOGIN_NONE);
      return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }
    response.setStatus(JJ_LOGIN_2FA);
    response.setUsername(Login.pendingLoginName(session));
    final TwoFactorMethod method = Login.pendingMethod(session);
    response.setTwoFactorMethod(method == null ? null : method.name());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  /**
   * Finish the login with a code from the e-mail, the authenticator app, or the recovery list.
   *
   * @return JJ.LOGIN.OK and the session is logged in; 401 JJ.LOGIN.FAIL for a wrong code; 429
   *     JJ.LOGIN.FAIL when too many codes were wrong and the login must start over
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LoginResponse> verify(
      @RequestBody final TwoFactorRequest body,
      final HttpServletRequest request,
      final HttpServletResponse servletResponse) {
    final HttpSession session = request.getSession(true);
    final LoginResponse response = new LoginResponse();

    if (!Login.isPendingSecondFactor(session)) {
      response.setStatus(JJ_LOGIN_NONE);
      return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    final int userId = Login.pendingLoginId(session);
    final String username = Login.pendingLoginName(session);
    response.setUsername(username);

    switch (twoFactorService.verify(userId, body.getCode())) {
      case LOCKED -> {
        log.warn("Two factor login locked for {}", username);
        Login.clearPendingSecondFactor(session);
        webLogin.blockIp(30);
        response.setStatus(JJ_LOGIN_FAIL);
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
      }
      case INVALID -> {
        log.warn("Two factor code rejected for {}", username);
        response.setStatus(JJ_LOGIN_FAIL);
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
      }
      case OK -> {
        webLogin.completeLogin(request, userId, username);
        if (body.isRememberDevice()) {
          servletResponse.addCookie(trustCookie(twoFactorService.rememberDevice(userId), request));
        }
        response.setStatus(JJ_LOGIN_OK);
        return new ResponseEntity<>(response, HttpStatus.OK);
      }
    }
    response.setStatus(JJ_LOGIN_FAIL);
    return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
  }

  /** Mail another code to a user whose method is e-mail. */
  @PostMapping(value = "/resend", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> resend(final HttpSession session, final HttpServletResponse response) {
    if (!Login.isPendingSecondFactor(session) || Login.pendingMethod(session) != TwoFactorMethod.EMAIL) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return ErrorHandler.modelError("No login is waiting for an e-mail code.");
    }

    final Optional<User> user = userRepository.findById(Login.pendingLoginId(session));
    if (user.isEmpty() || !twoFactorService.sendLoginCode(user.get())) {
      response.setStatus(429);
      return ErrorHandler.modelError(CODE_NOT_SENT);
    }
    return Collections.singletonMap("status", CODE_SENT);
  }

  static Cookie trustCookie(final String token, final HttpServletRequest request) {
    final Cookie cookie = new Cookie(TWO_FACTOR_TRUST_COOKIE, token);
    cookie.setHttpOnly(true);
    cookie.setSecure(request.isSecure());
    cookie.setPath("/");
    cookie.setMaxAge((int) TwoFactorService.TRUST_TTL.toSeconds());
    cookie.setAttribute("SameSite", "Lax");
    return cookie;
  }
}
