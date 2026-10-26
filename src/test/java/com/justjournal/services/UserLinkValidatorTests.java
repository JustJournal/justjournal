/*
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
package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** @author Lucas Holt */
class UserLinkValidatorTests {

  /** Resolves everything to a public address except names starting with "missing" or "private". */
  private final UserLinkValidator validator =
      new UserLinkValidator() {
        @Override
        protected InetAddress[] resolve(final String host) throws UnknownHostException {
          if (host.startsWith("missing")) throw new UnknownHostException(host);
          final byte[] addr = host.startsWith("private") ? new byte[] {10, 0, 0, 1} : new byte[] {93, (byte) 184, (byte) 215, 14};
          return new InetAddress[] {InetAddress.getByAddress(host, addr)};
        }
      };

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://www.midnightbsd.org/",
        "http://example.com",
        "HTTPS://Example.com/path?q=1#frag",
        "https://example.com:8443/x",
        "https://notbit.ly/"
      })
  void validLinks(final String uri) {
    assertEquals(Optional.empty(), validator.validate("Title", uri));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "data:text/html,hi",
        "ftp://example.com/",
        "//example.com/",
        "example.com",
        "https://",
        "https://user:pass@example.com/",
        "https://example.com@evil.com/",
        "http://127.0.0.1/",
        "http://[::1]/",
        "http://localhost/",
        "https://missing.example.com/",
        "https://private.example.com/",
        "https://exa mple.com/"
      })
  void invalidLinks(final String uri) {
    assertTrue(validator.validate("Title", uri).isPresent(), uri);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://bit.ly/abc",
        "https://BIT.LY/abc",
        "https://bit.ly./abc",
        "http://tinyurl.com/abc",
        "https://t.co/abc",
        "https://goo.gl/abc",
        "https://ow.ly/abc",
        "https://is.gd/abc",
        "https://www.tinyurl.com/abc",
        "https://custom.bitly.com/abc"
      })
  void shortenersRejected(final String uri) {
    final Optional<String> result = validator.validate("Title", uri);
    assertTrue(result.isPresent(), uri);
    assertTrue(result.get().contains("shortening"), result.get());
  }

  @Test
  void titleRequired() {
    assertTrue(validator.validate(" ", "https://example.com/").isPresent());
    assertTrue(validator.validate(null, "https://example.com/").isPresent());
  }

  @Test
  void lengthLimits() {
    assertTrue(validator.validate("x".repeat(51), "https://example.com/").isPresent());
    assertFalse(validator.validate("x".repeat(50), "https://example.com/").isPresent());
    assertTrue(
        validator.validate("Title", "https://example.com/" + "a".repeat(240)).isPresent());
    assertTrue(validator.validate("Title", null).isPresent());
  }
}
