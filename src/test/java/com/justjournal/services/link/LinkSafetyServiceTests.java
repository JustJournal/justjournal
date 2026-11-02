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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;

class LinkSafetyServiceTests {

  private SafeDnsResolver resolver;
  private SpamhausDblClient dbl;
  private SafeBrowsingClient safeBrowsing;
  private LinkSafetyService service;

  @BeforeEach
  void setUp() throws UnknownHostException {
    final UrlPolicy policy = new UrlPolicy();
    policy.setBlockedDomains(List.of("blocked.com"));
    resolver = mock(SafeDnsResolver.class);
    dbl = mock(SpamhausDblClient.class);
    safeBrowsing = mock(SafeBrowsingClient.class);
    when(resolver.resolve(anyString()))
        .thenReturn(new InetAddress[] {InetAddress.getByName("93.184.216.34")});
    when(dbl.enabled()).thenReturn(true);
    when(safeBrowsing.enabled()).thenReturn(true);
    when(dbl.check(anyString())).thenReturn(Reputation.CLEAN);
    when(safeBrowsing.check(anyString())).thenReturn(Reputation.CLEAN);
    service = new LinkSafetyService(policy, resolver, dbl, safeBrowsing);
  }

  @AfterEach
  void tearDown() {
    service.close();
  }

  private LinkRejectedException rejection(final String raw) {
    return assertThrows(LinkRejectedException.class, () -> service.checkForSave(raw));
  }

  @Test
  void checkForSave_runsEveryCheckInOrderAndReturnsCanonicalLink() throws Exception {
    final NormalizedUrl url = service.checkForSave("HTTPS://WWW.Example.com/a#top");
    assertEquals(URI.create("https://www.example.com/a#top"), url.href());

    final InOrder order = inOrder(resolver, dbl, safeBrowsing);
    order.verify(resolver).resolve("www.example.com");
    verify(dbl).check("www.example.com");
    verify(safeBrowsing).check("https://www.example.com/a"); // no fragment
  }

  @Test
  void policyFailure_stopsBeforeDns() {
    final LinkRejectedException e = rejection("https://bit.ly/abc");
    assertEquals(LinkRejectedException.Reason.SHORTENER, e.reason());
    assertEquals(LinkRejectedException.SHORTENER_MESSAGE, e.getMessage());
    verifyNoInteractions(resolver, dbl, safeBrowsing);

    assertEquals(LinkRejectedException.Reason.BLOCKLISTED, rejection("https://x.blocked.com/").reason());
    assertEquals(LinkRejectedException.Reason.SYNTAX, rejection("ftp://example.com/").reason());
    assertEquals(LinkRejectedException.Reason.PRIVATE_ADDRESS, rejection("http://localhost/").reason());
    verifyNoInteractions(resolver, dbl, safeBrowsing);
  }

  @Test
  void privateAddress_isRefusedBeforeReputation() throws Exception {
    when(resolver.resolve("intranet.example.com"))
        .thenThrow(new NonPublicAddressException("intranet.example.com"));
    final LinkRejectedException e = rejection("https://intranet.example.com/");
    assertEquals(LinkRejectedException.Reason.PRIVATE_ADDRESS, e.reason());
    assertEquals("Links must point to a public web site.", e.getMessage());
    verify(dbl, never()).check(anyString());
    verify(safeBrowsing, never()).check(anyString());
  }

  @Test
  void missingDomain_namesTheHost() throws Exception {
    when(resolver.resolve("missing.example.com"))
        .thenThrow(new UnknownHostException("missing.example.com"));
    final LinkRejectedException e = rejection("https://Missing.Example.com/");
    assertEquals(LinkRejectedException.Reason.DNS, e.reason());
    assertEquals("The domain missing.example.com could not be found.", e.getMessage());
    verify(dbl, never()).check(anyString());
  }

  @Test
  void dblListing_isRefused() {
    when(dbl.check("evil.com")).thenReturn(Reputation.LISTED);
    final LinkRejectedException e = rejection("https://evil.com/");
    assertEquals(LinkRejectedException.Reason.DBL, e.reason());
    assertEquals("This link can't be added because the site is listed as unsafe.", e.getMessage());
  }

  @Test
  void safeBrowsingListing_isRefused() {
    when(safeBrowsing.check("https://evil.com/x")).thenReturn(Reputation.LISTED);
    final LinkRejectedException e = rejection("https://evil.com/x");
    assertEquals(LinkRejectedException.Reason.SAFE_BROWSING, e.reason());
    assertEquals("This link can't be added because the site is listed as unsafe.", e.getMessage());
  }

  @Test
  void listingWinsOverUnknown() {
    when(dbl.check(anyString())).thenReturn(Reputation.UNKNOWN);
    when(safeBrowsing.check(anyString())).thenReturn(Reputation.LISTED);
    assertEquals(LinkRejectedException.Reason.SAFE_BROWSING, rejection("https://evil.com/").reason());
  }

