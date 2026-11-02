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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The parts of draft-cavage-http-signatures-12 that signing and checking share, as Mastodon uses
 * it: RSASSA-PKCS1-v1_5 with SHA-256 over a signing string of {@code name: value} lines.
 */
final class HttpSignatures {
  static final String REQUEST_TARGET = "(request-target)";
  static final String CREATED = "(created)";
  static final String EXPIRES = "(expires)";
  static final String HOST = "host";
  static final String DATE = "date";
  static final String DIGEST = "digest";
  static final String RSA_SHA256 = "rsa-sha256";
  static final String HS2019 = "hs2019";

  /** IMF-fixdate, the HTTP Date format: always two digit days and GMT. */
  static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
          .withZone(ZoneOffset.UTC);

  private static final String JCA_ALGORITHM = "SHA256withRSA";

  private HttpSignatures() {}

  /**
   * The headers that sign a request: Host, Date, Digest when there is a body, and Signature.
   *
   * @param body the request body, or null for a request without one
   */
  static Map<String, String> sign(
      final String method,
      final URI target,
      final byte[] body,
      final String keyId,
      final PrivateKey key,
      final Instant now) {
    final Map<String, String> signed = new LinkedHashMap<>();
    signed.put(REQUEST_TARGET, requestTarget(method, pathAndQuery(target)));
    signed.put(HOST, host(target));
    signed.put(DATE, HTTP_DATE.format(now));
    if (body != null) signed.put(DIGEST, digest(body));
    final List<String> names = List.copyOf(signed.keySet());
    final String signingString = signingString(names, signed::get);

    final Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Host", signed.get(HOST));
    headers.put("Date", signed.get(DATE));
    if (body != null) headers.put("Digest", signed.get(DIGEST));
    headers.put(
        "Signature",
        "keyId=\""
            + keyId
            + "\",algorithm=\""
            + RSA_SHA256
            + "\",headers=\""
            + String.join(" ", names)
            + "\",signature=\""
            + Base64.getEncoder().encodeToString(rsaSign(key, signingString))
            + "\"");
    return headers;
  }

  /** The Host header for a target: the host, and the port when it isn't the scheme's default. */
  static String host(final URI target) {
    final int port = target.getPort();
    final boolean defaultPort =
        port == -1
            || ("https".equalsIgnoreCase(target.getScheme()) && port == 443)
            || ("http".equalsIgnoreCase(target.getScheme()) && port == 80);
    return defaultPort ? target.getHost() : target.getHost() + ":" + port;
  }

  /** The path and query as sent on the request line. */
  static String pathAndQuery(final URI target) {
    final String raw = target.getRawPath();
    final String path = raw == null || raw.isEmpty() ? "/" : raw;
    return target.getRawQuery() == null ? path : path + "?" + target.getRawQuery();
  }

  static String requestTarget(final String method, final String pathAndQuery) {
    return method.toLowerCase(Locale.ROOT) + " " + pathAndQuery;
  }

  /** {@code SHA-256=} and the base64 SHA-256 of the body. */
  static String digest(final byte[] body) {
    return "SHA-256=" + Base64.getEncoder().encodeToString(sha256(body));
  }

  static byte[] sha256(final byte[] body) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(body);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /**
   * One {@code name: value} line per signed header, in the order given, joined with newlines.
   *
   * @param names lower case header names and pseudo headers
   * @param valueOf the value for a name
   */
  static String signingString(final List<String> names, final UnaryOperator<String> valueOf) {
    final StringBuilder out = new StringBuilder();
    for (final String name : names) {
      if (!out.isEmpty()) out.append('\n');
      out.append(name).append(": ").append(valueOf.apply(name));
    }
    return out.toString();
  }

  static byte[] rsaSign(final PrivateKey key, final String signingString) {
    try {
      final Signature signature = Signature.getInstance(JCA_ALGORITHM);
      signature.initSign(key);
      signature.update(signingString.getBytes(StandardCharsets.UTF_8));
      return signature.sign();
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign with " + JCA_ALGORITHM, e);
    }
  }

  static boolean rsaVerify(final PublicKey key, final String signingString, final byte[] sig) {
    try {
      final Signature signature = Signature.getInstance(JCA_ALGORITHM);
      signature.initVerify(key);
      signature.update(signingString.getBytes(StandardCharsets.UTF_8));
      return signature.verify(sig);
    } catch (final GeneralSecurityException e) {
      return false;
    }
  }

  /**
   * The parameters of a Signature header: {@code name="value"} pairs separated by commas. Numbers
   * ({@code created}, {@code expires}) may be unquoted.
   *
   * @throws HttpSignatureException if the header is not in that form or repeats a name
   */
  static Map<String, String> parseSignature(final String header) throws HttpSignatureException {
    final Map<String, String> params = new LinkedHashMap<>();
    final int length = header.length();
    int i = 0;
    while (i < length) {
      while (i < length && (header.charAt(i) == ' ' || header.charAt(i) == ',')) i++;
      if (i >= length) break;
      final int equals = header.indexOf('=', i);
      if (equals < 0) throw malformed();
      final String name = header.substring(i, equals).strip();
      if (name.isEmpty() || !name.chars().allMatch(Character::isLetter)) throw malformed();
      i = equals + 1;
      final String value;
      if (i < length && header.charAt(i) == '"') {
        final int close = header.indexOf('"', i + 1);
        if (close < 0) throw malformed();
        value = header.substring(i + 1, close);
        i = close + 1;
      } else {
        int end = header.indexOf(',', i);
        if (end < 0) end = length;
        value = header.substring(i, end).strip();
        i = end;
      }
      while (i < length && header.charAt(i) == ' ') i++;
      if (i < length && header.charAt(i) != ',') throw malformed();
      if (params.put(name, value) != null) throw malformed();
    }
    return params;
  }

  private static HttpSignatureException malformed() {
    return new HttpSignatureException(
        HttpSignatureException.Reason.MALFORMED, "Unreadable Signature header");
  }
}
