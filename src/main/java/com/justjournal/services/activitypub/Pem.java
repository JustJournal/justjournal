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

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA keys as PEM text: public keys as X.509 SubjectPublicKeyInfo ({@code PUBLIC KEY}), which is
 * what Mastodon and other fediverse servers publish, private keys as PKCS#8 ({@code PRIVATE KEY}).
 */
public final class Pem {
  private static final String PUBLIC = "PUBLIC KEY";
  private static final String PRIVATE = "PRIVATE KEY";

  private Pem() {}

  public static String encode(final PublicKey key) {
    return encode(PUBLIC, key.getEncoded());
  }

  public static String encode(final PrivateKey key) {
    return encode(PRIVATE, key.getEncoded());
  }

  /**
   * @throws GeneralSecurityException if the text is not a PEM encoded RSA public key
   */
  public static PublicKey publicKey(final String pem) throws GeneralSecurityException {
    return KeyFactory.getInstance("RSA")
        .generatePublic(new X509EncodedKeySpec(decode(PUBLIC, pem)));
  }

  /**
   * @throws GeneralSecurityException if the text is not a PEM encoded PKCS#8 RSA private key
   */
  public static PrivateKey privateKey(final String pem) throws GeneralSecurityException {
    return KeyFactory.getInstance("RSA")
        .generatePrivate(new PKCS8EncodedKeySpec(decode(PRIVATE, pem)));
  }

  private static String encode(final String label, final byte[] der) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
        + "\n-----END "
        + label
        + "-----\n";
  }

  private static byte[] decode(final String label, final String pem)
      throws GeneralSecurityException {
    if (pem == null) throw new GeneralSecurityException("No key");
    final String begin = "-----BEGIN " + label + "-----";
    final String end = "-----END " + label + "-----";
    final int from = pem.indexOf(begin);
    final int to = pem.indexOf(end);
    if (from < 0 || to < from) throw new GeneralSecurityException("Not a PEM " + label);
    try {
      return Base64.getMimeDecoder().decode(pem.substring(from + begin.length(), to));
    } catch (final IllegalArgumentException e) {
      throw new GeneralSecurityException("Bad base64 in PEM " + label, e);
    }
  }
}