  @Test
  void unknown_passesWhenFailOpen() throws Exception {
    when(dbl.check(anyString())).thenReturn(Reputation.UNKNOWN);
    when(safeBrowsing.check(anyString())).thenReturn(Reputation.UNKNOWN);
    service.setFailClosed(false);
    assertEquals("example.com", service.checkForSave("https://example.com/").asciiHost());
  }

  @Test
  void unknownDbl_isRefusedWhenFailClosed() {
    service.setFailClosed(true);
    when(dbl.check(anyString())).thenReturn(Reputation.UNKNOWN);
    final LinkRejectedException e = rejection("https://example.com/");
    assertEquals(LinkRejectedException.Reason.DBL, e.reason());
    assertEquals(LinkRejectedException.UNSAFE_MESSAGE, e.getMessage());
  }

  @Test
  void unknownSafeBrowsing_isRefusedWhenFailClosed() {
    service.setFailClosed(true);
    when(safeBrowsing.check(anyString())).thenReturn(Reputation.UNKNOWN);
    assertEquals(LinkRejectedException.Reason.SAFE_BROWSING, rejection("https://example.com/").reason());
  }

  @Test
  void cleanPassesWhenFailClosed() throws Exception {
    service.setFailClosed(true);
    assertEquals("example.com", service.checkForSave("https://example.com/").asciiHost());
  }

  @Test
  void disabledServices_areSkippedEvenWhenFailClosed() throws Exception {
    service.setFailClosed(true);
    when(dbl.enabled()).thenReturn(false);
    when(safeBrowsing.enabled()).thenReturn(false);
    assertEquals("example.com", service.checkForSave("https://example.com/").asciiHost());
    verify(dbl, never()).check(anyString());
    verify(safeBrowsing, never()).check(anyString());
  }

  @Test
  void failingService_countsAsUnknown() throws Exception {
    when(dbl.check(anyString())).thenThrow(new IllegalStateException("boom"));
    assertEquals("example.com", service.checkForSave("https://example.com/").asciiHost());
    service.setFailClosed(true);
    assertEquals(LinkRejectedException.Reason.DBL, rejection("https://example.com/").reason());
  }

  @Test
  void reputationServicesRunAtTheSameTime() throws Exception {
    when(dbl.check(anyString()))
        .thenAnswer(
            inv -> {
              Thread.sleep(600);
              return Reputation.CLEAN;
            });
    when(safeBrowsing.check(anyString()))
        .thenAnswer(
            inv -> {
              Thread.sleep(600);
              return Reputation.CLEAN;
            });
    final long start = System.currentTimeMillis();
    service.checkForSave("https://example.com/");
    final long elapsed = System.currentTimeMillis() - start;
    assertTrue(elapsed < 1_100, "took " + elapsed + "ms");
  }

  @Test
  void checkHop_appliesPolicyAndReputationButNotDns() throws Exception {
    final NormalizedUrl url = service.checkHop(URI.create("https://Next.example.com/b#frag"));
    assertEquals(URI.create("https://next.example.com/b"), url.fetchUri());
    verify(dbl).check("next.example.com");
    verify(safeBrowsing).check("https://next.example.com/b");
    verifyNoInteractions(resolver);
  }

  /** Each class has a constructor for tests; Spring must still find the right one everywhere. */
  @Test
  void springWiresThePackage() {
    new ApplicationContextRunner()
        .withUserConfiguration(
            UrlPolicy.class,
            SafeDnsResolver.class,
            SpamhausDblClient.class,
            SafeBrowsingClient.class,
            LinkSafetyService.class,
            SafeHttpFetcher.class,
            OpenGraphParser.class)
        .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LinkSafetyService.class);
              assertThat(context).hasSingleBean(SafeHttpFetcher.class);
              assertThat(context).hasSingleBean(OpenGraphParser.class);
              assertFalse(context.getBean(SpamhausDblClient.class).enabled());
              assertFalse(context.getBean(SafeBrowsingClient.class).enabled());
            });
  }

  @Test
  void checkHop_refusesBadTargets() {
    assertEquals(
        LinkRejectedException.Reason.SHORTENER,
        assertThrows(
                LinkRejectedException.class, () -> service.checkHop(URI.create("https://t.co/x")))
            .reason());
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        assertThrows(
                LinkRejectedException.class,
                () -> service.checkHop(URI.create("http://169.254.169.254/latest/meta-data/")))
            .reason());
    when(dbl.check("evil.com")).thenReturn(Reputation.LISTED);
    assertEquals(
        LinkRejectedException.Reason.DBL,
        assertThrows(
                LinkRejectedException.class, () -> service.checkHop(URI.create("https://evil.com/")))
            .reason());
    verifyNoInteractions(resolver);
  }
}
