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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The legacy login servlet answers with a JJ.LOGIN status token. */
class LoginAccountTests {

  private Login login;
  private LoginAccount servlet;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    login = mock(Login.class);
    servlet = new LoginAccount(login);
    response = new MockHttpServletResponse();
  }

  private static MockHttpServletRequest request(final String method, final String username, final String password) {
    final MockHttpServletRequest request = new MockHttpServletRequest(method, "/loginAccount");
    request.setParameter("username", username);
    request.setParameter("password", password);
    request.addHeader("User-Agent", "JUnit");
    return request;
  }

  @Test
  void post_withGoodCredentials_isOk_andRecordsTheLogin() throws Exception {
    when(login.validate("ada", "secret")).thenReturn(5);

    servlet.service(request("POST", " Ada ", "secret"), response);

    assertEquals("JJ.LOGIN.OK", response.getContentAsString());
    verify(login).setLastLogin(5);
    assertEquals("text/html;charset=utf-8", response.getContentType().replace(" ", ""));
    assertEquals("no-store, no-cache, must-revalidate", response.getHeader("Cache-Control"));
    assertEquals("no-cache", response.getHeader("Pragma"));
    assertNotNull(response.getHeader("Expires"));
    assertNotNull(response.getHeader("Last-Modified"));
    assertNotNull(response.getHeader("ETag"));
    assertEquals("JJ.LOGIN.OK".length(), response.getContentLength());
  }

  @Test
  void get_withBadCredentials_fails() throws Exception {
    when(login.validate("ada", "wrong")).thenReturn(0);

    servlet.service(request("GET", "ada", "wrong"), response);

    assertEquals("JJ.LOGIN.FAIL", response.getContentAsString());
    verify(login, never()).setLastLogin(anyInt());
  }

  @Test
  void aBrokenLoginService_alsoFails() throws Exception {
    when(login.validate("ada", "secret")).thenThrow(new IllegalStateException("db"));

    servlet.service(request("POST", "ada", "secret"), response);

    assertEquals("JJ.LOGIN.FAIL", response.getContentAsString());
  }

  @Test
  void missingParameters_areTreatedAsEmpty() throws Exception {
    when(login.validate("", "")).thenReturn(0);

    servlet.service(new MockHttpServletRequest("POST", "/loginAccount"), response);

    assertEquals("JJ.LOGIN.FAIL", response.getContentAsString());
  }

  @Test
  void head_sendsHeadersWithoutABody() throws Exception {
    when(login.validate("ada", "secret")).thenReturn(5);

    servlet.service(request("HEAD", "ada", "secret"), response);

    assertEquals("", response.getContentAsString());
    assertEquals("JJ.LOGIN.OK".length(), response.getContentLength());
    assertTrue(response.isCommitted());
  }

  @Test
  void servletInfoAndLastModified() {
    assertEquals("login to journal service", servlet.getServletInfo());
    final long lastModified = servlet.getLastModified(new MockHttpServletRequest());
    assertEquals(0L, lastModified % 1000);
    assertTrue(Math.abs(System.currentTimeMillis() - lastModified) < 5_000);
  }
}
