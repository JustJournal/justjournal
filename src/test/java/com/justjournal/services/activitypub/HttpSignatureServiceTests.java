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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.services.activitypub.HttpSignatureException.Reason;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpSignatureServiceTests {

  private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
  private static final String BOB = "https://www.justjournal.com/users/bob";
  private static final String BOB_KEY = BOB + "#main-key";
  private static final String ALICE = "https://remote.social/users/alice";
  private static final String ALICE_KEY = ALICE + "#main-key";
  /** Where the openssl vector was signed for. */
  private static final URI INBOX = URI.create("https://remote.example/users/alice/inbox");
  private static final byte[] BODY = "{\"type\":\"Follow\"}".getBytes(StandardCharsets.UTF_8);

  /** SHA-256 of BODY, from {@code openssl dgst -sha256 -binary | base64}. */
  private static final String BODY_DIGEST = "SHA-256=GYwYnH3BiO6aICFt0ThC5bUIJ4byvqdpWtR8m5fNkww=";

  /**
   * The signing string below signed with TestKeys by {@code openssl dgst -sha256 -sign}. PKCS#1
   * v1.5 signatures are deterministic, so ours must come out the same.
   */
  private static final String OPENSSL_SIGNATURE =
      "GKIJrWkKrH+4YjULbX6O9GRP7l/64hSAKP/M4egMIxAlQDIArPYtKP5R1J4s47g/93UYfGzbuhIDmr7DbTSFwJUOlZY"
          + "xn98Q3JGRAYBjmG/rzCLQ2JVYQxrGl1AZRhkLD0BBI/hFltxhv4E6eyzEH8kSLWJVBNolScxtyrAMhny9Edty"
          + "wogIrI+Vfa4+658D2EKwSb5h9IIYqEU2qJ8R3ajV/CcMpQbl6IrOnmLCpRxYZey2mKyWbTPqIIpSmBKKcdPU7E"
          + "2/zpUlC2DKfepY7OBpdi5+GEN1d6W2XYoOjI1GIHiFvzI/PFqGpzBq0Jimh2DSR/nNLIFgqh4PSNaukw==";

  private ActorKeyService actorKeyService;
  private RemoteKeyResolver resolver;
  private PrivateKey privateKey;

  @BeforeEach
  void setUp() throws Exception {
    privateKey = TestKeys.privateKey();
    actorKeyService = mock(ActorKeyService.class);
    when(actorKeyService.privateKeyFor(BOB)).thenReturn(privateKey);
    when(actorKeyService.privateKeyFor(ALICE)).thenReturn(privateKey);
    when(actorKeyService.instanceActorId()).thenReturn("https://www.justjournal.com/actor");
    resolver = mock(RemoteKeyResolver.class);
    // a key fetched ten minutes ago, so a failure may fetch it again
    when(resolver.resolve(ALICE_KEY))
        .thenReturn(aliceKey(TestKeys.PUBLIC_PEM, NOW.minusSeconds(600)));
  }

  private HttpSignatureService service(final Instant now) {
    return new HttpSignatureService(actorKeyService, resolver, Clock.fixed(now, ZoneOffset.UTC));
  }

  private static RemoteKey aliceKey(final String pem, final Instant fetchedAt) {
    return new RemoteKey(ALICE_KEY, ALICE, pem, fetchedAt.toEpochMilli());
  }

  /** Signed headers as a servlet would hand them over, plus anything extra. */
  private static Map<String, List<String>> request(final Map<String, String> headers) {
    final Map<String, List<String>> request = new LinkedHashMap<>();
    headers.forEach((name, value) -> request.put(name, List.of(value)));
    request.put("Content-Type", List.of("application/activity+json"));
    return request;
  }

  /** A POST from Alice to Bob's inbox, signed at NOW. */
  private Map<String, List<String>> signedByAlice(final byte[] body) {
    final URI bobsInbox = URI.create("https://www.justjournal.com/users/bob/inbox");
    return request(service(NOW).sign("POST", bobsInbox, body, ALICE));
  }

  /** A Signature header over the given headers, with values supplied by the caller. */
  private String handMade(
      final String keyId,
      final String algorithm,
      final List<String> names,
      final Map<String, String> values) {
    final String signingString = HttpSignatures.signingString(names, values::get);
    final String sig =
        Base64.getEncoder().encodeToString(HttpSignatures.rsaSign(privateKey, signingString));
    return "keyId=\""
        + keyId
        + "\","
        + (algorithm == null ? "" : "algorithm=\"" + algorithm + "\",")
        + "headers=\""
        + String.join(" ", names)
        + "\",signature=\""
        + sig
        + "\"";
  }

  // ---- signing --------------------------------------------------------------------------

  @Test
  void sign_matchesAVectorMadeWithOpenssl() {
    final Map<String, String> headers = service(NOW).sign("POST", INBOX, BODY, BOB);

    assertEquals(List.of("Host", "Date", "Digest", "Signature"), List.copyOf(headers.keySet()));
    assertEquals("remote.example", headers.get("Host"));
    assertEquals("Mon, 02 Nov 2026 12:00:00 GMT", headers.get("Date"));
    assertEquals(BODY_DIGEST, headers.get("Digest"));
    assertEquals(
        "keyId=\""
            + BOB_KEY
            + "\",algorithm=\"rsa-sha256\",headers=\"(request-target) host date digest\","
            + "signature=\""
            + OPENSSL_SIGNATURE
            + "\"",
        headers.get("Signature"));
  }

  @Test
  void signingString_isOneLinePerHeaderInOrder() {
    final Map<String, String> values =
        Map.of(
            "(request-target)", "post /users/alice/inbox",
            "host", "remote.example",
            "date", "Mon, 02 Nov 2026 12:00:00 GMT",
            "digest", BODY_DIGEST);
    assertEquals(
        "(request-target): post /users/alice/inbox\n"
            + "host: remote.example\n"
            + "date: Mon, 02 Nov 2026 12:00:00 GMT\n"
            + "digest: "
            + BODY_DIGEST,
        HttpSignatures.signingString(
            List.of("(request-target)", "host", "date", "digest"), values::get));
  }

  @Test
  void sign_withoutBody_leavesOutDigest_andKeepsPortAndQuery() {
    final Map<String, String> headers =
        service(NOW)
            .sign("GET", URI.create("https://remote.social:8443/users/alice?page=1"), null, BOB);

    assertEquals(List.of("Host", "Date", "Signature"), List.copyOf(headers.keySet()));
    assertEquals("remote.social:8443", headers.get("Host"));
    assertEquals(true, headers.get("Signature").contains("headers=\"(request-target) host date\""));
  }

  @Test
  void sign_dateHasTwoDigitDays() {
    final Map<String, String> headers =
        service(Instant.parse("2026-11-03T04:05:06Z")).sign("GET", INBOX, null, BOB);
    assertEquals("Tue, 03 Nov 2026 04:05:06 GMT", headers.get("Date"));
  }

  @Test
  void sign_refusesActorsThatAreNotOurs() {
    when(actorKeyService.privateKeyFor("https://elsewhere.social/users/x"))
        .thenThrow(new IllegalArgumentException("Not a local actor"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service(NOW).sign("GET", INBOX, null, "https://elsewhere.social/users/x"));
  }

  // ---- verifying ------------------------------------------------------------------------

  @Test
  void verify_acceptsWhatSignSigned() throws Exception {
    final VerifiedSignature verified =
        service(NOW).verify("POST", "/users/bob/inbox", signedByAlice(BODY), BODY);

    assertEquals(new VerifiedSignature(ALICE_KEY, ALICE), verified);
    verify(resolver, never()).evict(anyString());
  }

  @Test
  void verify_acceptsSignedGetWithoutBody() throws Exception {
    final Map<String, List<String>> headers =
        request(
            service(NOW)
                .sign("GET", URI.create("https://www.justjournal.com/users/bob?x=1"), null, ALICE));
    assertEquals(ALICE, service(NOW).verify("GET", "/users/bob?x=1", headers, null).actorId());
  }

  @Test
  void verify_headerNamesAnyCase() throws Exception {
    final Map<String, List<String>> lower = new LinkedHashMap<>();
    signedByAlice(BODY).forEach((name, values) -> lower.put(name.toLowerCase(), values));
    assertEquals(ALICE, service(NOW).verify("POST", "/users/bob/inbox", lower, BODY).actorId());
  }

  @Test
  void verify_acceptsHs2019AndNoAlgorithm() throws Exception {
    for (final String algorithm : new String[] {"hs2019", null}) {
      final Map<String, String> values = new LinkedHashMap<>();
      values.put("(request-target)", "post /users/bob/inbox");
      values.put("host", "www.justjournal.com");
      values.put("date", "Mon, 02 Nov 2026 12:00:00 GMT");
      values.put("digest", BODY_DIGEST);
      final Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Host", values.get("host"));
      headers.put("Date", values.get("date"));
      headers.put("Digest", BODY_DIGEST);
      headers.put(
          "Signature", handMade(ALICE_KEY, algorithm, List.copyOf(values.keySet()), values));

      assertEquals(
          ALICE, service(NOW).verify("POST", "/users/bob/inbox", request(headers), BODY).actorId());
    }
  }

  @Test
  void verify_refusesOtherAlgorithms() {
    final Map<String, List<String>> headers = signedByAlice(BODY);
    headers.put(
        "Signature",
        List.of(headers.get("Signature").get(0).replace("rsa-sha256", "hmac-sha256")));
    assertReason(Reason.UNSUPPORTED_ALGORITHM, headers, BODY);
  }

  @Test
  void verify_refusesTamperedBody() {
    assertReason(
        Reason.DIGEST_MISMATCH,
        signedByAlice(BODY),
        "{\"type\":\"Delete\"}".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void verify_refusesBodyWithMatchingButUnsignedDigest() throws Exception {
    final byte[] other = "{\"type\":\"Delete\"}".getBytes(StandardCharsets.UTF_8);
    final Map<String, List<String>> headers = signedByAlice(BODY);
    headers.put("Digest", List.of(HttpSignatures.digest(other)));

    assertReason(Reason.BAD_SIGNATURE, headers, other);
    // the cached key was fetched long enough ago to be fetched again, once
    verify(resolver).evict(ALICE_KEY);
    verify(resolver, times(2)).resolve(ALICE_KEY);
  }

  @Test
  void verify_refusesDigestWithoutSha256() {
    final Map<String, List<String>> headers = signedByAlice(BODY);
    headers.put("Digest", List.of("SHA-512=abc"));
    assertReason(Reason.DIGEST_MISMATCH, headers, BODY);
  }

  @Test
  void verify_refusesAnotherPath() {
    assertReason(Reason.BAD_SIGNATURE, "/users/carol/inbox", signedByAlice(BODY), BODY);
  }

  @Test
  void verify_refusesARequestSignedForAnotherHost_evenWithThatHostHeader() {
    final URI elsewhere = URI.create("https://elsewhere.social/users/bob/inbox");
    assertReason(
        Reason.BAD_SIGNATURE, request(service(NOW).sign("POST", elsewhere, BODY, ALICE)), BODY);
  }

  @Test
  void verify_usesTheSiteHost_whenAProxyRewritesTheHostHeader() throws Exception {
    final Map<String, List<String>> headers = new LinkedHashMap<>(signedByAlice(BODY));
    headers.put("Host", List.of("localhost:8080"));
    assertEquals(
        ALICE, service(NOW).verify("POST", "/users/bob/inbox", headers, BODY).actorId());
  }

  @Test
  void verify_refusesMissingSignature() {
    final Map<String, List<String>> headers = signedByAlice(BODY);
    headers.remove("Signature");
    assertReason(Reason.MISSING_SIGNATURE, headers, BODY);
  }

  @Test
  void verify_refusesUnreadableSignature() {
    for (final String bad :
        new String[] {
          "garbage",
          "keyId=\"" + ALICE_KEY + "\",signature=\"unterminated",
          "keyId=\"" + ALICE_KEY + "\"",
          "keyId=\"a\",keyId=\"b\",signature=\"c\"",
          "keyId=\""
              + ALICE_KEY
              + "\",headers=\"(request-target) host date digest\",signature=\"!!\""
        }) {
      final Map<String, List<String>> headers = signedByAlice(BODY);
      headers.put("Signature", List.of(bad));
      assertReason(Reason.MALFORMED, headers, BODY);
    }
  }

  @Test
  void verify_requiresTargetHostDateAndDigestToBeSigned() throws Exception {
    final Map<String, String> values = new LinkedHashMap<>();
    values.put("(request-target)", "post /users/bob/inbox");
    values.put("host", "www.justjournal.com");
    values.put("date", "Mon, 02 Nov 2026 12:00:00 GMT");
    values.put("digest", BODY_DIGEST);
    for (final String left : values.keySet()) {
      final List<String> names = values.keySet().stream().filter(n -> !n.equals(left)).toList();
      final Map<String, List<String>> headers = signedByAlice(BODY);
      headers.put("Signature", List.of(handMade(ALICE_KEY, "rsa-sha256", names, values)));
      assertReason(Reason.UNSIGNED_HEADER, headers, BODY);
    }

    // a Signature without a headers list covers only the date
    final Map<String, List<String>> headers = signedByAlice(BODY);
    final String dateOnly = handMade(ALICE_KEY, "rsa-sha256", List.of("date"), values);
    headers.put("Signature", List.of(dateOnly.replaceFirst("headers=\"date\",", "")));
    assertReason(Reason.UNSIGNED_HEADER, headers, BODY);
  }

  @Test
  void verify_refusesSignedHeaderMissingFromRequest() {
    final Map<String, List<String>> headers = signedByAlice(BODY);
    headers.remove("Date");
    assertReason(Reason.MISSING_HEADER, headers, BODY);
  }

  @Test
  void verify_refusesStaleAndFutureDates() throws Exception {
    final Map<String, List<String>> headers = signedByAlice(BODY);
    final String path = "/users/bob/inbox";

    // within an hour either way is fine
    service(NOW.plus(Duration.ofMinutes(59))).verify("POST", path, headers, BODY);
    service(NOW.minus(Duration.ofMinutes(59))).verify("POST", path, headers, BODY);

    for (final Instant now :
        List.of(
            NOW.plus(Duration.ofMinutes(61)),
            NOW.minus(Duration.ofMinutes(61)),
            NOW.minus(Duration.ofDays(400)))) {
      final HttpSignatureException e =
          assertThrows(
              HttpSignatureException.class, () -> service(now).verify("POST", path, headers, BODY));
      assertEquals(Reason.DATE, e.reason());
    }
  }

  @Test
  void verify_dateWindowIsConfigurable() throws Exception {
    final HttpSignatureService service = service(NOW.plus(Duration.ofMinutes(10)));
    service.setMaxAgeSeconds(300);
    final HttpSignatureException e =
        assertThrows(
            HttpSignatureException.class,
            () -> service.verify("POST", "/users/bob/inbox", signedByAlice(BODY), BODY));
    assertEquals(Reason.DATE, e.reason());

    final HttpSignatureService early = service(NOW.minus(Duration.ofMinutes(10)));
    early.setMaxFutureSeconds(300);
    assertEquals(
        Reason.DATE,
        assertThrows(
                HttpSignatureException.class,
                () -> early.verify("POST", "/users/bob/inbox", signedByAlice(BODY), BODY))
            .reason());
  }

  @Test
  void verify_refusesUnreadableDate() throws Exception {
    final Map<String, String> values = new LinkedHashMap<>();
    values.put("(request-target)", "post /users/bob/inbox");
    values.put("host", "www.justjournal.com");
    values.put("date", "yesterday");
    values.put("digest", BODY_DIGEST);
    final Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Host", "www.justjournal.com");
    headers.put("Date", "yesterday");
    headers.put("Digest", BODY_DIGEST);
    headers.put(
        "Signature", handMade(ALICE_KEY, "rsa-sha256", List.copyOf(values.keySet()), values));
    assertReason(Reason.DATE, request(headers), BODY);
  }

  @Test
  void verify_passesOnUnknownKeys() throws Exception {
    when(resolver.resolve(ALICE_KEY))
        .thenThrow(new HttpSignatureException(Reason.KEY_UNAVAILABLE, "404"));
    assertReason(Reason.KEY_UNAVAILABLE, signedByAlice(BODY), BODY);
  }

  @Test
  void verify_fetchesARotatedKeyOnce() throws Exception {
    // the cache still holds Alice's old key; she now signs with TestKeys
    final KeyPair old = TestKeys.another();
    when(resolver.resolve(ALICE_KEY))
        .thenReturn(aliceKey(Pem.encode(old.getPublic()), NOW.minusSeconds(3600)))
        .thenReturn(aliceKey(TestKeys.PUBLIC_PEM, NOW));

    assertEquals(
        ALICE,
        service(NOW).verify("POST", "/users/bob/inbox", signedByAlice(BODY), BODY).actorId());
    verify(resolver).evict(ALICE_KEY);
    verify(resolver, times(2)).resolve(ALICE_KEY);
  }

  @Test
  void verify_givesUpWhenTheRefetchedKeyFailsToo() throws Exception {
    final KeyPair other = TestKeys.another();
    when(resolver.resolve(ALICE_KEY))
        .thenReturn(aliceKey(Pem.encode(other.getPublic()), NOW.minusSeconds(3600)));

    assertReason(Reason.BAD_SIGNATURE, signedByAlice(BODY), BODY);
    verify(resolver).evict(ALICE_KEY);
    verify(resolver, times(2)).resolve(ALICE_KEY);
  }

  @Test
  void verify_doesNotRefetchAKeyJustFetched() throws Exception {
    final KeyPair other = TestKeys.another();
    when(resolver.resolve(ALICE_KEY))
        .thenReturn(aliceKey(Pem.encode(other.getPublic()), NOW.minusSeconds(5)));

    assertReason(Reason.BAD_SIGNATURE, signedByAlice(BODY), BODY);
    verify(resolver, never()).evict(anyString());
    verify(resolver, times(1)).resolve(ALICE_KEY);
  }

  private void assertReason(
      final Reason reason, final Map<String, List<String>> headers, final byte[] body) {
    assertReason(reason, "/users/bob/inbox", headers, body);
  }

  private void assertReason(
      final Reason reason,
      final String path,
      final Map<String, List<String>> headers,
      final byte[] body) {
    final HttpSignatureException e =
        assertThrows(
            HttpSignatureException.class, () -> service(NOW).verify("POST", path, headers, body));
    assertEquals(reason, e.reason(), e.getMessage());
  }
}
