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
package com.justjournal.services.link;

/**
 * A link failed one of the safety checks. The message is meant for the writer and never contains
 * anything they didn't type themselves, apart from the host name.
 *
 * <p>This is a checked exception on purpose: every caller has to decide what a refused link means
 * for it (a 400 on save, a BLOCKED preview, a dropped redirect), and the compiler makes sure none
 * of them forgets.
 */
public class LinkRejectedException extends Exception {

  /** Which check refused the link. */
  public enum Reason {
    SYNTAX,
    SHORTENER,
    BLOCKLISTED,
    PRIVATE_ADDRESS,
    DNS,
    DBL,
    SAFE_BROWSING
  }

  static final String SYNTAX_MESSAGE = "Link URL is not valid.";
  /** Same wording the sidebar link validator has always used. */
  public static final String SHORTENER_MESSAGE =
      "Links from URL shortening services are not allowed. Use the full URL.";
  static final String UNSAFE_MESSAGE =
      "This link can't be added because the site is listed as unsafe.";
  static final String PRIVATE_MESSAGE = "Links must point to a public web site.";

  private final Reason reason;

  public LinkRejectedException(final Reason reason, final String userMessage) {
    super(userMessage);
    this.reason = reason;
  }

  /**
   * A rejection with the standard message for its reason.
   *
   * @param reason which check failed
   * @param host the ASCII host name, used only in the DNS message; may be null otherwise
   * @return the exception, ready to throw
   */
  public static LinkRejectedException of(final Reason reason, final String host) {
    return new LinkRejectedException(reason, messageFor(reason, host));
  }

  static String messageFor(final Reason reason, final String host) {
    return switch (reason) {
      case SYNTAX -> SYNTAX_MESSAGE;
      case SHORTENER -> SHORTENER_MESSAGE;
      case BLOCKLISTED, DBL, SAFE_BROWSING -> UNSAFE_MESSAGE;
      case PRIVATE_ADDRESS -> PRIVATE_MESSAGE;
      case DNS -> "The domain " + host + " could not be found.";
    };
  }

  /** Which check refused the link. */
  public Reason reason() {
    return reason;
  }
}
