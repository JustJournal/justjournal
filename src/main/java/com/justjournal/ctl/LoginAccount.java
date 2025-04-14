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
package com.justjournal.ctl;

import static com.justjournal.core.Constants.*;

import com.justjournal.Login;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Login account servlet.
 *
 * @author Lucas Holt
 *     <p>Version 1.1 changes to a stringbuffer for output. This should improve performance a bit.
 *     <p>1.2 fixed a bug with NULL pointer exceptions.
 *     <p>Mon Sep 19 2005 1.3 added JJ.LOGIN.FAIL and JJ.LOGIN.OK for desktop clients.
 *     <p>
 */
@Slf4j
@Component
public class LoginAccount extends JustJournalBaseServlet {

  private final Login webLogin;

  @Autowired
  public LoginAccount(Login webLogin) {
    super();
    this.webLogin = webLogin;
  }

  @Override
  protected void execute(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final HttpSession session,
      final StringBuilder sb) {
    final int userID;
    String userName = fixInput(request, PARAM_USERNAME);
    final String password = fixInput(request, PARAM_PASSWORD);
    final String userAgent = fixHeaderInput(request, HEADER_USER_AGENT);
    final String mobile = fixInput(request, PARAM_MOBILE);

    // adjust the case
    userName = userName.toLowerCase();

    if (log.isDebugEnabled()) {
      log.debug("User Agent is: {} mobile: {}", userAgent, mobile);
    }

    try {
      if (log.isDebugEnabled()) log.debug("Attempting Login Validation  ");

      userID = webLogin.validate(userName, password); // TODO: broken

      if (userID > 0) {
        sb.append(JJ_LOGIN_OK);
        webLogin.setLastLogin(userID);
      } else {
        sb.append(JJ_LOGIN_FAIL);
      }
    } catch (final Exception e3) {
      sb.append(JJ_LOGIN_FAIL);
      log.error("Login failed: {}", userName, e3);
    }
  }

  /** Returns a short description of the servlet. */
  @Override
  public String getServletInfo() {
    return "login to journal service";
  }
}
