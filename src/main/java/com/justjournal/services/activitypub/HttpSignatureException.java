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

import java.io.Serial;

/** Why an HTTP signature was not accepted, or its key could not be had. */
public class HttpSignatureException extends Exception {
  @Serial private static final long serialVersionUID = 4520137416290880197L;

  public enum Reason {
    /** The request has no Signature header. */
    MISSING_SIGNATURE,
    /** The Signature header can't be parsed or lacks keyId or signature. */
    MALFORMED,
    /** An algorithm other than rsa-sha256 or hs2019, or a key that isn't RSA. */
    UNSUPPORTED_ALGORITHM,
    /** A header that must be signed is not in the signed list. */
    UNSIGNED_HEADER,
    /** A header in the signed list is not in the request. */
    MISSING_HEADER,
    /** The Digest header does not match the body, or has no SHA-256 value. */
    DIGEST_MISMATCH,
    /** The Date is unreadable or outside the allowed window, or the signature expired. */
    DATE,
    /** The key could not be fetched or read. */
    KEY_UNAVAILABLE,
    /** The fetched key belongs to someone other than who served it, or to another host. */
    KEY_MISMATCH,
    /** The signature does not verify with the key, even after fetching it again. */
    BAD_SIGNATURE
  }

  private final Reason reason;

  public HttpSignatureException(final Reason reason, final String message) {
    super(message);
    this.reason = reason;
  }

  public HttpSignatureException(final Reason reason, final String message, final Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
