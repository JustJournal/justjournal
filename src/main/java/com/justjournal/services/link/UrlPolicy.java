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

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Decides whether a link is acceptable by its text alone, and turns it into the one canonical form
 * that is stored, rendered and fetched. Nothing here touches the network; DNS and reputation checks
 * live in {@link LinkSafetyService}.
 *
 * <p>Only absolute http and https links to a domain name on the default ports pass. The host is
 * converted to ASCII, so an international name and its punycode form are the same link, and the
 * checks below can't be dodged with look-alike Unicode dots or upper case.
 *
 * <p>The domain lists ({@code link-safety.extra-shorteners}, {@code link-safety.blocked-domains})
 * may be YAML lists or comma separated values. A domain blocks itself and its subdomains: {@code
 * bad.com} blocks {@code x.bad.com} but not {@code notbad.com}.
 */
@Slf4j
@Component
public class UrlPolicy implements EnvironmentAware {

  /** Matches the entry_link.url column size. */
  public static final int MAX_LENGTH = 1024;

  /** Popular URL shortening / open redirect services. Subdomains are blocked as well. */
  public static final Set<String> SHORTENER_DOMAINS =
      Set.of(
          "adf.ly", "bc.vc", "bit.do", "bit.ly", "bitly.com", "bl.ink", "buff.ly", "cli.gs",
          "clck.ru", "cutt.ly", "db.tt", "dlvr.it", "fb.me", "goo.gl", "ift.tt", "is.gd", "j.mp",
          "lnkd.in", "mcaf.ee", "ow.ly", "po.st", "qr.ae", "rb.gy", "rebrand.ly", "s.id",
          "short.gy", "short.io", "shorte.st", "shorturl.at", "shorturl.com", "snip.ly", "soo.gd",
          "su.pr", "t.co", "t.ly", "tiny.cc", "tiny.one", "tinyurl.com", "tr.im", "trib.al",
          "v.gd", "x.co");

  /** Names reserved for local or special use (RFC 6761, 6762, 7686, 8375 and friends). */
  static final Set<String> SPECIAL_USE_DOMAINS =
      Set.of(
          "localhost", "local", "internal", "lan", "home.arpa", "onion", "test", "invalid",
          "example");

  /** One LDH label of a host name, at most 63 characters. */
  private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

  /**
   * A top level domain: letters, or punycode. No real TLD is numeric, so this also refuses every
   * spelling of an IPv4 address ({@code 127.1}, {@code 0x7f.1}, {@code 017700000001}).
   */
  private static final Pattern TLD = Pattern.compile("[a-z]{2,63}|xn--[a-z0-9-]{1,59}");

  private static final int MAX_HOST_LENGTH = 253;

  static final String EXTRA_SHORTENERS = "link-safety.extra-shorteners";
  static final String BLOCKED_DOMAINS = "link-safety.blocked-domains";

  private volatile Set<String> extraShorteners = Set.of();
  private volatile Set<String> blockedDomains = Set.of();

  /**
   * Reads the domain lists. Boot's binder is used rather than {@code @Value}, because {@code
   * @Value} silently sees nothing when a list is written as a YAML sequence.
   */
  @Override
  public void setEnvironment(final Environment environment) {
    final Binder binder = Binder.get(environment);
    setExtraShorteners(
        binder.bind(EXTRA_SHORTENERS, Bindable.listOf(String.class)).orElse(List.of()));
    setBlockedDomains(
        binder.bind(BLOCKED_DOMAINS, Bindable.listOf(String.class)).orElse(List.of()));
  }

  /** More shortening services, on top of {@link #SHORTENER_DOMAINS}. */
  public void setExtraShorteners(final List<String> domains) {
    this.extraShorteners = domainSet(domains, EXTRA_SHORTENERS);
  }

  /** Domains no link may point to. */
  public void setBlockedDomains(final List<String> domains) {
    this.blockedDomains = domainSet(domains, BLOCKED_DOMAINS);
  }

