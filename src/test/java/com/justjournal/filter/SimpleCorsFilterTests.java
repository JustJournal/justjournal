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
package com.justjournal.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The CORS filter adds the access control headers and answers OPTIONS requests itself. */
class SimpleCorsFilterTests {

  private final SimpleCorsFilter filter = new SimpleCorsFilter();

  @Test
  void addsCorsHeaders_andContinuesTheChain() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.init(new MockFilterConfig());
    filter.doFilter(request, response, chain);
    filter.destroy();

    assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
    assertEquals("POST, GET, OPTIONS, DELETE, PUT, PATCH", response.getHeader("Access-Control-Allow-Methods"));
    assertEquals("3600", response.getHeader("Access-Control-Max-Age"));
    assertEquals(
        "x-auth-token, x-requested-with, accept, authorization, content-type",
        response.getHeader("Access-Control-Allow-Headers"));
    assertSame(request, chain.getRequest());
  }

  @Test
  void optionsRequests_stopAtTheFilter() throws Exception {
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/x"), response, chain);

    assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
    assertNull(chain.getRequest());
  }

  @Test
  void applicationExceptions_passThroughUnchanged() {
    for (final RuntimeException e :
        new RuntimeException[] {
          new NotFoundException(), new ForbiddenException(), new BadRequestException("bad"), new UnauthorizedException("no")
        }) {
      final FilterChain chain = (req, res) -> { throw e; };
      final RuntimeException thrown =
          assertThrows(
              RuntimeException.class,
              () -> filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), chain));
      assertSame(e, thrown);
    }
  }

  @Test
  void otherExceptions_areWrappedInAServletException() {
    final IllegalStateException cause = new IllegalStateException("boom");
    final FilterChain chain = (req, res) -> { throw cause; };

    final ServletException thrown =
        assertThrows(
            ServletException.class,
            () -> filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), chain));

    assertSame(cause, thrown.getRootCause());
  }
}
