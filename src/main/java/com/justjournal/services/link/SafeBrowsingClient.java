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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Asks Google Safe Browsing (the v4 Lookup API) whether a link is known malware, phishing or
 * unwanted software.
 *
 * <p>Off unless {@code link-safety.safe-browsing.api-key} is set. The key travels in the request
 * URL, so neither the URL nor exception messages (which repeat it) are ever logged. The Lookup API
 * sends the links themselves to Google and is meant for non-commercial use.
 */
@Slf4j
@Component
public class SafeBrowsingClient {

  static final String ENDPOINT = "https://safebrowsing.googleapis.com/v4/threatMatches:find";
  static final String CLIENT_VERSION = "1.0";
  static final List<String> THREAT_TYPES =
      List.of(
          "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION");

  @Setter
  @Value("${link-safety.safe-browsing.api-key:}")
  private String apiKey;

  @Setter
  @Value("${link-safety.safe-browsing.client-id:justjournal}")
  private String clientId = "justjournal";

  private final RestTemplate restTemplate;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Cache<String, Reputation> cache;

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public SafeBrowsingClient(
      final RestTemplateBuilder builder,
      @Value("${link-safety.safe-browsing.timeout-ms:3000}") final long timeoutMs,
      @Value("${link-safety.cache-minutes:30}") final long cacheMinutes) {
    this(
        builder
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .readTimeout(Duration.ofMillis(timeoutMs))
            .build(),
        cacheMinutes);
  }

  SafeBrowsingClient(final RestTemplate restTemplate, final long cacheMinutes) {
    this.restTemplate = restTemplate;
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(cacheMinutes, TimeUnit.MINUTES)
            .build();
  }

  /** Whether lookups are made at all; when off, every check is {@link Reputation#UNKNOWN}. */
  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }

  /**
   * Look a link up.
   *
   * @param url absolute link, as {@link NormalizedUrl#fetchUri()} has it
   * @return LISTED if Google lists it, CLEAN if not, UNKNOWN on any error or when turned off
   */
  public Reputation check(final String url) {
    if (!enabled() || url == null || url.isBlank()) return Reputation.UNKNOWN;
    final Reputation cached = cache.getIfPresent(url);
    if (cached != null) return cached;

    final String response;
    try {
      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      response =
          restTemplate.postForObject(
              ENDPOINT + "?key={key}",
              new HttpEntity<>(mapper.writeValueAsString(body(url)), headers),
              String.class,
              apiKey.strip());
    } catch (final RestClientResponseException e) {
      log.warn("Safe Browsing lookup failed with status {}", e.getStatusCode().value());
      return Reputation.UNKNOWN;
    } catch (final RestClientException | IOException e) {
      // the message repeats the request URL, and with it the key
      log.warn("Safe Browsing lookup failed: {}", e.getClass().getSimpleName());
      return Reputation.UNKNOWN;
    }

    final Reputation verdict = verdictOf(response);
    if (verdict != Reputation.UNKNOWN) cache.put(url, verdict);
    return verdict;
  }

  private ObjectNode body(final String url) {
    final ObjectNode body = mapper.createObjectNode();
    final ObjectNode client = body.putObject("client");
    client.put("clientId", clientId);
    client.put("clientVersion", CLIENT_VERSION);
    final ObjectNode threatInfo = body.putObject("threatInfo");
    final ArrayNode threatTypes = threatInfo.putArray("threatTypes");
    THREAT_TYPES.forEach(threatTypes::add);
    threatInfo.putArray("platformTypes").add("ANY_PLATFORM");
    threatInfo.putArray("threatEntryTypes").add("URL");
    threatInfo.putArray("threatEntries").addObject().put("url", url);
    return body;
  }

  private Reputation verdictOf(final String response) {
    if (response == null || response.isBlank()) return Reputation.UNKNOWN;
    try {
      final JsonNode root = mapper.readTree(response);
      if (root == null || !root.isObject()) return Reputation.UNKNOWN;
      final JsonNode matches = root.path("matches");
      return matches.isArray() && !matches.isEmpty() ? Reputation.LISTED : Reputation.CLEAN;
    } catch (final IOException e) {
      log.warn("Safe Browsing gave an answer that is not JSON");
      return Reputation.UNKNOWN;
    }
  }
}
