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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UrlPolicyTests {

  private UrlPolicy policy;

  @BeforeEach
  void setUp() {
    policy = new UrlPolicy();
    policy.setBlockedDomains(List.of("bad.com", "*.evil.org", ".worse.net.", " Mixed.Case.IO "));
    policy.setExtraShorteners(List.of("sho.rt"));
  }

  private LinkRejectedException.Reason reasonFor(final String raw) {
    return assertThrows(LinkRejectedException.class, () -> policy.normalize(raw), raw).reason();
  }

  @Test
  void normalize_lowercasesHostDropsTrailingDotAndKeepsFragmentOnlyInHref() throws Exception {
    final NormalizedUrl url = policy.normalize("  HTTPS://WWW.Example.COM./Path/A?q=1&b=Two#Frag ");
    assertEquals("www.example.com", url.asciiHost());
    assertEquals(URI.create("https://www.example.com/Path/A?q=1&b=Two#Frag"), url.href());
    assertEquals(URI.create("https://www.example.com/Path/A?q=1&b=Two"), url.fetchUri());
  }

  @Test
  void normalize_addsRootPath() throws Exception {
    assertEquals(URI.create("http://example.com/"), policy.normalize("http://example.com").href());
    assertEquals(
        URI.create("http://example.com/?x=1"), policy.normalize("http://example.com?x=1").href());
  }

  @Test
  void normalize_dropsDefaultPortsOnly() throws Exception {
    assertEquals(
        URI.create("https://example.com/x"), policy.normalize("https://example.com:443/x").href());
    assertEquals(
        URI.create("http://example.com/x"), policy.normalize("http://example.com:80/x").href());
    assertEquals(
        URI.create("http://example.com:443/x"), policy.normalize("http://example.com:443/x").href());
    assertEquals(
        URI.create("https://example.com:80/x"), policy.normalize("https://example.com:80/x").href());
    assertEquals(URI.create("http://example.com/"), policy.normalize("http://example.com:/").href());
  }

  @Test
  void normalize_convertsInternationalNamesAndPathsToAscii() throws Exception {
    final NormalizedUrl url = policy.normalize("https://Bücher.de/päth?q=ü#fräg");
    assertEquals("xn--bcher-kva.de", url.asciiHost());
    assertEquals("https://xn--bcher-kva.de/p%C3%A4th?q=%C3%BC#fr%C3%A4g", url.href().toString());
    assertEquals("https://xn--bcher-kva.de/p%C3%A4th?q=%C3%BC", url.fetchUri().toString());
    // punycode typed directly is the same link
    assertEquals(url.href(), policy.normalize("https://xn--bcher-kva.de/p%C3%A4th?q=%C3%BC#fr%C3%A4g").href());
  }

  @Test
  void normalize_mapsIdeographicFullStopToDot() throws Exception {
    assertEquals("example.com", policy.normalize("https://example。com/").asciiHost());
  }

  @Test
  void normalize_keepsPercentEncodingInPath() throws Exception {
    assertEquals(
        "https://example.com/a%20b/%2F",
        policy.normalize("https://example.com/a%20b/%2F").href().toString());
  }

  @Test
  void normalize_acceptsMaximumLabelLength() throws Exception {
    final String label = "a".repeat(63);
    assertEquals(label + ".com", policy.normalize("https://" + label + ".com/").asciiHost());
  }

  @Test
  void normalize_uriOverload() throws Exception {
    assertEquals(
        "example.com", policy.normalize(URI.create("https://EXAMPLE.com/x")).asciiHost());
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        assertThrows(LinkRejectedException.class, () -> policy.normalize((URI) null)).reason());
    assertEquals(
        LinkRejectedException.Reason.SHORTENER,
        assertThrows(
                LinkRejectedException.class,
                () -> policy.normalize(URI.create("https://bit.ly/x")))
            .reason());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "javascript:alert(1)",
        "data:text/html,hi",
        "ftp://example.com/",
        "file:///etc/passwd",
        "gopher://example.com/",
        "mailto:someone@example.com",
        "http:example.com",
        "//example.com/",
        "example.com",
        "/relative/path",
        "https://",
        "https:///path",
        "https://user:pass@example.com/",
        "https://user@example.com/",
        "https://example.com@evil.com/",
        "https://exa mple.com/",
        "https://example.com/a b",
        "https://example.com/\ta",
        "https://example.com/\u0000",
        "https://example.com/a​b",
        "https://exa‮mple.com/",
        "https://example.com/ ",
        "https://example.com:8080/",
        "https://example.com:0/",
        "https://example.com:22/",
        "https://example.com:99999/",
        "https://example.com:44a/",
        "https://example.com:80:80/",
        "http://127.0.0.1/",
        "http://127.0.0.1:80/",
        "http://93.184.216.34/",
        "http://127.1/",
        "http://2130706433/",
        "http://0x7f000001/",
        "http://017700000001/",
        "http://0x7f.1/",
        "http://0x7f.0x0.0x0.0x1/",
        "http://0177.0.0.1/",
        "http://10.0.0.1.1/",
        "http://[::1]/",
        "http://[::ffff:127.0.0.1]/",
        "http://[2606:2800:220:1::1]/",
        "http://::1/",
        "http://%31%32%37.0.0.1/",
        "http://exam%70le.com/",
        "http://a..example.com/",
        "http://.example.com/",
        "http://-example.com/",
        "http://example-.com/",
        "http://ex_ample.com/",
        "http://example.com\\@evil.com/",
        "http://example.c0m/",
        "http://example.x/",
      })
  void malformedLinksAreSyntaxErrors(final String raw) {
    assertEquals(LinkRejectedException.Reason.SYNTAX, reasonFor(raw));
  }

  @Test
  void nullIsASyntaxError() {
    assertEquals(LinkRejectedException.Reason.SYNTAX, reasonFor(null));
  }

  @Test
  void lengthLimits() {
    assertEquals(
        LinkRejectedException.Reason.SYNTAX, reasonFor("https://" + "a".repeat(64) + ".com/"));
    final String longHost = ("a".repeat(60) + ".").repeat(5) + "com"; // 308 characters
    assertEquals(LinkRejectedException.Reason.SYNTAX, reasonFor("https://" + longHost + "/"));
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        reasonFor("https://example.com/" + "a".repeat(UrlPolicy.MAX_LENGTH)));
    // fits as typed, but not once encoded
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        reasonFor("https://example.com/" + "ü".repeat(900)));
  }

  @Test
  void longestLinkAccepted() throws Exception {
    final String prefix = "https://example.com/";
    final String raw = prefix + "a".repeat(UrlPolicy.MAX_LENGTH - prefix.length());
    assertEquals(UrlPolicy.MAX_LENGTH, policy.normalize(raw).href().toString().length());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost/",
        "http://LOCALHOST./",
        "http://localhost:80/",
        "http://app.localhost/",
        "http://printer.local/",
        "http://db.internal/",
        "http://nas.lan/",
        "http://router.home.arpa/",
        "http://abcdefghijklmnop.onion/",
        "http://site.test/",
        "http://site.invalid/",
        "http://www.example/",
        "http://intranet/",
      })
  void privateNamesRejected(final String raw) {
    assertEquals(LinkRejectedException.Reason.PRIVATE_ADDRESS, reasonFor(raw));
  }

  @Test
  void specialUseNamesMatchOnLabelBoundary() throws Exception {
    // "arpa" itself is a public TLD; only home.arpa is reserved
    assertEquals("notlocal.com", policy.normalize("http://notlocal.com/").asciiHost());
    assertEquals("mytest.org", policy.normalize("http://mytest.org/").asciiHost());
    assertEquals("example.com", policy.normalize("http://example.com/").asciiHost());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://bit.ly/abc",
        "https://BIT.LY/abc",
        "https://bit.ly./abc",
        "http://tinyurl.com/abc",
        "https://www.tinyurl.com/abc",
        "https://custom.bitly.com/abc",
        "https://t.co/abc",
        "https://sho.rt/abc",
        "https://www.sho.rt/abc",
      })
  void shortenersRejected(final String raw) {
    final LinkRejectedException e =
        assertThrows(LinkRejectedException.class, () -> policy.normalize(raw));
    assertEquals(LinkRejectedException.Reason.SHORTENER, e.reason());
    assertEquals(LinkRejectedException.SHORTENER_MESSAGE, e.getMessage());
  }

  @Test
  void shortenerLookAlikesAllowed() throws Exception {
    assertEquals("notbit.ly", policy.normalize("https://notbit.ly/").asciiHost());
    assertEquals("bit.ly.example.com", policy.normalize("https://bit.ly.example.com/").asciiHost());
  }

  @Test
  void isShortener_staticApiCoversBuiltInListOnly() {
    assertTrue(UrlPolicy.isShortener("bit.ly"));
    assertTrue(UrlPolicy.isShortener("Www.Bit.Ly."));
    assertFalse(UrlPolicy.isShortener("notbit.ly"));
    assertFalse(UrlPolicy.isShortener("sho.rt"));
    assertFalse(UrlPolicy.isShortener(null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://bad.com/",
        "https://BAD.com./x",
        "https://x.bad.com/",
        "https://a.b.bad.com/",
        "https://evil.org/",
        "https://www.evil.org/",
        "https://worse.net/",
        "https://mixed.case.io/",
        "https://sub.mixed.case.io/",
      })
  void blockedDomainsRejectedWithSubdomains(final String raw) {
    final LinkRejectedException e =
        assertThrows(LinkRejectedException.class, () -> policy.normalize(raw));
    assertEquals(LinkRejectedException.Reason.BLOCKLISTED, e.reason());
    assertEquals(LinkRejectedException.UNSAFE_MESSAGE, e.getMessage());
  }

  @Test
  void blockedDomainsMatchOnLabelBoundary() throws Exception {
    assertEquals("notbad.com", policy.normalize("https://notbad.com/").asciiHost());
    assertEquals("bad.com.example.org", policy.normalize("https://bad.com.example.org/").asciiHost());
    assertEquals("case.io", policy.normalize("https://case.io/").asciiHost());
  }

  @Test
  void internationalBlockedDomainMatchesItsPunycode() {
    policy.setBlockedDomains(List.of("bücher.de"));
    assertEquals(LinkRejectedException.Reason.BLOCKLISTED, reasonFor("https://xn--bcher-kva.de/"));
    assertEquals(LinkRejectedException.Reason.BLOCKLISTED, reasonFor("https://www.BÜCHER.de/"));
  }

  @Test
  void emptyAndNullListsBlockNothing() throws Exception {
    policy.setBlockedDomains(null);
    policy.setExtraShorteners(List.of("", " "));
    assertEquals("bad.com", policy.normalize("https://bad.com/").asciiHost());
  }

  @Test
  void messages() {
    assertEquals(
        "Link URL is not valid.",
        assertThrows(LinkRejectedException.class, () -> policy.normalize("ftp://x.com/"))
            .getMessage());
    assertEquals(
        "Links must point to a public web site.",
        assertThrows(LinkRejectedException.class, () -> policy.normalize("http://localhost/"))
            .getMessage());
  }

  /** The lists come from properties, as a comma separated value or a YAML list; empty by default. */
  @Test
  void springBindsDomainLists() {
    new ApplicationContextRunner()
        .withUserConfiguration(UrlPolicy.class)
        .withPropertyValues(
            "link-safety.blocked-domains=bad.com, evil.org", "link-safety.extra-shorteners=sho.rt")
        .run(
            context -> {
              final UrlPolicy bound = context.getBean(UrlPolicy.class);
              assertThrows(LinkRejectedException.class, () -> bound.normalize("https://x.evil.org/"));
              assertThrows(LinkRejectedException.class, () -> bound.normalize("https://bad.com/"));
              assertThrows(LinkRejectedException.class, () -> bound.normalize("https://sho.rt/"));
              assertThat(bound.normalize("https://good.com/").asciiHost()).isEqualTo("good.com");
            });
    // a YAML sequence arrives as indexed properties
    new ApplicationContextRunner()
        .withUserConfiguration(UrlPolicy.class)
        .withPropertyValues(
            "link-safety.blocked-domains[0]=bad.com",
            "link-safety.blocked-domains[1]=evil.org",
            "link-safety.extra-shorteners[0]=sho.rt")
        .run(
            context -> {
              final UrlPolicy bound = context.getBean(UrlPolicy.class);
              assertThrows(LinkRejectedException.class, () -> bound.normalize("https://x.evil.org/"));
              assertThrows(LinkRejectedException.class, () -> bound.normalize("https://bad.com/"));
              assertThrows(LinkRejectedException.class, () -> bound.normalize("https://sho.rt/"));
            });
    new ApplicationContextRunner()
        .withUserConfiguration(UrlPolicy.class)
        .run(
            context ->
                assertThat(context.getBean(UrlPolicy.class).normalize("https://bad.com/").asciiHost())
                    .isEqualTo("bad.com"));
  }
}
