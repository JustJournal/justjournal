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


import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.core.Settings;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.model.User;
import com.justjournal.model.api.NewUser;
import com.justjournal.services.AccountService;
import com.justjournal.utility.DNSUtil;
import com.justjournal.utility.StringUtil;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Create new accounts in Just Journal. To delete accounts, use AccountController.
 *
 * @author Lucas Holt
 * @see com.justjournal.ctl.api.AccountController
 */
@Slf4j
@Transactional
@RestController
@RequestMapping("/api/signup")
public class SignUpController {

  private final AccountService accountService;

  private final Settings settings;

  private final Login login;

  private static final String EMAIL_INVALID = "Invalid email address.";

  public SignUpController(AccountService accountService, Settings settings, Login login) {
    this.accountService = accountService;
    this.settings = settings;
    this.login = login;
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> post(
      @RequestBody final NewUser user, final HttpServletResponse response) {

    if (!settings.isUserAllowNew()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError(Constants.ERR_ADD_USER);
    }

    if (!StringUtil.isEmailValid(user.getEmail()) || !DNSUtil.isEmailDomainValid((user.getEmail()))) {
      throw new IllegalArgumentException(EMAIL_INVALID);
    }

    if (!login.exists(user.getUsername())) {
        log.warn("Username used for signup is invalid: {}", user.getUsername());

      throw new IllegalArgumentException("Username must be letters and numbers only");
    }

    if (!Login.isPassword(user.getPassword())) {
      log.warn("Password for signup is invalid");

      throw new IllegalArgumentException("Password must be 5-18 characters.");
    }

    return newUser(user, response);
  }

  private Map<String, String> newUser(final NewUser newUser, final HttpServletResponse response) {

    try {
      final User user = accountService.signup(newUser);

      response.setStatus(HttpServletResponse.SC_CREATED);
      return java.util.Collections.singletonMap("id", Integer.toString(user.getId()));
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return ErrorHandler.modelError(Constants.ERR_ADD_USER);
    }
  }
}
