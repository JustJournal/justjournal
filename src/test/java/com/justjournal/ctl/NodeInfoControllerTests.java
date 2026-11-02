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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.core.Settings;
import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.NodeInfoService;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NodeInfoControllerTests {

  private final ObjectMapper mapper = new ObjectMapper();
  private Settings settings;
  private FederationPolicy federationPolicy;
  private ObjectProvider<BuildProperties> buildProperties;
  private MockMvc mockMvc;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    when(settings.getSiteName()).thenReturn("JustJournal");
    when(settings.isUserAllowNew()).thenReturn(true);
    federationPolicy = mock(FederationPolicy.class);
    when(federationPolicy.isEnabled()).thenReturn(true);
    final NodeInfoService nodeInfoService = mock(NodeInfoService.class);
    when(nodeInfoService.usage()).thenReturn(new NodeInfoService.Usage(120, 7, 19, 4321));
    buildProperties = mock(ObjectProvider.class);
    final Properties build = new Properties();
    build.setProperty("version", "3.2.1");
    when(buildProperties.getIfAvailable()).thenReturn(new BuildProperties(build));

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new NodeInfoController(
                    settings, federationPolicy, nodeInfoService, buildProperties))
            .build();
  }

  private MvcResult fetch(final String path) throws Exception {
    return mockMvc
        .perform(get(path).header(HttpHeaders.ACCEPT, "application/json"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
        .andReturn();
  }

  private JsonNode json(final MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void discovery_linksBothSchemasOverHttps() throws Exception {
    final JsonNode links = json(fetch("/.well-known/nodeinfo")).get("links");

    assertEquals(2, links.size());
    assertEquals(NodeInfoController.SCHEMA + "2.0", links.get(0).get("rel").asText());
    assertEquals(
        "https://www.justjournal.com/nodeinfo/2.0", links.get(0).get("href").asText());
    assertEquals(NodeInfoController.SCHEMA + "2.1", links.get(1).get("rel").asText());
    assertEquals(
        "https://www.justjournal.com/nodeinfo/2.1", links.get(1).get("href").asText());
  }

  @Test
  void nodeInfo21_describesTheSiteAndCountsEveryUser() throws Exception {
    final MvcResult result = fetch("/nodeinfo/2.1");
    assertEquals(
        "application/json;profile=\"" + NodeInfoController.SCHEMA + "2.1#\"",
        result.getResponse().getContentType().replace(" ", ""));
    final JsonNode info = json(result);

    assertEquals("2.1", info.get("version").asText());
    assertEquals("justjournal", info.at("/software/name").asText());
    assertEquals("3.2.1", info.at("/software/version").asText());
    assertEquals(NodeInfoController.REPOSITORY, info.at("/software/repository").asText());
    assertEquals(NodeInfoController.HOMEPAGE, info.at("/software/homepage").asText());
    assertEquals("activitypub", info.at("/protocols/0").asText());
    assertEquals(1, info.get("protocols").size());
    assertEquals(0, info.at("/services/inbound").size());
    assertEquals("atom1.0", info.at("/services/outbound/0").asText());
    assertEquals("rss2.0", info.at("/services/outbound/1").asText());
    assertTrue(info.get("openRegistrations").asBoolean());
    assertEquals(120, info.at("/usage/users/total").asLong());
    assertEquals(19, info.at("/usage/users/activeHalfyear").asLong());
    assertEquals(7, info.at("/usage/users/activeMonth").asLong());
    assertEquals(4321, info.at("/usage/localPosts").asLong());
    assertEquals("JustJournal", info.at("/metadata/nodeName").asText());
  }

  @Test
  void nodeInfo20_leavesOutWhat21Added() throws Exception {
    final MvcResult result = fetch("/nodeinfo/2.0");
    assertTrue(result.getResponse().getContentType().contains(NodeInfoController.SCHEMA + "2.0#"));
    final JsonNode info = json(result);

    assertEquals("2.0", info.get("version").asText());
    assertFalse(info.get("software").has("repository"));
    assertFalse(info.get("software").has("homepage"));
    assertEquals(120, info.at("/usage/users/total").asLong());
  }

  @Test
  void closedSignUpAndNoBuildInformation() throws Exception {
    when(settings.isUserAllowNew()).thenReturn(false);
    when(settings.getSiteName()).thenReturn("");
    when(buildProperties.getIfAvailable()).thenReturn(null);

    final JsonNode info = json(fetch("/nodeinfo/2.1"));

    assertFalse(info.get("openRegistrations").asBoolean());
    assertEquals("unknown", info.at("/software/version").asText());
    assertFalse(info.get("metadata").has("nodeName"));
  }

  @Test
  void everythingIsNotFoundWhileFederationIsOff() throws Exception {
    when(federationPolicy.isEnabled()).thenReturn(false);

    for (final String path :
        new String[] {"/.well-known/nodeinfo", "/nodeinfo/2.0", "/nodeinfo/2.1"}) {
      mockMvc.perform(get(path)).andExpect(status().isNotFound());
    }
  }

  @Test
  void otherSchemaVersionsAreNotServed() throws Exception {
    mockMvc.perform(get("/nodeinfo/1.0")).andExpect(status().isNotFound());
    mockMvc.perform(get("/nodeinfo/2.2")).andExpect(status().isNotFound());
  }
}
