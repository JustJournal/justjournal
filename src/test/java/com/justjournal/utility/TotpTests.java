/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2003-2023 Lucas Holt
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
package com.justjournal.utility;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** @author Lucas Holt */
class TotpTests {

  /** The RFC 6238 appendix B test secret, "12345678901234567890" in ASCII, as base32. */
  private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  /** RFC 6238 appendix B vectors for HMAC-SHA1, trimmed to six digits. */
  @ParameterizedTest
  @CsvSource({
    "59, 287082",
    "1111111109, 081804",
    "1111111111, 050471",
    "1234567890, 005924",
    "2000000000, 279037",
    "20000000000, 353130"
  })
  void matchesRfc6238Vectors(final long time, final String expected) {
    assertEquals(expected, Totp.codeAt(RFC_SECRET, time / Totp.STEP_SECONDS));
  }

  @Test
  void acceptsTheCurrentStep() {
    assertEquals(1, Totp.matchingStep(RFC_SECRET, "287082", 59));
  }

  @Test
  void acceptsOneStepOfDriftEitherWay() {
    // step 1 is still good while the clock says step 0 or step 2
    assertEquals(1, Totp.matchingStep(RFC_SECRET, "287082", 29));
    assertEquals(1, Totp.matchingStep(RFC_SECRET, "287082", 89));
  }

  @Test
  void rejectsCodesOutsideTheWindow() {
    assertEquals(-1, Totp.matchingStep(RFC_SECRET, "287082", 120));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "12345", "1234567", "28708a", " 287082"})
  void rejectsMalformedCodes(final String code) {
    assertFalse(Totp.isCode(code));
    assertEquals(-1, Totp.matchingStep(RFC_SECRET, code, 59));
  }

  @Test
  void rejectsNullCode() {
    assertFalse(Totp.isCode(null));
    assertEquals(-1, Totp.matchingStep(RFC_SECRET, null, 59));
  }

  @Test
  void secretsAreBase32AndRandom() {
    final String a = Totp.generateSecret();
    final String b = Totp.generateSecret();
    assertEquals(32, a.length(), "20 bytes is 32 base32 characters");
    assertTrue(a.matches("[A-Z2-7]+"));
    assertNotEquals(a, b);
    assertEquals(20, Totp.decodeSecret(a).length);
  }

  @Test
  void decodesTheRfcSecretBackToAscii() {
    assertArrayEquals(
        "12345678901234567890".getBytes(StandardCharsets.US_ASCII), Totp.decodeSecret(RFC_SECRET));
    // apps show secrets lower case and in groups of four
    assertArrayEquals(
        Totp.decodeSecret(RFC_SECRET),
        Totp.decodeSecret("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"));
  }

  @Test
  void buildsTheUriAuthenticatorAppsExpect() {
    final String uri = Totp.otpauthUri("Just Journal", "jjsite", RFC_SECRET);
    assertEquals(
        "otpauth://totp/Just%20Journal:jjsite?secret="
            + RFC_SECRET
            + "&issuer=Just%20Journal&algorithm=SHA1&digits=6&period=30",
        uri);
  }
}
