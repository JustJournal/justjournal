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

import static com.justjournal.core.Constants.*;

import com.justjournal.Login;
import com.justjournal.exception.BadRequestException;
import com.justjournal.model.LoginResponse;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.model.UserTwoFactor;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.TwoFactorService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
 * Log user into session. Accounts with a second factor turned on get status {@code JJ.LOGIN.2FA}
 * back and finish the login at {@code /api/login/2fa}.
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/login")
public class LoginController {

  private final com.justjournal.Login webLogin;

  private final UserRepository userRepository;

  private final TwoFactorService twoFactorService;

  public LoginController(
      Login webLogin, UserRepository userRepository, TwoFactorService twoFactorService) {
    this.webLogin = webLogin;
    this.userRepository = userRepository;
    this.twoFactorService = twoFactorService;
  }

  /**
   * Check the login status of the user
   *
   * @param session HttpSession
   * @return LoginResponse with login OK or NONE
   */
  @GetMapping(headers = HEADER_ACCEPT_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
  public LoginResponse getLoginStatus(final HttpSession session) {
    final LoginResponse response = new LoginResponse();
    final String username = (String) session.getAttribute(LOGIN_ATTRNAME);
    response.setUsername(username);
    response.setStatus(username == null ? JJ_LOGIN_NONE : JJ_LOGIN_OK);
    return response;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE,
      headers = {HEADER_ACCEPT_ALL, "content-type=application/json"})
  public ResponseEntity<LoginResponse> post(
      @RequestBody final com.justjournal.core.Login login, final HttpServletRequest request) {
    final LoginResponse loginResponse = new LoginResponse();

    try {
      // Current authentication needs to get whacked
      HttpSession session = request.getSession(true);
      if (Login.isAuthenticated(session)) {
        session.invalidate();
        session = request.getSession(true); // reset
      }
      // a fresh password attempt also drops any half finished login
      Login.clearPendingSecondFactor(session);

      final int userID = webLogin.validatePassword(login.getUsername(), login.getPassword());
      if (userID <= BAD_USER_ID) {
        log.error("Login attempt failed with user: " + login.getUsername());

        loginResponse.setStatus(JJ_LOGIN_FAIL);
        return new ResponseEntity<>(loginResponse, HttpStatus.UNAUTHORIZED);
      }
      log.debug("LoginController.post(): Username is " + login.getUsername());
      loginResponse.setUsername(login.getUsername());

      final Optional<UserTwoFactor> twoFactor = twoFactorService.find(userID);
      if (twoFactor.isPresent()
          && !twoFactorService.isTrustedDevice(
              userID, cookieValue(request, TWO_FACTOR_TRUST_COOKIE))) {
        // password is right but the session stays logged out until the code comes back
        Login.setPendingSecondFactor(
            session, userID, login.getUsername(), twoFactor.get().getMethod());
        if (twoFactor.get().getMethod() == TwoFactorMethod.EMAIL) {
          try {
            userRepository.findById(userID).ifPresent(twoFactorService::sendLoginCode);
          } catch (final BadRequestException e) {
            // without the code the login can't be finished, so don't leave it half done
            Login.clearPendingSecondFactor(session);
            loginResponse.setStatus(JJ_LOGIN_FAIL);
            return new ResponseEntity<>(loginResponse, HttpStatus.SERVICE_UNAVAILABLE);
          }
        }

        loginResponse.setStatus(JJ_LOGIN_2FA);
        loginResponse.setTwoFactorMethod(twoFactor.get().getMethod().name());
        return new ResponseEntity<>(loginResponse, HttpStatus.OK);
      }

      webLogin.completeLogin(request, userID, login.getUsername());
      loginResponse.setStatus(JJ_LOGIN_OK);
      return new ResponseEntity<>(loginResponse, HttpStatus.OK);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      return new ResponseEntity<>(loginResponse, HttpStatus.BAD_REQUEST);
    }
  }

  static String cookieValue(final HttpServletRequest request, final String name) {
    final Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (final Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) return cookie.getValue();
    }
    return null;
  }
}
