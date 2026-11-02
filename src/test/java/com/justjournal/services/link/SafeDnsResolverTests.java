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
package com.justjournal.services.link;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SafeDnsResolverTests {

  private static InetAddress ip(final String literal) {
    try {
      return InetAddress.getByName(literal);
    } catch (final UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final Map<String, InetAddress[]> ZONE =
      Map.of(
          "public.example.com", new InetAddress[] {ip("93.184.216.34"), ip("2606:2800:220:1::1")},
          "mixed.example.com", new InetAddress[] {ip("93.184.216.34"), ip("10.0.0.1")},
          "mixed6.example.com", new InetAddress[] {ip("2606:2800:220:1::1"), ip("fd00::1")},
          "loopback.example.com", new InetAddress[] {ip("127.0.0.1")},
          "metadata.example.com", new InetAddress[] {ip("169.254.169.254")},
          "mapped.example.com", new InetAddress[] {ip("::ffff:127.0.0.1")},
          "empty.example.com", new InetAddress[0]);

  private SafeDnsResolver resolver;

  private SafeDnsResolver resolver(final long timeoutMs, final Predicate<InetAddress> allowed) {
    resolver =
        new SafeDnsResolver(timeoutMs, allowed) {
          @Override
          protected InetAddress[] lookup(final String host) throws UnknownHostException {
            if (host.startsWith("slow")) {
              try {
                Thread.sleep(5_000);
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            if (host.startsWith("broken")) throw new IllegalStateException("resolver bug");
            final InetAddress[] answer = ZONE.get(host);
            if (answer == null) throw new UnknownHostException(host);
            return answer;
          }
        };
    return resolver;
  }

  private SafeDnsResolver strict() {
    return resolver(1_000, PublicAddresses::isPublic);
  }

  @AfterEach
  void tearDown() {
    if (resolver != null) resolver.close();
  }

  @Test
  void resolve_returnsAllAddressesWhenAllArePublic() throws UnknownHostException {
    assertArrayEquals(ZONE.get("public.example.com"), strict().resolve("public.example.com"));
  }

  @Test
  void resolve_refusesWhenAnyAddressIsPrivate() {
    final SafeDnsResolver r = strict();
    for (final String host :
        new String[] {
          "mixed.example.com",
          "mixed6.example.com",
          "loopback.example.com",
          "metadata.example.com",
          "mapped.example.com"
        }) {
      final UnknownHostException e =
          assertThrows(UnknownHostException.class, () -> r.resolve(host), host);
      assertInstanceOf(NonPublicAddressException.class, e, host);
    }
  }

  @Test
  void resolve_refusesNameWithNoAddresses() {
    final UnknownHostException e =
        assertThrows(UnknownHostException.class, () -> strict().resolve("empty.example.com"));
    assertFalse(e instanceof NonPublicAddressException);
  }

  @Test
  void resolve_passesOnUnknownHost() {
    final UnknownHostException e =
        assertThrows(UnknownHostException.class, () -> strict().resolve("missing.example.com"));
    assertFalse(e instanceof NonPublicAddressException);
  }

  @Test
  void resolve_turnsLookupBugsIntoUnknownHost() {
    final UnknownHostException e =
        assertThrows(UnknownHostException.class, () -> strict().resolve("broken.example.com"));
    assertFalse(e instanceof NonPublicAddressException);
    assertInstanceOf(IllegalStateException.class, e.getCause());
  }

  @Test
  void resolve_givesUpAfterTimeout() {
    final SafeDnsResolver r = resolver(200, PublicAddresses::isPublic);
    final long start = System.currentTimeMillis();
    final UnknownHostException e =
        assertThrows(UnknownHostException.class, () -> r.resolve("slow.example.com"));
    assertTrue(System.currentTimeMillis() - start < 2_000);
    assertTrue(e.getMessage().contains("timed out"), e.getMessage());
    assertFalse(e instanceof NonPublicAddressException);
  }

  @Test
  void resolve_customPredicate() throws UnknownHostException {
    final SafeDnsResolver r =
        resolver(1_000, a -> a.isLoopbackAddress() || PublicAddresses.isPublic(a));
    assertEquals(1, r.resolve("loopback.example.com").length);
    assertThrows(NonPublicAddressException.class, () -> r.resolve("mixed.example.com"));
  }

  /** The production lookup, on names that never leave the machine. */
  @Test
  void defaultLookupRefusesLocalAddresses() {
    resolver = new SafeDnsResolver(2_000);
    assertThrows(NonPublicAddressException.class, () -> resolver.resolve("127.0.0.1"));
    assertThrows(NonPublicAddressException.class, () -> resolver.resolve("::1"));
    assertThrows(NonPublicAddressException.class, () -> resolver.resolve("localhost"));
  }
}
