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
package com.justjournal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TagTests {

  @Test
  void setName_acceptsLettersDigitsAndInnerHyphens() {
    assertEquals("news", new Tag("News").getName());
    assertEquals("windows11", new Tag("windows11").getName());
    assertEquals("open-source", new Tag("open-source").getName());
    assertEquals("2026", new Tag("2026").getName());
  }

  @Test
  void setName_rejectsAnythingElse() {
    assertThrows(IllegalArgumentException.class, () -> new Tag(""));
    assertThrows(IllegalArgumentException.class, () -> new Tag(null));
    assertThrows(IllegalArgumentException.class, () -> new Tag("-news"));
    assertThrows(IllegalArgumentException.class, () -> new Tag("news-"));
    assertThrows(IllegalArgumentException.class, () -> new Tag("open--source"));
    assertThrows(IllegalArgumentException.class, () -> new Tag("open source"));
    assertThrows(IllegalArgumentException.class, () -> new Tag("node.js"));
    assertThrows(IllegalArgumentException.class, () -> new Tag("café"));
    assertThrows(IllegalArgumentException.class, () -> new Tag("abcdefghijklmnopqrstuvwxyz12345"));
  }

  @Test
  void normalizeName_bringsRequestedTagsIntoStoredForm() {
    assertEquals("open-source", Tag.normalizeName("  Open Source "));
    assertEquals("zero-day", Tag.normalizeName("zero_day"));
    assertEquals("node-js", Tag.normalizeName("node.js"));
    assertEquals("ai-ml", Tag.normalizeName("AI/ML"));
    assertEquals("cplusplus", Tag.normalizeName("C++"));
    assertEquals("csharp", Tag.normalizeName("C#"));
    assertEquals("linux", Tag.normalizeName("#Linux"));
    assertEquals("cafe", Tag.normalizeName("café"));
    assertEquals("cve-2026-12244", Tag.normalizeName("CVE-2026-12244"));
    assertEquals("a-b", Tag.normalizeName("--a -- b--"));
  }

  @Test
  void normalizeName_returnsEmptyWhenNothingUsableIsLeft() {
    assertEquals("", Tag.normalizeName(null));
    assertEquals("", Tag.normalizeName("   "));
    assertEquals("", Tag.normalizeName("!!!"));
    assertEquals("", Tag.normalizeName("日本"));
  }
}
