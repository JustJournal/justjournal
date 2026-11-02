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

import static com.justjournal.services.activitypub.HttpSignatures.CREATED;
import static com.justjournal.services.activitypub.HttpSignatures.DATE;
import static com.justjournal.services.activitypub.HttpSignatures.DIGEST;
import static com.justjournal.services.activitypub.HttpSignatures.EXPIRES;
import static com.justjournal.services.activitypub.HttpSignatures.HOST;
import static com.justjournal.services.activitypub.HttpSignatures.REQUEST_TARGET;

import com.justjournal.services.activitypub.HttpSignatureException.Reason;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs requests to other servers and checks the signatures on requests from them, per
 * draft-cavage-http-signatures-12 as Mastodon uses it.
 *
 * <p>Outgoing requests sign {@code (request-target) host date}, plus {@code digest} when there is
 * a body, with RSASSA-PKCS1-v1_5 SHA-256 ({@code rsa-sha256}). Incoming requests may say {@code
 * rsa-sha256} or {@code hs2019} (treated the same, with an RSA key), and must sign at least {@code
 * (request-target)}, {@code host}, {@code date} and, when they have a body, {@code digest}.
 */
@Slf4j
@Service
public class HttpSignatureService {

  private final ActorKeyService actorKeyService;
  private final RemoteKeyResolver remoteKeyResolver;
  private final Clock clock;

  /** How old a Date may be. */
  @Setter
  @Value("${activitypub.signature.max-age-seconds:3600}")
  private long maxAgeSeconds = 3600;

  /** How far ahead of our clock a Date may be. */
  @Setter
  @Value("${activitypub.signature.max-future-seconds:3600}")
  private long maxFutureSeconds = 3600;

  /**
   * A cached key that fails to verify is fetched again (the sender may have a new key), unless it
   * was fetched less than this long ago, so bad signatures can't make us fetch over and over.
   */
  @Setter
  @Value("${activitypub.signature.refetch-min-age-seconds:60}")
  private long refetchMinAgeSeconds = 60;

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public HttpSignatureService(
      final ActorKeyService actorKeyService, final RemoteKeyResolver remoteKeyResolver) {
    this(actorKeyService, remoteKeyResolver, Clock.systemUTC());
  }

  HttpSignatureService(
      final ActorKeyService actorKeyService,
      final RemoteKeyResolver remoteKeyResolver,
      final Clock clock) {
    this.actorKeyService = actorKeyService;
    this.remoteKeyResolver = remoteKeyResolver;
    this.clock = clock;
  }

  /**
   * The headers that sign a request from a local actor: {@code Host}, {@code Date}, {@code Digest}
   * when there is a body, and {@code Signature} with keyId {@code actorId#main-key}.
   *
   * @param method the HTTP method, such as POST
   * @param target the full address the request goes to
   * @param bodyOrNull the exact bytes that will be sent, or null for a request without a body
   * @param actorId a local user's actor id or the instance actor's
   * @throws IllegalArgumentException if the actor isn't one of ours
   */
  public Map<String, String> sign(
      final String method, final URI target, final byte[] bodyOrNull, final String actorId) {
    return HttpSignatures.sign(
        method,
        target,
        bodyOrNull,
        ActorKeyService.keyId(actorId),
        actorKeyService.privateKeyFor(actorId),
        clock.instant());
  }