  /**
   * Check a link the way a writer typed it and return its canonical form.
   *
   * @param raw the link; surrounding white space is ignored
   * @return the canonical link
   * @throws LinkRejectedException if the link is malformed, points somewhere private, or names a
   *     shortener or blocked domain
   */
  public NormalizedUrl normalize(final String raw) throws LinkRejectedException {
    if (raw == null) throw reject(LinkRejectedException.Reason.SYNTAX);
    final String text = raw.strip();
    if (text.isEmpty() || text.length() > MAX_LENGTH)
      throw reject(LinkRejectedException.Reason.SYNTAX);
    for (int i = 0; i < text.length(); ) {
      final int cp = text.codePointAt(i);
      if (Character.isISOControl(cp)
          || Character.isWhitespace(cp)
          || Character.isSpaceChar(cp)
          || Character.getType(cp) == Character.FORMAT) // zero width and bidi controls
        throw reject(LinkRejectedException.Reason.SYNTAX);
      i += Character.charCount(cp);
    }

    final URI parsed;
    try {
      parsed = new URI(text);
    } catch (final URISyntaxException e) {
      throw reject(LinkRejectedException.Reason.SYNTAX);
    }
    if (parsed.isOpaque() || parsed.getScheme() == null)
      throw reject(LinkRejectedException.Reason.SYNTAX);
    final String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https"))
      throw reject(LinkRejectedException.Reason.SYNTAX);

    // the authority is split by hand: java.net.URI gives up on hosts that aren't ASCII
    final String authority = parsed.getRawAuthority();
    if (authority == null || authority.isEmpty()) throw reject(LinkRejectedException.Reason.SYNTAX);
    // user info allows deceptive urls like https://trusted.com@evil.com/
    if (authority.indexOf('@') >= 0) throw reject(LinkRejectedException.Reason.SYNTAX);
    // bracketed IPv6 literal
    if (authority.startsWith("[")) throw reject(LinkRejectedException.Reason.SYNTAX);

    String hostPart = authority;
    int port = -1;
    final int colon = authority.lastIndexOf(':');
    if (colon >= 0) {
      hostPart = authority.substring(0, colon);
      final String portText = authority.substring(colon + 1);
      if (!portText.isEmpty()) {
        if (portText.length() > 5 || !portText.chars().allMatch(c -> c >= '0' && c <= '9'))
          throw reject(LinkRejectedException.Reason.SYNTAX);
        port = Integer.parseInt(portText);
      }
    }
    if (port != -1 && port != 80 && port != 443) throw reject(LinkRejectedException.Reason.SYNTAX);

    final String host = asciiHost(hostPart);

    if (matchesDomain(host, SHORTENER_DOMAINS) || matchesDomain(host, extraShorteners))
      throw reject(LinkRejectedException.Reason.SHORTENER);
    if (matchesDomain(host, blockedDomains)) throw reject(LinkRejectedException.Reason.BLOCKLISTED);

    return rebuild(parsed, scheme, host, port);
  }

  /**
   * Check a link that came from somewhere other than a writer, such as a redirect.
   *
   * @param uri absolute link
   * @return the canonical link
   * @throws LinkRejectedException as for {@link #normalize(String)}
   */
  public NormalizedUrl normalize(final URI uri) throws LinkRejectedException {
    if (uri == null) throw reject(LinkRejectedException.Reason.SYNTAX);
    return normalize(uri.toString());
  }

  /**
   * Whether a host belongs to one of the built in shortening services.
   *
   * @param host host name, ASCII
   * @return true for the service's own domain and any subdomain of it
   */
  public static boolean isShortener(final String host) {
    if (host == null) return false;
    return matchesDomain(stripTrailingDot(host.toLowerCase(Locale.ROOT)), SHORTENER_DOMAINS);
  }

