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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.justjournal.model.api.CommentTo;
import java.util.Date;
import org.junit.jupiter.api.Test;

/** Comment validates its fields and converts to the API shape. */
class CommentTests {

  @Test
  void settersValidate() {
    final Comment comment = new Comment();
    assertThrows(IllegalArgumentException.class, () -> comment.setId(-1));
    assertThrows(IllegalArgumentException.class, () -> comment.setBody("x"));

    comment.setId(3);
    comment.setEid(9);
    comment.setSubject("");
    comment.setBody("ok");

    assertEquals(3, comment.getId());
    assertEquals(9, comment.getEid());
    assertEquals("(no subject)", comment.getSubject());
    assertEquals("ok", comment.getBody());

    comment.setSubject("Hello");
    assertEquals("Hello", comment.getSubject());
  }

  @Test
  void bodyViews_linkUrisAndStripTags() {
    final Comment comment = new Comment();
    comment.setBody("see https://example.com/ now");
    final String linked = comment.getBodyWithLinks();
    org.junit.jupiter.api.Assertions.assertTrue(linked.startsWith("see <a href=\"https://example.com/\">https://example.com/</a>"), linked);

    comment.setBody("<b>bold</b> text");
    assertEquals("bold text", comment.getBodyWithoutHTML());
  }

  @Test
  void toCommentTo_copiesTheFields() {
    final User user = new User();
    user.setUsername("ada");
    final Date date = new Date(1_700_000_000_000L);
    final Comment comment = new Comment();
    comment.setId(5);
    comment.setSubject("Re: hi");
    comment.setBody("body text");
    comment.setDate(date);
    comment.setUser(user);
    final Entry entry = new Entry();
    comment.setEntry(entry);

    final CommentTo to = comment.toCommentTo();

    assertEquals(5, to.getId());
    assertEquals("Re: hi", to.getSubject());
    assertEquals("body text", to.getBody());
    assertEquals(date, to.getDate());
    assertEquals("ada", to.getUsername());
    assertEquals(comment.getFormat().toString(), to.getFormat());
    assertEquals(entry, comment.getEntry());

    comment.setUser(null);
    assertNull(comment.toCommentTo().getUsername());
  }
}
