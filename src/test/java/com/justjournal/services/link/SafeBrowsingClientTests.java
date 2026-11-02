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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SafeBrowsingClientTests {

  private static final String URL = "https://example.com/page?x=1";
  private static final String ENDPOINT_WITH_KEY = SafeBrowsingClient.ENDPOINT + "?key=test-key";
  private static final String MATCH =
      "{\"matches\":[{\"threatType\":\"MALWARE\",\"platformType\":\"ANY_PLATFORM\","
          + "\"threat\":{\"url\":\"https://example.com/page?x=1\"},\"cacheDuration\":\"300s\","
          + "\"threatEntryType\":\"URL\"}]}";

  private MockRestServiceServer server;
  private SafeBrowsingClient client;

  @BeforeEach
  void setUp() {
    final RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new SafeBrowsingClient(restTemplate, 30);
    client.setApiKey("test-key");
    client.setClientId("justjournal");
  }

  @Test
  void springBuildsItFromTheRestTemplateBuilder() {
    new ApplicationContextRunner()
        .withUserConfiguration(SafeBrowsingClient.class)
        .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(SafeBrowsingClient.class);
              assertFalse(context.getBean(SafeBrowsingClient.class).enabled());
            });
  }

  @Test
  void match_isListed() {
    server
        .expect(once(), requestTo(ENDPOINT_WITH_KEY))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.client.clientId").value("justjournal"))
        .andExpect(jsonPath("$.client.clientVersion").value(SafeBrowsingClient.CLIENT_VERSION))
        .andExpect(
            jsonPath("$.threatInfo.threatTypes")
                .value(
                    org.hamcrest.Matchers.contains(
                        "MALWARE",
                        "SOCIAL_ENGINEERING",
                        "UNWANTED_SOFTWARE",
                        "POTENTIALLY_HARMFUL_APPLICATION")))
        .andExpect(jsonPath("$.threatInfo.platformTypes[0]").value("ANY_PLATFORM"))
        .andExpect(jsonPath("$.threatInfo.threatEntryTypes[0]").value("URL"))
        .andExpect(jsonPath("$.threatInfo.threatEntries[0].url").value(URL))
        .andRespond(withSuccess(MATCH, MediaType.APPLICATION_JSON));

    assertEquals(Reputation.LISTED, client.check(URL));
    server.verify();
  }

  @Test
  void emptyAnswer_isCleanAndCached() {
    server
        .expect(once(), requestTo(ENDPOINT_WITH_KEY))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertEquals(Reputation.CLEAN, client.check(URL));
    assertEquals(Reputation.CLEAN, client.check(URL)); // from the cache
    server.verify();
  }

  @Test
  void listedVerdictIsCached() {
    server
        .expect(once(), requestTo(ENDPOINT_WITH_KEY))
        .andRespond(withSuccess(MATCH, MediaType.APPLICATION_JSON));

    assertEquals(Reputation.LISTED, client.check(URL));
    assertEquals(Reputation.LISTED, client.check(URL));
    server.verify();
  }

  @Test
  void emptyMatchesArray_isClean() {
    server
        .expect(once(), requestTo(ENDPOINT_WITH_KEY))
        .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));
    assertEquals(Reputation.CLEAN, client.check(URL));
  }

  @Test
  void serverError_isUnknownAndNotCached() {
    server.expect(twice(), requestTo(ENDPOINT_WITH_KEY)).andRespond(withServerError());

    assertEquals(Reputation.UNKNOWN, client.check(URL));
    assertEquals(Reputation.UNKNOWN, client.check(URL));
    server.verify();
  }

  @Test
  void badKeyOrQuota_isUnknown() {
    server
        .expect(once(), requestTo(ENDPOINT_WITH_KEY))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    assertEquals(Reputation.UNKNOWN, client.check(URL));
  }

  @Test
  void connectionFailure_isUnknown() {
    server
        .expect(once(), requestTo(ENDPOINT_WITH_KEY))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });
    assertEquals(Reputation.UNKNOWN, client.check(URL));
  }

  @Test
  void notJson_isUnknown() {
    server
        .expect(once(), requestTo(ENDPOINT_WITH_KEY))
        .andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));
    assertEquals(Reputation.UNKNOWN, client.check(URL));
  }

  @Test
  void blankKey_sendsNothing() {
    client.setApiKey("  ");
    assertFalse(client.enabled());
    assertEquals(Reputation.UNKNOWN, client.check(URL));
    client.setApiKey(null);
    assertEquals(Reputation.UNKNOWN, client.check(URL));
    server.verify(); // no request expected, none made
  }

  @Test
  void enabledWithKey() {
    assertTrue(client.enabled());
  }
}
