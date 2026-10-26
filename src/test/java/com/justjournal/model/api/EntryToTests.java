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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.mediatype.MessageResolver;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.mediatype.hal.Jackson2HalModule;
import org.springframework.hateoas.server.core.AnnotationLinkRelationProvider;
import org.springframework.hateoas.server.core.DelegatingLinkRelationProvider;
import org.springframework.hateoas.server.core.EvoInflectorLinkRelationProvider;

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

  /** Mirrors /api/entry/{username}/size/{size}/page/{page}, which clients read as HAL. */
  @Test
  void pagedEntriesRenderAsHal() throws Exception {
    final ObjectMapper hal = new ObjectMapper();
    hal.registerModule(new Jackson2HalModule());
    hal.setHandlerInstantiator(
        new Jackson2HalModule.HalHandlerInstantiator(
            new DelegatingLinkRelationProvider(
                new AnnotationLinkRelationProvider(), new EvoInflectorLinkRelationProvider()),
            CurieProvider.NONE,
            MessageResolver.DEFAULTS_ONLY,
            new HalConfiguration(),
            new DefaultListableBeanFactory()));

    final EntryTo entry = EntryTo.builder().entryId(7).subject("hi").body("text").build();
    final PagedModel<EntryTo> page =
        PagedModel.of(
            List.of(entry),
            new PagedModel.PageMetadata(20, 0, 1, 1),
            List.of(Link.of("/api/entry/laffer1/size/20/page/0")));

    final var json = hal.readTree(hal.writeValueAsString(page));

    assertEquals(7, json.at("/_embedded/entryToList/0/entryId").asInt());
    assertEquals("hi", json.at("/_embedded/entryToList/0/subject").asText());
    assertEquals(1, json.at("/page/totalElements").asInt());
    assertEquals(0, json.at("/page/number").asInt());
  }
}