  private static String asciiHost(final String hostPart) throws LinkRejectedException {
    // percent-encoded hosts are legal but nobody needs them, and they hide what the host is
    if (hostPart.isEmpty() || hostPart.indexOf('%') >= 0 || hostPart.indexOf(':') >= 0)
      throw reject(LinkRejectedException.Reason.SYNTAX);

    String host;
    try {
      host = IDN.toASCII(hostPart, IDN.USE_STD3_ASCII_RULES);
    } catch (final IllegalArgumentException e) {
      throw reject(LinkRejectedException.Reason.SYNTAX);
    }
    host = stripTrailingDot(host.toLowerCase(Locale.ROOT));
    if (host.isEmpty() || host.length() > MAX_HOST_LENGTH)
      throw reject(LinkRejectedException.Reason.SYNTAX);

    final String[] labels = host.split("\\.", -1);
    for (final String label : labels) {
      if (!LABEL.matcher(label).matches()) throw reject(LinkRejectedException.Reason.SYNTAX);
    }
    // an IP address in any spelling: we need a name to check against the domain blocklists
    if (labels.length == 1 && host.matches("[0-9]+|0x[0-9a-f]+"))
      throw reject(LinkRejectedException.Reason.SYNTAX);
    if (labels.length > 1 && !TLD.matcher(labels[labels.length - 1]).matches())
      throw reject(LinkRejectedException.Reason.SYNTAX);
    // intranet names, and names that are never on the public internet
    if (labels.length < 2 || matchesDomain(host, SPECIAL_USE_DOMAINS))
      throw reject(LinkRejectedException.Reason.PRIVATE_ADDRESS);
    return host;
  }

  private static NormalizedUrl rebuild(
      final URI parsed, final String scheme, final String host, final int port)
      throws LinkRejectedException {
    final StringBuilder fetch = new StringBuilder(scheme).append("://").append(host);
    final int defaultPort = scheme.equals("https") ? 443 : 80;
    if (port != -1 && port != defaultPort) fetch.append(':').append(port);
    final String rawPath = parsed.getRawPath();
    fetch.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
    if (parsed.getRawQuery() != null) fetch.append('?').append(parsed.getRawQuery());

    try {
      final URI fetchUri = URI.create(new URI(fetch.toString()).toASCIIString());
      final URI href =
          parsed.getRawFragment() == null
              ? fetchUri
              : URI.create(new URI(fetch + "#" + parsed.getRawFragment()).toASCIIString());
      if (href.toString().length() > MAX_LENGTH) throw reject(LinkRejectedException.Reason.SYNTAX);
      return new NormalizedUrl(href, fetchUri, host);
    } catch (final URISyntaxException | IllegalArgumentException e) {
      throw reject(LinkRejectedException.Reason.SYNTAX);
    }
  }

  /** Whether {@code host} is one of {@code domains} or a subdomain of one. */
  static boolean matchesDomain(final String host, final Set<String> domains) {
    if (domains.isEmpty()) return false;
    String candidate = host;
    while (true) {
      if (domains.contains(candidate)) return true;
      final int dot = candidate.indexOf('.');
      if (dot < 0) return false;
      candidate = candidate.substring(dot + 1);
    }
  }

  private static Set<String> domainSet(final List<String> domains, final String property) {
    final Set<String> result = new HashSet<>();
    if (domains == null) return Set.of();
    for (final String entry : domains) {
      if (entry == null) continue;
      String domain = entry.strip().toLowerCase(Locale.ROOT);
      if (domain.startsWith("*.")) domain = domain.substring(2);
      if (domain.startsWith(".")) domain = domain.substring(1);
      domain = stripTrailingDot(domain);
      if (domain.isEmpty()) continue;
      try {
        result.add(IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT));
      } catch (final IllegalArgumentException e) {
        log.warn("Ignoring {} entry that is not a domain name: {}", property, domain);
      }
    }
    return Set.copyOf(result);
  }

  private static String stripTrailingDot(final String host) {
    return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
  }

  private static LinkRejectedException reject(final LinkRejectedException.Reason reason) {
    return LinkRejectedException.of(reason, null);
  }
}
