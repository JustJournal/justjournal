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

import static com.justjournal.services.activitypub.ActivityPubFetcher.sameHost;
import static com.justjournal.services.activitypub.ActivityPubFetcher.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import com.justjournal.services.link.UrlPolicy;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Fetches the public key another server signs with, from the keyId in its signature.
 *
 * <p>The fetch goes through {@link ActivityPubFetcher}, so it reaches only public addresses, is
 * capped in size and time, and is signed by the instance actor. The document may be an actor
 * carrying the key in {@code publicKey}, or the key on its own. Either way the key's {@code id}
 * must be the keyId asked for, the key must be owned by the actor that served it (or, for a bare
 * key, by an actor on the same host), and no redirect may leave the keyId's host.
 *
 * <p>Keys are cached for {@code activitypub.key-cache-minutes} (see {@code CacheConfig}); {@link
 * #evict} drops one so a key that no longer verifies can be fetched again after a rotation.
 */
@Slf4j
@Service
public class RemoteKeyResolver {
  public static final String CACHE = "activitypubKeys";

  static final Set<String> ACCEPT_TYPES = ActivityPubFetcher.ACCEPT_TYPES;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ActivityPubFetcher fetcher;
  private final UrlPolicy urlPolicy;
  private final Clock clock;

  @Setter
  @Value("${activitypub.key-fetch.max-bytes:262144}")
  private int maxBytes = 262_144;

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public RemoteKeyResolver(
      final SafeHttpFetcher fetcher,
      final UrlPolicy urlPolicy,
      final ActorKeyService actorKeyService) {
    this(fetcher, urlPolicy, actorKeyService, Clock.systemUTC());
  }

  RemoteKeyResolver(
      final SafeHttpFetcher fetcher,
      final UrlPolicy urlPolicy,
      final ActorKeyService actorKeyService,
      final Clock clock) {
    this.fetcher = new ActivityPubFetcher(fetcher, urlPolicy, actorKeyService, clock);
    this.urlPolicy = urlPolicy;
    this.clock = clock;
  }

  /**
   * The key a signature names, from the cache or fetched now.
   *
   * @param keyId the keyId of a Signature header, an http or https URL
   * @throws HttpSignatureException KEY_UNAVAILABLE when the key can't be fetched or read,
   *     KEY_MISMATCH when the document doesn't vouch for it
   */
  @Cacheable(CACHE)
  public RemoteKey resolve(final String keyId) throws HttpSignatureException {
    final NormalizedUrl url;
    try {
      url = urlPolicy.normalize(keyId);
    } catch (final LinkRejectedException e) {
      throw unavailable("Refused key address " + keyId, e);
    }

    final SafeHttpFetcher.Result result;
    try {
      result = fetcher.signedGet(url, maxBytes);
    } catch (final IOException | LinkRejectedException e) {
      throw unavailable("Could not fetch key " + keyId + ": " + e.getMessage(), e);
    }
    final String host = url.asciiHost();
    if (!sameHost(result.finalUri(), host))
      throw mismatch("Key " + keyId + " was redirected to " + result.finalUri().getHost());

    final JsonNode doc;
    try {
      doc = MAPPER.readTree(result.body());
    } catch (final IOException e) {
      throw unavailable("Key " + keyId + " is not JSON", e);
    }
    if (doc == null || !doc.isObject())
      throw unavailable("Key " + keyId + " is not an object", null);

    final JsonNode key;
    final String owner;
    if (doc.has("publicKey")) {
      final String actorId = text(doc, "id");
      key = pick(doc.get("publicKey"), keyId);
      if (key == null) throw mismatch("Actor " + actorId + " has no key " + keyId);
      owner = text(key, "owner");
      if (actorId == null || !actorId.equals(owner))
        throw mismatch("Key " + keyId + " is owned by " + owner + ", not " + actorId);
    } else if (doc.has("publicKeyPem")) {
      key = doc;
      if (!keyId.equals(text(doc, "id"))) throw mismatch("Key document is not " + keyId);
      owner = text(doc, "owner");
      if (owner == null) throw mismatch("Key " + keyId + " has no owner");
    } else {
      throw unavailable("No key in the document at " + keyId, null);
    }
    if (!sameHost(uri(owner), host))
      throw mismatch("Key " + keyId + " is owned by " + owner + " on another host");

    final String pem = text(key, "publicKeyPem");
    final PublicKey publicKey = parse(pem, keyId);
    // Unless the document we fetched is the owner's own actor, anyone on that host could have
    // published it naming someone else as owner; the owner's actor must list this key itself.
    if (!owner.equals(withoutFragment(result.finalUri())) || !owner.equals(text(doc, "id"))) {
      confirmOwner(owner, keyId, publicKey, host);
    }
    return new RemoteKey(keyId, owner, pem, clock.millis());
  }

  /** Fetch the owner's actor and require that it is the owner and lists the same key. */
  private void confirmOwner(
      final String owner, final String keyId, final PublicKey publicKey, final String host)
      throws HttpSignatureException {
    final NormalizedUrl url;
    try {
      url = urlPolicy.normalize(owner);
    } catch (final LinkRejectedException e) {
      throw mismatch("Refused owner address " + owner);
    }
    final SafeHttpFetcher.Result result;
    try {
      result = fetcher.signedGet(url, maxBytes);
    } catch (final IOException | LinkRejectedException e) {
      throw unavailable("Could not fetch owner " + owner + ": " + e.getMessage(), e);
    }
    if (!sameHost(result.finalUri(), host))
      throw mismatch("Owner " + owner + " was redirected to " + result.finalUri().getHost());
    final JsonNode actor;
    try {
      actor = MAPPER.readTree(result.body());
    } catch (final IOException e) {
      throw unavailable("Owner " + owner + " is not JSON", e);
    }
    if (actor == null || !actor.isObject() || !owner.equals(text(actor, "id")))
      throw mismatch("The document at " + owner + " is not that actor");
    final JsonNode listed = actor.has("publicKey") ? pick(actor.get("publicKey"), keyId) : null;
    if (listed == null || !owner.equals(text(listed, "owner")))
      throw mismatch("Actor " + owner + " does not list key " + keyId);
    if (!Arrays.equals(
        publicKey.getEncoded(), parse(text(listed, "publicKeyPem"), keyId).getEncoded()))
      throw mismatch("Actor " + owner + " lists a different key as " + keyId);
  }

  private static PublicKey parse(final String pem, final String keyId)
      throws HttpSignatureException {
    if (pem == null) throw unavailable("Key " + keyId + " has no publicKeyPem", null);
    try {
      return Pem.publicKey(pem);
    } catch (final GeneralSecurityException e) {
      throw unavailable("Key " + keyId + " is not an RSA public key", e);
    }
  }

  private static String withoutFragment(final URI uri) {
    final String value = uri.toString();
    final int hash = value.indexOf('#');
    return hash < 0 ? value : value.substring(0, hash);
  }

  /** Forget a cached key, so the next {@link #resolve} fetches it again. */
  @CacheEvict(CACHE)
  public void evict(final String keyId) {
    log.debug("Dropped cached key {}", keyId);
  }

  /** The key in an actor's publicKey whose id is the keyId; servers send one key or a list. */
  private static JsonNode pick(final JsonNode publicKey, final String keyId) {
    if (publicKey.isObject()) return keyId.equals(text(publicKey, "id")) ? publicKey : null;
    if (publicKey.isArray()) {
      for (final JsonNode candidate : publicKey) {
        if (candidate.isObject() && keyId.equals(text(candidate, "id"))) return candidate;
      }
    }
    return null;
  }

  private static URI uri(final String value) {
    if (value == null) return null;
    try {
      return URI.create(value);
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  private static HttpSignatureException unavailable(final String message, final Throwable cause) {
    return new HttpSignatureException(
        HttpSignatureException.Reason.KEY_UNAVAILABLE, message, cause);
  }

  private static HttpSignatureException mismatch(final String message) {
    return new HttpSignatureException(HttpSignatureException.Reason.KEY_MISMATCH, message);
  }
}
