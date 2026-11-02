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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.core.Settings;
import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.NodeInfoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * NodeInfo 2.0 and 2.1 (https://nodeinfo.diaspora.software/), which fediverse servers and
 * directories read to learn what software a site runs and how big it is. The figures count every
 * account, not only the journals that federate. There is nothing to say while federation is off,
 * so everything here is a 404 then.
 *
 * @author Lucas Holt
 */
@RestController
public class NodeInfoController {
  static final String SCHEMA = "http://nodeinfo.diaspora.software/ns/schema/";
  static final String SOFTWARE = "justjournal";
  static final String REPOSITORY = "https://github.com/JustJournal/justjournal";
  static final String HOMEPAGE = "https://www.justjournal.com/";

  private final Settings settings;
  private final FederationPolicy federationPolicy;
  private final NodeInfoService nodeInfoService;
  private final ObjectProvider<BuildProperties> buildProperties;

  public NodeInfoController(
      final Settings settings,
      final FederationPolicy federationPolicy,
      final NodeInfoService nodeInfoService,
      final ObjectProvider<BuildProperties> buildProperties) {
    this.settings = settings;
    this.federationPolicy = federationPolicy;
    this.nodeInfoService = nodeInfoService;
    this.buildProperties = buildProperties;
  }

  /** Where the documents are, newest schema last. */
  @GetMapping(value = "/.well-known/nodeinfo", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> discovery() {
    if (!federationPolicy.isEnabled()) return ResponseEntity.notFound().build();
    final ObjectNode discovery = JsonNodeFactory.instance.objectNode();
    final var links = discovery.putArray("links");
    for (final String version : new String[] {"2.0", "2.1"}) {
      links
          .addObject()
          .put("rel", SCHEMA + version)
          .put("href", base() + "nodeinfo/" + version);
    }
    return ok(discovery, MediaType.APPLICATION_JSON);
  }

  @GetMapping(
      value = "/nodeinfo/{version:2\\.[01]}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> nodeInfo(@PathVariable("version") final String version) {
    if (!federationPolicy.isEnabled()) return ResponseEntity.notFound().build();
    final ObjectNode info = JsonNodeFactory.instance.objectNode();
    info.put("version", version);

    final ObjectNode software = info.putObject("software");
    software.put("name", SOFTWARE);
    software.put("version", softwareVersion());
    if ("2.1".equals(version)) {
      software.put("repository", REPOSITORY);
      software.put("homepage", HOMEPAGE);
    }

    info.putArray("protocols").add("activitypub");
    final ObjectNode services = info.putObject("services");
    services.putArray("inbound");
    services.putArray("outbound").add("atom1.0").add("rss2.0");
    info.put("openRegistrations", settings.isUserAllowNew());

    final NodeInfoService.Usage figures = nodeInfoService.usage();
    final ObjectNode usage = info.putObject("usage");
    usage
        .putObject("users")
        .put("total", figures.users())
        .put("activeHalfyear", figures.activeHalfyear())
        .put("activeMonth", figures.activeMonth());
    usage.put("localPosts", figures.localPosts());

    final ObjectNode metadata = info.putObject("metadata");
    final String siteName = settings.getSiteName();
    if (siteName != null && !siteName.isBlank()) metadata.put("nodeName", siteName);

    return ok(
        info, MediaType.parseMediaType("application/json; profile=\"" + SCHEMA + version + "#\""));
  }

  /** The version Maven built, or "unknown" when there is no build information (as in an IDE). */
  private String softwareVersion() {
    final BuildProperties build = buildProperties.getIfAvailable();
    return build == null || build.getVersion() == null ? "unknown" : build.getVersion();
  }

  /** Same as {@link WebFingerController}: always https. */
  private String base() {
    return settings.getBaseUri().replace("http://", "https://");
  }

  /** Public figures, which browser-based tools may read too. */
  private static ResponseEntity<JsonNode> ok(final JsonNode body, final MediaType type) {
    return ResponseEntity.ok()
        .contentType(type)
        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        .body(body);
  }
}
