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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.services.activitypub.HttpSignatureException.Reason;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import com.justjournal.services.link.UrlPolicy;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RemoteKeyResolverTests {

  private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
  private static final String INSTANCE = "https://www.justjournal.com/actor";
  private static final String ALICE = "https://remote.social/users/alice";
  private static final String ALICE_KEY = ALICE + "#main-key";
  private static final URI ALICE_URI = URI.create(ALICE);

  private final ObjectMapper mapper = new ObjectMapper();
  private SafeHttpFetcher fetcher;
  private ActorKeyService actorKeyService;
  private RemoteKeyResolver resolver;

  @BeforeEach
  void setUp() throws Exception {
    fetcher = mock(SafeHttpFetcher.class);
    actorKeyService = mock(ActorKeyService.class);
    when(actorKeyService.instanceActorId()).thenReturn(INSTANCE);
    when(actorKeyService.privateKeyFor(INSTANCE)).thenReturn(TestKeys.privateKey());
    resolver =
        new RemoteKeyResolver(
            fetcher, new UrlPolicy(), actorKeyService, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private ObjectNode actor(final String id) {
    final ObjectNode actor = mapper.createObjectNode();
    actor.putArray("@context")
        .add("https://www.w3.org/ns/activitystreams")
        .add("https://w3id.org/security/v1");
    actor.put("id", id);
    actor.put("type", "Person");
    actor.putObject("publicKey").setAll(key(id + "#main-key", id));
    return actor;
  }

  private ObjectNode key(final String id, final String owner) {
    final ObjectNode key = mapper.createObjectNode();
    key.put("id", id);
    key.put("owner", owner);
    key.put("publicKeyPem", TestKeys.PUBLIC_PEM);
    return key;
  }

  private void serve(final URI finalUri, final Object document) throws Exception {
    final byte[] body =
        document instanceof String text
            ? text.getBytes(StandardCharsets.UTF_8)
            : mapper.writeValueAsBytes(document);
    when(fetcher.fetch(any(), any(), anyInt(), any()))
        .thenReturn(
            new SafeHttpFetcher.Result(
                finalUri, "application/activity+json", StandardCharsets.UTF_8, body));
  }

  private Reason failure(final String keyId) {
    return assertThrows(HttpSignatureException.class, () -> resolver.resolve(keyId)).reason();
  }

  @Test
  void resolve_readsTheKeyFromTheActor() throws Exception {
    serve(ALICE_URI, actor(ALICE));

    assertEquals(
        new RemoteKey(ALICE_KEY, ALICE, TestKeys.PUBLIC_PEM, NOW.toEpochMilli()),
        resolver.resolve(ALICE_KEY));
  }

  @Test
  void resolve_fetchesWithoutTheFragment_asActivityJson_signedByTheInstanceActor()
      throws Exception {
    serve(ALICE_URI, actor(ALICE));
    resolver.resolve(ALICE_KEY);

    final ArgumentCaptor<NormalizedUrl> url = ArgumentCaptor.forClass(NormalizedUrl.class);
    final ArgumentCaptor<SafeHttpFetcher.RequestHeaders> headers =
        ArgumentCaptor.forClass(SafeHttpFetcher.RequestHeaders.class);
    verify(fetcher)
        .fetch(url.capture(), eq(RemoteKeyResolver.ACCEPT_TYPES), anyInt(), headers.capture());
    assertEquals(ALICE_URI, url.getValue().fetchUri());

    // each hop is signed for its own address
    final URI hop = URI.create("https://remote.social/users/alice?x=1");
    final Map<String, String> sent = headers.getValue().forRequest(hop);
    assertEquals(
        "application/activity+json, "
            + "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"",
        sent.get("Accept"));
    assertEquals("remote.social", sent.get("Host"));
    assertEquals("Mon, 02 Nov 2026 12:00:00 GMT", sent.get("Date"));
    assertTrue(!sent.containsKey("Digest"));
    final Map<String, String> params = HttpSignatures.parseSignature(sent.get("Signature"));
    assertEquals(INSTANCE + "#main-key", params.get("keyId"));
    assertEquals("(request-target) host date", params.get("headers"));
    final String signingString =
        "(request-target): get /users/alice?x=1\n"
            + "host: remote.social\n"
            + "date: Mon, 02 Nov 2026 12:00:00 GMT";
    assertTrue(
        HttpSignatures.rsaVerify(
            TestKeys.publicKey(),
            signingString,
            Base64.getDecoder().decode(params.get("signature"))));
  }

  @Test
  void resolve_picksTheNamedKeyFromAList() throws Exception {
    final ObjectNode actor = actor(ALICE);
    final ArrayNode keys = mapper.createArrayNode();
    final ObjectNode other = key(ALICE + "#other-key", ALICE);
    other.put("publicKeyPem", "not this one");
    keys.add(other).add(key(ALICE_KEY, ALICE));
    actor.set("publicKey", keys);
    serve(ALICE_URI, actor);

    assertEquals(TestKeys.PUBLIC_PEM, resolver.resolve(ALICE_KEY).publicKeyPem());
  }

  @Test
  void resolve_readsABareKeyOwnedOnTheSameHost() throws Exception {
    final String keyId = "https://remote.social/users/alice/main-key";
    serve(URI.create(keyId), key(keyId, ALICE));

    final RemoteKey key = resolver.resolve(keyId);
    assertEquals(ALICE, key.owner());
    assertEquals(keyId, key.keyId());
  }

  @Test
  void resolve_refusesAKeyOwnedBySomeoneElse() throws Exception {
    final ObjectNode actor = actor(ALICE);
    ((ObjectNode) actor.get("publicKey")).put("owner", "https://remote.social/users/mallory");
    serve(ALICE_URI, actor);
    assertEquals(Reason.KEY_MISMATCH, failure(ALICE_KEY));
  }

  @Test
  void resolve_refusesAnActorWithoutTheKeyAskedFor() throws Exception {
    serve(ALICE_URI, actor(ALICE));
    assertEquals(Reason.KEY_MISMATCH, failure(ALICE + "#another-key"));
  }

  @Test
  void resolve_refusesAnActorOnAnotherHost() throws Exception {
    // remote.social serves a document claiming to be an actor elsewhere
    final ObjectNode actor = actor("https://elsewhere.social/users/alice");
    ((ObjectNode) actor.get("publicKey")).put("id", ALICE_KEY);
    serve(ALICE_URI, actor);
    assertEquals(Reason.KEY_MISMATCH, failure(ALICE_KEY));
  }

  @Test
  void resolve_refusesABareKeyOwnedOnAnotherHost() throws Exception {
    final String keyId = "https://remote.social/keys/1";
    serve(URI.create(keyId), key(keyId, "https://elsewhere.social/users/alice"));
    assertEquals(Reason.KEY_MISMATCH, failure(keyId));
  }

  @Test
  void resolve_refusesARedirectToAnotherHost() throws Exception {
    final String elsewhere = "https://elsewhere.social/users/alice";
    final ObjectNode actor = actor(elsewhere);
    serve(URI.create(elsewhere), actor);
    assertEquals(Reason.KEY_MISMATCH, failure(ALICE_KEY));
  }

  @Test
  void resolve_refusesUnusableDocuments() throws Exception {
    serve(ALICE_URI, "<html></html>");
    assertEquals(Reason.KEY_UNAVAILABLE, failure(ALICE_KEY));

    serve(ALICE_URI, mapper.createObjectNode().put("id", ALICE).put("type", "Person"));
    assertEquals(Reason.KEY_UNAVAILABLE, failure(ALICE_KEY));

    final ObjectNode actor = actor(ALICE);
    ((ObjectNode) actor.get("publicKey")).put("publicKeyPem", "-----BEGIN PUBLIC KEY-----");
    serve(ALICE_URI, actor);
    assertEquals(Reason.KEY_UNAVAILABLE, failure(ALICE_KEY));
  }

  @Test
  void resolve_reportsFetchFailuresAsUnavailable() throws Exception {
    doThrow(new FetchException(410, true, "HTTP status 410"))
        .when(fetcher)
        .fetch(any(), any(), anyInt(), any());
    assertEquals(Reason.KEY_UNAVAILABLE, failure(ALICE_KEY));

    doThrow(LinkRejectedException.of(LinkRejectedException.Reason.PRIVATE_ADDRESS, "x"))
        .when(fetcher)
        .fetch(any(), any(), anyInt(), any());
    assertEquals(Reason.KEY_UNAVAILABLE, failure(ALICE_KEY));
  }

  @Test
  void resolve_refusesAddressesThePolicyRefuses_withoutFetching() throws Exception {
    for (final String keyId :
        List.of(
            "http://localhost/users/a#main-key",
            "https://10.0.0.1/users/a#main-key",
            "ftp://remote.social/key",
            "https://bit.ly/abc",
            "not a url")) {
      assertEquals(Reason.KEY_UNAVAILABLE, failure(keyId), keyId);
    }
    verify(fetcher, never()).fetch(any(), any(), anyInt(), any());
  }

  // ---- caching --------------------------------------------------------------------------

  @Configuration
  @EnableCaching
  static class CachingConfig {
    static SafeHttpFetcher fetcher;
    static ActorKeyService actorKeyService;

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(RemoteKeyResolver.CACHE);
    }

    @Bean
    RemoteKeyResolver remoteKeyResolver() {
      return new RemoteKeyResolver(fetcher, new UrlPolicy(), actorKeyService);
    }
  }

  @Test
  void resolve_isCachedUntilEvicted() throws Exception {
    serve(ALICE_URI, actor(ALICE));
    CachingConfig.fetcher = fetcher;
    CachingConfig.actorKeyService = actorKeyService;
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(CachingConfig.class)) {
      final RemoteKeyResolver cached = context.getBean(RemoteKeyResolver.class);

      final RemoteKey first = cached.resolve(ALICE_KEY);
      assertSame(first, cached.resolve(ALICE_KEY));
      verify(fetcher, times(1)).fetch(any(), any(), anyInt(), any());

      cached.evict(ALICE_KEY);
      assertNotSame(first, cached.resolve(ALICE_KEY));
      verify(fetcher, times(2)).fetch(any(), any(), anyInt(), any());

      // failures are not cached
      doThrow(new IOException("x")).when(fetcher).fetch(any(), any(), anyInt(), any());
      assertThrows(HttpSignatureException.class, () -> cached.resolve("https://remote.social/k"));
      assertThrows(HttpSignatureException.class, () -> cached.resolve("https://remote.social/k"));
      verify(fetcher, times(4)).fetch(any(), any(), anyInt(), any());
    } finally {
      CachingConfig.fetcher = null;
      CachingConfig.actorKeyService = null;
    }
  }

  @Test
  void normalizedUrlKeepsFragmentOnlyInHref() throws Exception {
    final NormalizedUrl url = new UrlPolicy().normalize(ALICE_KEY);
    assertEquals(URI.create(ALICE_KEY), url.href());
    assertEquals(ALICE_URI, url.fetchUri());
    assertEquals("remote.social", url.asciiHost());
  }
}
