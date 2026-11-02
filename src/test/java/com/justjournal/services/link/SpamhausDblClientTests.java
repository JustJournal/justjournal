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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpamhausDblClientTests {

  private final Map<String, String> answers = new ConcurrentHashMap<>();
  private final List<String> queries = new CopyOnWriteArrayList<>();
  private SpamhausDblClient client;

  private SpamhausDblClient client(final long timeoutMs) {
    final SpamhausDblClient c =
        new SpamhausDblClient(timeoutMs, 30) {
          @Override
          protected InetAddress[] lookup(final String queryName) throws UnknownHostException {
            queries.add(queryName);
            if (queryName.startsWith("slow.")) {
              try {
                Thread.sleep(5_000);
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            final String answer = answers.get(queryName);
            if (answer == null) throw new UnknownHostException(queryName); // NXDOMAIN
            return new InetAddress[] {InetAddress.getByName(answer)};
          }
        };
    c.setEnabled(true);
    c.setZone("dbl.spamhaus.org");
    return c;
  }

  @BeforeEach
  void setUp() {
    client = client(1_000);
  }

  @AfterEach
  void tearDown() {
    client.close();
  }

  @Test
  void disabled_isUnknownAndAsksNothing() {
    client.setEnabled(false);
    assertFalse(client.enabled());
    assertEquals(Reputation.UNKNOWN, client.check("example.com"));
    assertTrue(queries.isEmpty());
  }

  @Test
  void nxdomain_isClean() {
    assertEquals(Reputation.CLEAN, client.check("example.com"));
    assertEquals(List.of("example.com.dbl.spamhaus.org"), queries);
  }

  @Test
  void listedDomain() {
    answers.put("evil.com.dbl.spamhaus.org", "127.0.1.2");
    assertEquals(Reputation.LISTED, client.check("evil.com"));
  }

  @Test
  void listedDomainCoversItsSubdomains() {
    answers.put("evil.co.uk.dbl.spamhaus.org", "127.0.1.4");
    assertEquals(Reputation.LISTED, client.check("www.evil.co.uk"));
    assertTrue(queries.contains("evil.co.uk.dbl.spamhaus.org"), queries.toString());
    assertTrue(queries.contains("www.evil.co.uk.dbl.spamhaus.org"), queries.toString());
  }

  @Test
  void subdomainListedOnItsOwn() {
    answers.put("phish.example.com.dbl.spamhaus.org", "127.0.1.5");
    assertEquals(Reputation.LISTED, client.check("phish.example.com"));
    assertEquals(Reputation.CLEAN, client.check("www.example.com"));
  }

  @Test
  void abusedLegitimateDomainCodesAreListed() {
    answers.put("hacked.com.dbl.spamhaus.org", "127.0.1.102");
    assertEquals(Reputation.LISTED, client.check("hacked.com"));
  }

  @Test
  void publicResolverRefusal_isUnknown() {
    answers.put("example.com.dbl.spamhaus.org", "127.255.255.254");
    assertEquals(Reputation.UNKNOWN, client.check("example.com"));
  }

  @Test
  void otherErrorCodesAndStrayAnswers_areUnknown() {
    answers.put("a.com.dbl.spamhaus.org", "127.255.255.252");
    answers.put("b.com.dbl.spamhaus.org", "127.0.1.255");
    answers.put("c.com.dbl.spamhaus.org", "93.184.216.34");
    answers.put("d.com.dbl.spamhaus.org", "127.0.0.2");
    for (final String host : List.of("a.com", "b.com", "c.com", "d.com")) {
      assertEquals(Reputation.UNKNOWN, client.check(host), host);
    }
  }

  @Test
  void unknownForOneNameMakesTheVerdictUnknown() {
    answers.put("www.example.com.dbl.spamhaus.org", "127.255.255.254");
    assertEquals(Reputation.UNKNOWN, client.check("www.example.com"));
  }

  @Test
  void verdictsAreCached_unknownIsNot() {
    answers.put("evil.com.dbl.spamhaus.org", "127.0.1.2");
    answers.put("unsure.com.dbl.spamhaus.org", "127.255.255.254");
    assertEquals(Reputation.LISTED, client.check("evil.com"));
    assertEquals(Reputation.CLEAN, client.check("good.com"));
    assertEquals(Reputation.UNKNOWN, client.check("unsure.com"));
    answers.remove("evil.com.dbl.spamhaus.org");
    answers.put("good.com.dbl.spamhaus.org", "127.0.1.2");
    answers.remove("unsure.com.dbl.spamhaus.org");

    assertEquals(Reputation.LISTED, client.check("evil.com"));
    assertEquals(Reputation.CLEAN, client.check("good.com"));
    assertEquals(Reputation.CLEAN, client.check("unsure.com"));
    assertEquals(
        List.of(
            "evil.com.dbl.spamhaus.org",
            "good.com.dbl.spamhaus.org",
            "unsure.com.dbl.spamhaus.org",
            "unsure.com.dbl.spamhaus.org"),
        queries);
  }

  @Test
  void registeredDomainIsCachedForSiblingHosts() {
    assertEquals(Reputation.CLEAN, client.check("a.example.com"));
    assertEquals(Reputation.CLEAN, client.check("b.example.com"));
    // the two names of one host are asked at the same time, so in no particular order
    assertThat(queries)
        .containsExactlyInAnyOrder(
            "example.com.dbl.spamhaus.org",
            "a.example.com.dbl.spamhaus.org",
            "b.example.com.dbl.spamhaus.org");
  }

  @Test
  void timeout_isUnknown() {
    client.close();
    client = client(200);
    final long start = System.currentTimeMillis();
    assertEquals(Reputation.UNKNOWN, client.check("slow.com"));
    assertTrue(System.currentTimeMillis() - start < 2_000);
  }

  @Test
  void zoneIsConfigurable() {
    client.setZone(".key.zen.dq.spamhaus.net.");
    answers.put("evil.com.key.zen.dq.spamhaus.net", "127.0.1.2");
    assertEquals(Reputation.LISTED, client.check("evil.com"));
  }

  @Test
  void blankHost_isUnknown() {
    assertEquals(Reputation.UNKNOWN, client.check(" "));
    assertEquals(Reputation.UNKNOWN, client.check(null));
    assertTrue(queries.isEmpty());
  }

  @Test
  void namesFor_registeredDomainThenHost() {
    assertEquals(List.of("example.com"), SpamhausDblClient.namesFor("example.com"));
    assertEquals(
        List.of("example.co.uk", "www.example.co.uk"),
        SpamhausDblClient.namesFor("www.example.co.uk"));
    assertEquals(
        List.of("example.com", "a.b.example.com"), SpamhausDblClient.namesFor("a.b.example.com"));
    // not under a known public suffix
    assertEquals(List.of("host.notatld"), SpamhausDblClient.namesFor("host.notatld"));
    // a public suffix itself
    assertEquals(List.of("co.uk"), SpamhausDblClient.namesFor("co.uk"));
  }
}
