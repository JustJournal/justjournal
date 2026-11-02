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
package com.justjournal.services.activitypub;

import com.fasterxml.jackson.databind.JsonNode;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import com.justjournal.services.link.UrlPolicy;
import java.io.IOException;
import java.net.URI;
import java.security.PrivateKey;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fetches ActivityPub documents, such as actors and keys, from other servers. The fetch goes
 * through {@link SafeHttpFetcher}, so it reaches only public addresses and is capped in size and
 * time. A signed fetch is signed by the instance actor, since servers in Mastodon's "authorized
 * fetch" mode answer only signed requests.
 */
@Component
public class ActivityPubFetcher {

  static final Set<String> ACCEPT_TYPES =
      Set.of("application/activity+json", "application/ld+json");
  static final String ACCEPT =
      "application/activity+json, "
          + "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";

  private final SafeHttpFetcher fetcher;
  private final UrlPolicy urlPolicy;
  private final ActorKeyService actorKeyService;
  private final Clock clock;

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public ActivityPubFetcher(
      final SafeHttpFetcher fetcher,
      final UrlPolicy urlPolicy,
      final ActorKeyService actorKeyService) {
    this(fetcher, urlPolicy, actorKeyService, Clock.systemUTC());
  }

  ActivityPubFetcher(
      final SafeHttpFetcher fetcher,
      final UrlPolicy urlPolicy,
      final ActorKeyService actorKeyService,
      final Clock clock) {
    this.fetcher = fetcher;
    this.urlPolicy = urlPolicy;
    this.actorKeyService = actorKeyService;
    this.clock = clock;
  }

  /**
   * An address checked as a link would be: refused for private hosts, odd schemes and the like.
   *
   * @throws LinkRejectedException if it may not be fetched
   */
  public NormalizedUrl normalize(final String url) throws LinkRejectedException {
    return urlPolicy.normalize(url);
  }

  /**
   * GET a document with a request the instance actor signs, following redirects.
   *
   * @param url an address from {@link #normalize}
   * @param maxBytes largest body to accept
   * @return where the fetch ended up and what it got; the caller checks the final host
   * @throws LinkRejectedException if a redirect or a connection leads somewhere it may not
   * @throws IOException on failures; a {@link com.justjournal.services.link.FetchException}
   *     carries the HTTP status
   */
  public SafeHttpFetcher.Result signedGet(final NormalizedUrl url, final int maxBytes)
      throws IOException, LinkRejectedException {
    return fetcher.fetch(url, ACCEPT_TYPES, maxBytes, signedHeaders());
  }

  /** GET a document as {@link #signedGet} does, without a signature. */
  public SafeHttpFetcher.Result unsignedGet(final NormalizedUrl url, final int maxBytes)
      throws IOException, LinkRejectedException {
    return fetcher.fetch(url, ACCEPT_TYPES, maxBytes, target -> Map.of("Accept", ACCEPT));
  }

  /** Headers for a GET signed by the instance actor, worked out again for each redirect. */
  private SafeHttpFetcher.RequestHeaders signedHeaders() {
    final String actorId = actorKeyService.instanceActorId();
    final PrivateKey privateKey = actorKeyService.privateKeyFor(actorId);
    return target -> {
      final Map<String, String> headers =
          new LinkedHashMap<>(
              HttpSignatures.sign(
                  "GET",
                  target,
                  null,
                  ActorKeyService.keyId(actorId),
                  privateKey,
                  clock.instant()));
      headers.put("Accept", ACCEPT);
      return headers;
    };
  }

  /** Whether an address is on the given host, which is lower case. */
  static boolean sameHost(final URI uri, final String host) {
    return uri != null
        && uri.getHost() != null
        && uri.getHost().toLowerCase(Locale.ROOT).equals(host);
  }

  /** A string field of a JSON object, or null when it is missing or not a string. */
  static String text(final JsonNode node, final String field) {
    final JsonNode value = node == null ? null : node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }
}
