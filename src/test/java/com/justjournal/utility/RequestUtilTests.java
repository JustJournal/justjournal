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
package com.justjournal.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** RequestUtil reads the caller's address from the current request. */
class RequestUtilTests {

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  private static MockHttpServletRequest bind(final String remoteAddr) {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return request;
  }

  @Test
  void withoutARequest_theAddressIsUnknown() {
    RequestContextHolder.resetRequestAttributes();

    assertEquals("0.0.0.0", RequestUtil.getRemoteIP());
  }

  @Test
  void withoutProxyHeaders_theRemoteAddressIsUsed() {
    bind("10.0.0.7");

    assertEquals("10.0.0.7", RequestUtil.getRemoteIP());
  }

  @Test
  void proxyHeaders_areListedInOrder_firstHopOnly_thenTheRemoteAddress_commaSeparated() {
    final MockHttpServletRequest request = bind("10.0.0.7");
    request.addHeader("X-Forwarded-For", "203.0.113.5, 198.51.100.9");
    request.addHeader("Proxy-Client-IP", "unknown");
    request.addHeader("HTTP_X_FORWARDED_FOR", "192.0.2.44");
    request.addHeader("HTTP_VIA", "");

    final String ip = RequestUtil.getRemoteIP();

    assertEquals("203.0.113.5,192.0.2.44,10.0.0.7", ip);
    assertTrue(!ip.contains("198.51.100.9"), ip);
    assertTrue(!ip.contains("unknown"), ip);
  }
}
