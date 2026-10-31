/*
 * Copyright (c) 2003-2021 Lucas Holt
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
package com.justjournal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The error page replaces whatever output was built so far with a styled message. */
class ErrorPageTests {

  @Test
  void display_writesAFullHtmlPage() {
    final StringBuilder sb = new StringBuilder();

    ErrorPage.display("Error", "Something broke.", sb);
    final String html = sb.toString();

    assertTrue(html.startsWith("<!DOCTYPE html PUBLIC"), html);
    assertTrue(html.contains("<title>Error</title>"), html);
    assertTrue(html.contains("background: maroon"), html);
    assertTrue(html.contains("<h1 style=\"font: 72pt Arial, Helvetica, sans-serif; letter-spacing: .2in;\">Error</h1>"), html);
    assertTrue(html.contains("<p>Something broke.</p>"), html);
    assertTrue(html.endsWith("</body>\n</html>\n"), html);
  }

  @Test
  void display_discardsTheOutputBuiltSoFar() {
    final StringBuilder sb = new StringBuilder("<html><body>half a page of garbage");

    ErrorPage.display("Oops", "Try again.", sb);
    final String html = sb.toString();

    assertFalse(html.contains("half a page of garbag"), html);
    assertTrue(html.contains("<title>Oops</title>"), html);
    assertTrue(html.contains("<p>Try again.</p>"), html);
  }
}
