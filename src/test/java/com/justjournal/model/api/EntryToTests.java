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
package com.justjournal.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.model.Entry;
import org.junit.jupiter.api.Test;

/** Entries posted to /api/entry are deserialized by Jackson; omitted fields must keep their defaults. */
class EntryToTests {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void omittedFieldsKeepDefaults() throws Exception {
    final EntryTo to = mapper.readValue("{\"subject\":\"hi\",\"body\":\"text\"}", EntryTo.class);

    assertEquals("", to.getMusic());
    assertEquals("", to.getTag());
    assertTrue(to.getTags().isEmpty());
    assertEquals("TEXT", to.getFormat());
    assertTrue(to.getAllowComments());
    assertNotNull(to.getDate());
  }

  @Test
  void nullTagsBecomeEmpty() throws Exception {
    final EntryTo to =
        mapper.readValue("{\"body\":\"text\",\"tag\":null,\"tags\":null}", EntryTo.class);

    assertEquals("", to.getTag());
    assertTrue(to.getTags().isEmpty());
  }

  @Test
  void minimalJsonConvertsToEntry() throws Exception {
    final EntryTo to = mapper.readValue("{\"body\":\"text\"}", EntryTo.class);

    final Entry entry = new Entry(to);

    assertEquals("", entry.getMusic());
    assertEquals("text", entry.getBody());
  }
}
