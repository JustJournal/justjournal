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
package com.justjournal.ctl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.core.Settings;
import com.justjournal.model.ActorKey;
import com.justjournal.services.activitypub.ActorKeyService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstanceActorControllerTests {

  private static final String ACTOR = "https://www.justjournal.com/actor";
  private static final String PEM =
      "-----BEGIN PUBLIC KEY-----\nMIIBIjANBg...\n-----END PUBLIC KEY-----\n";

  private final ObjectMapper mapper = new ObjectMapper();
  private Settings settings;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    when(settings.getSiteName()).thenReturn("JustJournal");
    final ActorKeyService actorKeyService = mock(ActorKeyService.class);
    when(actorKeyService.instanceActorId()).thenReturn(ACTOR);
    final ActorKey key = new ActorKey();
    key.setKeyName(ActorKeyService.INSTANCE);
    key.setPublicKeyPem(PEM);
    key.setPrivateKeyPem("never shown");
    when(actorKeyService.forInstance()).thenReturn(key);

    mockMvc =
        MockMvcBuilders.standaloneSetup(new InstanceActorController(settings, actorKeyService))
            .build();
  }

  private JsonNode json(final String path, final String accept) throws Exception {
    return mapper.readTree(
        mockMvc
            .perform(get(path).header(HttpHeaders.ACCEPT, accept))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/activity+json"))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void actor_isAnApplicationWithTheSiteKey() throws Exception {
    for (final String accept :
        List.of(
            "application/activity+json",
            "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"")) {
      final JsonNode actor = json("/actor", accept);

      assertEquals(
          List.of("https://www.w3.org/ns/activitystreams", "https://w3id.org/security/v1"),
          List.of(actor.get("@context").get(0).asText(), actor.get("@context").get(1).asText()));
      assertEquals(ACTOR, actor.get("id").asText());
      assertEquals("Application", actor.get("type").asText());
      assertEquals("www.justjournal.com", actor.get("preferredUsername").asText());
      assertEquals("JustJournal", actor.get("name").asText());
      assertEquals("https://www.justjournal.com/", actor.get("url").asText());
      assertEquals(ACTOR + "/inbox", actor.get("inbox").asText());
      assertEquals(ACTOR + "/outbox", actor.get("outbox").asText());
      assertTrue(actor.get("manuallyApprovesFollowers").asBoolean());
      final JsonNode publicKey = actor.get("publicKey");
      assertEquals(ACTOR + "#main-key", publicKey.get("id").asText());
      assertEquals(ACTOR, publicKey.get("owner").asText());
      assertEquals(PEM, publicKey.get("publicKeyPem").asText());
      assertTrue(!actor.toString().contains("never shown"));
    }
  }

  @Test
  void actor_leavesOutABlankSiteName() throws Exception {
    when(settings.getSiteName()).thenReturn("");
    assertTrue(!json("/actor", "application/activity+json").has("name"));
  }

  @Test
  void actor_isOnlyActivityStreams() throws Exception {
    mockMvc
        .perform(get("/actor").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void inbox_takesNothing() throws Exception {
    mockMvc
        .perform(
            post("/actor/inbox").contentType("application/activity+json").content("{}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, ""));
    mockMvc.perform(get("/actor/inbox")).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void outbox_isEmpty() throws Exception {
    final JsonNode outbox = json("/actor/outbox", "application/activity+json");
    assertEquals("https://www.w3.org/ns/activitystreams", outbox.get("@context").asText());
    assertEquals(ACTOR + "/outbox", outbox.get("id").asText());
    assertEquals("OrderedCollection", outbox.get("type").asText());
    assertEquals(0, outbox.get("totalItems").asInt());
    assertEquals(0, outbox.get("orderedItems").size());
  }
}