  /**
   * Check the signature on a request from another server.
   *
   * @param method the HTTP method
   * @param pathAndQuery the raw path and query string the request was sent to
   * @param headers the request headers; names in any case, several values are joined with ", "
   * @param body the request body, or null or empty when there is none
   * @return the key that signed it and the actor that owns the key
   * @throws HttpSignatureException saying why the request is not accepted
   */
  public VerifiedSignature verify(
      final String method,
      final String pathAndQuery,
      final Map<String, List<String>> headers,
      final byte[] body)
      throws HttpSignatureException {
    final Map<String, String> request = lowerCase(headers);
    final String header = request.get("signature");
    if (header == null || header.isBlank())
      throw new HttpSignatureException(Reason.MISSING_SIGNATURE, "No Signature header");

    final Map<String, String> params = HttpSignatures.parseSignature(header);
    final String keyId = params.get("keyId");
    final String signatureText = params.get("signature");
    if (keyId == null || keyId.isBlank() || signatureText == null || signatureText.isBlank())
      throw new HttpSignatureException(Reason.MALFORMED, "Signature lacks keyId or signature");

    final String algorithm = params.get("algorithm");
    if (algorithm != null
        && !HttpSignatures.RSA_SHA256.equalsIgnoreCase(algorithm)
        && !HttpSignatures.HS2019.equalsIgnoreCase(algorithm))
      throw new HttpSignatureException(
          Reason.UNSUPPORTED_ALGORITHM, "Unsupported algorithm " + algorithm);

    // the draft's default when headers is left out is "date" alone, which isn't enough
    final List<String> signed =
        Arrays.stream(params.getOrDefault("headers", DATE).strip().split("\\s+"))
            .map(name -> name.toLowerCase(Locale.ROOT))
            .toList();
    final boolean hasBody = body != null && body.length > 0;
    for (final String required :
        hasBody
            ? List.of(REQUEST_TARGET, HOST, DATE, DIGEST)
            : List.of(REQUEST_TARGET, HOST, DATE)) {
      if (!signed.contains(required))
        throw new HttpSignatureException(Reason.UNSIGNED_HEADER, required + " is not signed");
    }

    final Map<String, String> values = new HashMap<>();
    for (final String name : signed) {
      final String value =
          switch (name) {
            case REQUEST_TARGET -> HttpSignatures.requestTarget(method, pathAndQuery);
            case CREATED, EXPIRES -> params.get(name.substring(1, name.length() - 1));
            default -> request.get(name);
          };
      if (value == null)
        throw new HttpSignatureException(Reason.MISSING_HEADER, name + " is signed but missing");
      values.put(name, value);
    }

    if (hasBody || request.containsKey(DIGEST)) checkDigest(request.get(DIGEST), body);
    checkDate(request.get(DATE), params.get("expires"));

    final byte[] signature;
    try {
      signature = Base64.getDecoder().decode(signatureText.strip());
    } catch (final IllegalArgumentException e) {
      throw new HttpSignatureException(Reason.MALFORMED, "Signature is not base64");
    }
    final String signingString = HttpSignatures.signingString(signed, values::get);

    final RemoteKey key = remoteKeyResolver.resolve(keyId);
    if (verifies(key, signingString, signature)) return new VerifiedSignature(keyId, key.owner());

    if (clock.millis() - key.fetchedAt() < refetchMinAgeSeconds * 1000)
      throw new HttpSignatureException(Reason.BAD_SIGNATURE, "Signature does not verify");
    log.debug("Signature by {} failed with the cached key; fetching it again", keyId);
    remoteKeyResolver.evict(keyId);
    final RemoteKey fresh = remoteKeyResolver.resolve(keyId);
    if (verifies(fresh, signingString, signature))
      return new VerifiedSignature(keyId, fresh.owner());
    throw new HttpSignatureException(Reason.BAD_SIGNATURE, "Signature does not verify");
  }

  private static boolean verifies(
      final RemoteKey key, final String signingString, final byte[] signature)
      throws HttpSignatureException {
    final PublicKey publicKey;
    try {
      publicKey = Pem.publicKey(key.publicKeyPem());
    } catch (final GeneralSecurityException e) {
      throw new HttpSignatureException(
          Reason.KEY_UNAVAILABLE, "Key " + key.keyId() + " is unreadable", e);
    }
    if (!(publicKey instanceof RSAPublicKey))
      throw new HttpSignatureException(Reason.UNSUPPORTED_ALGORITHM, "Key is not RSA");
    return HttpSignatures.rsaVerify(publicKey, signingString, signature);
  }

  /** The Digest header must carry a SHA-256 of the body; other algorithms in it are ignored. */
  private static void checkDigest(final String digest, final byte[] body)
      throws HttpSignatureException {
    if (digest == null) throw new HttpSignatureException(Reason.MISSING_HEADER, "No Digest header");
    final byte[] expected = HttpSignatures.sha256(body == null ? new byte[0] : body);
    for (final String part : digest.split(",")) {
      final int equals = part.indexOf('=');
      if (equals < 0) continue;
      if (!"sha-256".equalsIgnoreCase(part.substring(0, equals).strip())) continue;
      final byte[] given;
      try {
        given = Base64.getDecoder().decode(part.substring(equals + 1).strip());
      } catch (final IllegalArgumentException e) {
        throw new HttpSignatureException(Reason.DIGEST_MISMATCH, "Digest is not base64");
      }
      if (MessageDigest.isEqual(expected, given)) return;
      throw new HttpSignatureException(Reason.DIGEST_MISMATCH, "Digest does not match the body");
    }
    throw new HttpSignatureException(Reason.DIGEST_MISMATCH, "Digest has no SHA-256 value");
  }

  private void checkDate(final String date, final String expires) throws HttpSignatureException {
    final Instant sent;
    try {
      sent = ZonedDateTime.parse(date.strip(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (final DateTimeParseException e) {
      throw new HttpSignatureException(Reason.DATE, "Unreadable Date " + date);
    }
    final Instant now = clock.instant();
    if (sent.isBefore(now.minusSeconds(maxAgeSeconds)))
      throw new HttpSignatureException(Reason.DATE, "Date " + date + " is too old");
    if (sent.isAfter(now.plusSeconds(maxFutureSeconds)))
      throw new HttpSignatureException(Reason.DATE, "Date " + date + " is in the future");
    if (expires != null) {
      try {
        if (Long.parseLong(expires.strip()) < now.getEpochSecond())
          throw new HttpSignatureException(Reason.DATE, "Signature expired");
      } catch (final NumberFormatException e) {
        throw new HttpSignatureException(Reason.MALFORMED, "Unreadable expires " + expires);
      }
    }
  }

  private static Map<String, String> lowerCase(final Map<String, List<String>> headers) {
    final Map<String, String> result = new HashMap<>();
    if (headers == null) return result;
    headers.forEach(
        (name, values) -> {
          if (name == null || values == null || values.isEmpty()) return;
          result.merge(
              name.toLowerCase(Locale.ROOT),
              String.join(", ", values),
              (a, b) -> a + ", " + b);
        });
    return result;
  }
}
