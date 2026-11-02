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

import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.UrlPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Validates links users add to their blog sidebar. Links must be absolute http(s) URLs whose host
 * resolves in DNS to a public address, and may not point at link shortening services since those
 * hide the real destination from readers.
 *
 * @author Lucas Holt
 */
@Component
public class UserLinkValidator {

  /** Matches the user_link.title column size. */
  public static final int MAX_TITLE_LENGTH = 50;

  /** Matches the user_link.uri column size. */
  public static final int MAX_URI_LENGTH = 255;

  private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

  /**
   * Validate a link title and uri.
   *
   * @param title link title
   * @param uri link address
   * @return an error message suitable for display, or empty if the link is acceptable
   */
  public Optional<String> validate(final String title, final String uri) {
    if (StringUtils.isBlank(title)) return Optional.of("A link title is required.");
    if (title.trim().length() > MAX_TITLE_LENGTH)
      return Optional.of("Link title must be " + MAX_TITLE_LENGTH + " characters or less.");

    if (StringUtils.isBlank(uri)) return Optional.of("A link URL is required.");
    final String trimmedUri = uri.trim();
    if (trimmedUri.length() > MAX_URI_LENGTH)
      return Optional.of("Link URL must be " + MAX_URI_LENGTH + " characters or less.");

    final URI parsed;
    try {
      parsed = new URI(trimmedUri);
    } catch (final URISyntaxException e) {
      return Optional.of("Link URL is not valid.");
    }

    final String scheme = parsed.getScheme();
    if (scheme == null
        || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
      return Optional.of("Link URL must start with http:// or https://");

    // user info allows deceptive urls like https://trusted.com@evil.com/
    if (parsed.getRawUserInfo() != null)
      return Optional.of("Link URL may not contain a username or password.");

    String host = parsed.getHost();
    if (StringUtils.isBlank(host)) return Optional.of("Link URL must include a host name.");
    host = StringUtils.removeEnd(host.toLowerCase(Locale.ROOT), ".");

    if (host.startsWith("[") || IPV4.matcher(host).matches())
      return Optional.of("Link URL must use a domain name, not an IP address.");
    if (!host.contains(".")) return Optional.of("Link URL must use a fully qualified domain name.");

    if (UrlPolicy.isShortener(host)) return Optional.of(LinkRejectedException.SHORTENER_MESSAGE);

    if (!isPubliclyResolvable(host))
      return Optional.of("The domain " + host + " could not be found.");

    return Optional.empty();
  }

  boolean isPubliclyResolvable(final String host) {
    if (host.length() > 253) return false;
    for (final String label : host.split("\\.")) {
      if (label.isEmpty() || label.length() > 63) return false;
    }

    try {
      for (final InetAddress address : resolve(host)) {
        if (!(address.isLoopbackAddress()
            || address.isAnyLocalAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress())) return true;
      }
    } catch (final UnknownHostException | SecurityException ignored) {
      // fall through
    }
    return false;
  }

  /** DNS lookup; overridable for tests. */
  protected InetAddress[] resolve(final String host) throws UnknownHostException {
    return InetAddress.getAllByName(host);
  }
}
