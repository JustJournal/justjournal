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

import java.net.InetAddress;

/**
 * Tells public internet addresses from private, reserved and special purpose ones, so the server
 * can't be talked into fetching from its own network (SSRF).
 *
 * <p>Everything is decided on the address bytes; nothing is parsed from text. The IANA special
 * purpose registries are the reference. IPv6 is checked as an allowlist: only global unicast
 * (2000::/3) can be public, which by itself rules out ::/8 (including IPv4-mapped and
 * IPv4-compatible addresses), 64:ff9b::/96 NAT64, 100::/64, fc00::/7, fe80::/10, fec0::/10 and
 * ff00::/8. The tunnelling and documentation ranges inside 2000::/3 are then refused, since they can
 * carry an IPv4 address that would get past the IPv4 checks.
 */
public final class PublicAddresses {

  private PublicAddresses() {}

  /**
   * Whether a connection to this address would leave for the public internet.
   *
   * @param address any address
   * @return true only for a globally routable unicast address
   */
  public static boolean isPublic(final InetAddress address) {
    if (address == null) return false;
    final byte[] b = address.getAddress();
    if (b.length == 4) return isPublicV4(b, 0);
    if (b.length == 16) return isPublicV6(b);
    return false;
  }

  private static boolean isPublicV4(final byte[] bytes, final int offset) {
    final int a = bytes[offset] & 0xff;
    final int b = bytes[offset + 1] & 0xff;
    final int c = bytes[offset + 2] & 0xff;

    if (a == 0) return false; // 0.0.0.0/8 "this network"
    if (a == 10) return false; // 10.0.0.0/8 private
    if (a == 100 && (b & 0xc0) == 64) return false; // 100.64.0.0/10 carrier-grade NAT
    if (a == 127) return false; // 127.0.0.0/8 loopback
    if (a == 169 && b == 254) return false; // 169.254.0.0/16 link local, cloud metadata
    if (a == 172 && (b & 0xf0) == 16) return false; // 172.16.0.0/12 private
    if (a == 192 && b == 0 && c == 0) return false; // 192.0.0.0/24 IETF protocol assignments
    if (a == 192 && b == 0 && c == 2) return false; // 192.0.2.0/24 TEST-NET-1
    if (a == 192 && b == 88 && c == 99) return false; // 192.88.99.0/24 6to4 relay anycast
    if (a == 192 && b == 168) return false; // 192.168.0.0/16 private
    if (a == 198 && (b & 0xfe) == 18) return false; // 198.18.0.0/15 benchmarking
    if (a == 198 && b == 51 && c == 100) return false; // 198.51.100.0/24 TEST-NET-2
    if (a == 203 && b == 0 && c == 113) return false; // 203.0.113.0/24 TEST-NET-3
    // 224.0.0.0/4 multicast, 240.0.0.0/4 reserved, 255.255.255.255 broadcast
    return a < 224;
  }

  private static boolean isPublicV6(final byte[] b) {
    final int first = b[0] & 0xff;
    final int second = b[1] & 0xff;
    final int third = b[2] & 0xff;

    // global unicast is 2000::/3; everything else is local, multicast, mapped or reserved
    if ((first & 0xe0) != 0x20) return false;

    if (first == 0x20 && second == 0x01) {
      // 2001::/23 IETF protocol assignments, including 2001::/32 Teredo, which embeds an IPv4
      // address, and 2001:2::/48 benchmarking and 2001:10::/28 ORCHID
      if ((third & 0xfe) == 0x00) return false;
      // 2001:db8::/32 documentation
      if (third == 0x0d && (b[3] & 0xff) == 0xb8) return false;
    }
    // 2002::/16 6to4, which embeds an IPv4 address
    if (first == 0x20 && second == 0x02) return false;
    // 3fff::/20 documentation (RFC 9637)
    if (first == 0x3f && second == 0xff && (third & 0xf0) == 0x00) return false;
    return true;
  }
}
