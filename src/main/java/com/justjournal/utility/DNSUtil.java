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

import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/** @author Lucas Holt */
public class DNSUtil {

  private DNSUtil() {
    super();
  }

  public static boolean isUrlDomainValid(String uri) {
    if (StringUtils.isEmpty(uri)) return false;

    try {
      final URI tmpuri = new URI(uri);
      return isDomainValid(tmpuri.getHost());
    } catch (URISyntaxException ignored) {
      return false;
    }
  }

  public static String getDomainFromEmail(final String address) {
    final int at = address.lastIndexOf('@');
    if (address.length() < at + 1) return null;

    return address.substring(at + 1);
  }

  public static boolean isEmailDomainValid(final String address) {
    if (address == null || address.length() < 3) return false;

    return isDomainValid(getDomainFromEmail(address));
  }

  public static boolean isDomainValid(final String domainName) {
    if (domainName == null || domainName.isEmpty()) return false;

    // Max FQDN length is between 1 and 253 characters.
    if (domainName.length() > 253) return false;

    var parts = domainName.split("\\.");
    for (var part : parts) {
      if (part.length() > 63) return false; // label part between dots max length is 63.
    }

    try {
      final InetAddress inetAddress = InetAddress.getByName(domainName);
      return inetAddress != null;
    } catch (UnknownHostException ignored) {
      return false;
    }
  }
}
