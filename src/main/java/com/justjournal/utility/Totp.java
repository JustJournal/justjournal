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

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base32;

/**
 * Time based one time passwords (RFC 6238 over RFC 4226 HOTP) with the parameters every common
 * authenticator app uses: HMAC-SHA1, 30 second steps, 6 digits.
 *
 * @author Lucas Holt
 */
public final class Totp {

  public static final int DIGITS = 6;

  public static final int STEP_SECONDS = 30;

  /** Steps either side of now that are still accepted, to allow for clock drift. */
  public static final int WINDOW = 1;

  private static final int SECRET_BYTES = 20;

  private static final String HMAC = "HmacSHA1";

  private static final Pattern CODE_PATTERN = Pattern.compile("[0-9]{" + DIGITS + "}");

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private Totp() {
    super();
  }

  /**
   * @return a new random shared secret, base32 without padding
   */
  public static String generateSecret() {
    final byte[] bytes = new byte[SECRET_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base32.toBase32String(bytes).replace("=", "");
  }

  /**
   * Is the string shaped like a code from an authenticator app?
   *
   * @param code user input
   * @return true for exactly six digits
   */
  public static boolean isCode(final String code) {
    return code != null && CODE_PATTERN.matcher(code).matches();
  }

  /**
   * The code for one time step.
   *
   * @param base32Secret shared secret
   * @param step time step (unix seconds / 30)
   * @return zero padded code
   */
  public static String codeAt(final String base32Secret, final long step) {
    try {
      final Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(decodeSecret(base32Secret), HMAC));
      final byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());

      final int offset = hash[hash.length - 1] & 0x0f;
      final int binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + 3] & 0xff);

      final int otp = binary % (int) Math.pow(10, DIGITS);
      return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Check a code against the current time, allowing {@link #WINDOW} steps of drift.
   *
   * @param base32Secret shared secret
   * @param code user input
   * @param nowSeconds unix time
   * @return the time step the code matched, or -1 if it didn't
   */
  public static long matchingStep(final String base32Secret, final String code, final long nowSeconds) {
    if (!isCode(code) || base32Secret == null || base32Secret.isBlank()) return -1;

    final long current = nowSeconds / STEP_SECONDS;
    final byte[] given = code.getBytes(StandardCharsets.US_ASCII);
    long matched = -1;
    // check every step rather than returning early so timing doesn't reveal which one matched
    for (long step = current - WINDOW; step <= current + WINDOW; step++) {
      if (MessageDigest.isEqual(
          codeAt(base32Secret, step).getBytes(StandardCharsets.US_ASCII), given)) {
        matched = step;
      }
    }
    return matched;
  }

  /**
   * Build the otpauth URI authenticator apps import from a QR code.
   *
   * @param issuer site name
   * @param account username
   * @param base32Secret shared secret
   * @return otpauth://totp/... URI
   */
  public static String otpauthUri(
      final String issuer, final String account, final String base32Secret) {
    final String label = encode(issuer) + ":" + encode(account);
    return "otpauth://totp/"
        + label
        + "?secret="
        + base32Secret
        + "&issuer="
        + encode(issuer)
        + "&algorithm=SHA1&digits="
        + DIGITS
        + "&period="
        + STEP_SECONDS;
  }

  static byte[] decodeSecret(final String base32Secret) {
    final String normalized = base32Secret.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    // BouncyCastle requires padding to a multiple of 8 characters
    final int pad = (8 - normalized.length() % 8) % 8;
    return Base32.decode(normalized + "=".repeat(pad));
  }

  private static String encode(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
