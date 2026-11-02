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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicAddressesTests {

  /** Literals only, so InetAddress never asks DNS. */
  private static InetAddress literal(final String address) throws UnknownHostException {
    return InetAddress.getByName(address);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // 0.0.0.0/8
        "0.0.0.0",
        "0.1.2.3",
        "0.255.255.255",
        // 10.0.0.0/8
        "10.0.0.1",
        "10.255.255.255",
        // 100.64.0.0/10
        "100.64.0.1",
        "100.127.255.255",
        // 127.0.0.0/8
        "127.0.0.1",
        "127.255.255.254",
        // 169.254.0.0/16, including cloud metadata
        "169.254.0.1",
        "169.254.169.254",
        // 172.16.0.0/12
        "172.16.0.1",
        "172.20.1.1",
        "172.31.255.255",
        // 192.0.0.0/24
        "192.0.0.1",
        "192.0.0.170",
        // 192.0.2.0/24
        "192.0.2.1",
        // 192.88.99.0/24
        "192.88.99.1",
        // 192.168.0.0/16
        "192.168.0.1",
        "192.168.255.255",
        // 198.18.0.0/15
        "198.18.0.1",
        "198.19.255.255",
        // documentation
        "198.51.100.7",
        "203.0.113.9",
        // 224.0.0.0/4
        "224.0.0.1",
        "233.252.0.1",
        "239.255.255.250",
        // 240.0.0.0/4 and broadcast
        "240.0.0.1",
        "250.1.2.3",
        "255.255.255.255",
      })
  void privateAndReservedIpv4(final String address) throws UnknownHostException {
    assertFalse(PublicAddresses.isPublic(literal(address)), address);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "93.184.216.34",
        "8.8.8.8",
        "1.1.1.1",
        "1.0.0.0",
        "9.255.255.255",
        "11.0.0.0",
        "100.63.255.255",
        "100.128.0.0",
        "126.255.255.255",
        "128.0.0.1",
        "169.253.255.255",
        "169.255.0.1",
        "172.15.255.255",
        "172.32.0.0",
        "192.0.1.1",
        "192.0.3.1",
        "192.88.98.1",
        "192.167.255.255",
        "192.169.0.0",
        "198.17.255.255",
        "198.20.0.0",
        "198.51.101.1",
        "203.0.112.1",
        "223.255.255.255",
      })
  void publicIpv4(final String address) throws UnknownHostException {
    assertTrue(PublicAddresses.isPublic(literal(address)), address);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // unspecified and loopback
        "::",
        "::1",
        // IPv4-mapped (Java turns these into IPv4 addresses, which must still be refused)
        "::ffff:127.0.0.1",
        "::ffff:10.0.0.1",
        "::ffff:169.254.169.254",
        // IPv4-compatible ::/96
        "::7f00:1",
        "::127.0.0.1",
        "::8.8.8.8",
        // NAT64
        "64:ff9b::7f00:1",
        "64:ff9b::808:808",
        "64:ff9b:1::1",
        // discard only
        "100::1",
        // 2001::/23, including Teredo, benchmarking and ORCHID
        "2001::1",
        "2001:0:4136:e378:8000:63bf:3fff:fdd2",
        "2001:2::1",
        "2001:10::1",
        "2001:1ff:ffff:ffff::1",
        // documentation
        "2001:db8::1",
        "2001:db8:ffff::1",
        "3fff::1",
        "3fff:fff::1",
        // 6to4
        "2002::1",
        "2002:7f00:1::",
        "2002:808:808::1",
        // unique local
        "fc00::1",
        "fd00::1",
        "fdff:ffff::1",
        // link local and site local
        "fe80::1",
        "febf::1",
        "fec0::1",
        "feff::1",
        // multicast
        "ff02::1",
        "ff0e::1",
        // unallocated, outside 2000::/3
        "1::1",
        "4000::1",
        "e000::1",
      })
  void privateAndReservedIpv6(final String address) throws UnknownHostException {
    assertFalse(PublicAddresses.isPublic(literal(address)), address);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2606:2800:220:1::1",
        "2606:2800:220:1:248:1893:25c8:1946",
        "2001:4860:4860::8888",
        "2001:200::1",
        "2001:db9::1",
        "2003::1",
        "2a00:1450:4001:80b::200e",
        "2c0f:fb50:4002::1",
        "3ffe::1",
        "3fff:1000::1",
      })
  void publicIpv6(final String address) throws UnknownHostException {
    assertTrue(PublicAddresses.isPublic(literal(address)), address);
  }

  @Test
  void ipv4MappedAddressThatStaysIpv6IsRefused() throws UnknownHostException {
    final byte[] mapped = new byte[16];
    mapped[10] = (byte) 0xff;
    mapped[11] = (byte) 0xff;
    mapped[12] = 8;
    mapped[13] = 8;
    mapped[14] = 8;
    mapped[15] = 8;
    final InetAddress address = Inet6Address.getByAddress(null, mapped, -1);
    assertEquals(16, address.getAddress().length);
    assertFalse(PublicAddresses.isPublic(address));
  }

  @Test
  void ipv4MappedLoopbackIsRefusedWhicheverWayJavaRepresentsIt() throws UnknownHostException {
    final byte[] mapped = new byte[16];
    mapped[10] = (byte) 0xff;
    mapped[11] = (byte) 0xff;
    mapped[12] = 127;
    mapped[15] = 1;
    assertFalse(PublicAddresses.isPublic(InetAddress.getByAddress(mapped)));
    assertFalse(PublicAddresses.isPublic(Inet6Address.getByAddress(null, mapped, -1)));
  }

  @Test
  void nullIsNotPublic() {
    assertFalse(PublicAddresses.isPublic(null));
  }
}
