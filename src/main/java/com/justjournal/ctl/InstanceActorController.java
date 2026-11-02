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

import static com.justjournal.ctl.ActivityStreamController.ACTIVITY_JSON;
import static com.justjournal.ctl.ActivityStreamController.ACTIVITY_JSON_VALUE;
import static com.justjournal.ctl.ActivityStreamController.AS_CONTEXT;
import static com.justjournal.ctl.ActivityStreamController.LD_JSON_VALUE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.core.Settings;
import com.justjournal.services.activitypub.ActorKeyService;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The instance actor: an Application standing for the site itself. Requests made on behalf of the
 * site rather than a user, such as fetching another server's key, are signed with its key, and
 * servers that check signatures fetch this document to find that key. Its inbox is in {@link
 * InboxController}.
 *
 * @author Lucas Holt
 */
@RestController
public class InstanceActorController {
  static final String PATH = "/actor";

  private final Settings settings;
  private final ActorKeyService actorKeyService;

  public InstanceActorController(
      final Settings settings, final ActorKeyService actorKeyService) {
    this.settings = settings;
    this.actorKeyService = actorKeyService;
  }

  @GetMapping(
      value = PATH,
      produces = {ACTIVITY_JSON_VALUE, LD_JSON_VALUE})
  public ResponseEntity<JsonNode> actor() {
    final String id = actorKeyService.instanceActorId();
    final ObjectNode actor = ActivityStreamController.withKeyContext();
    actor.put("id", id);
    actor.put("type", "Application");
    actor.put("preferredUsername", host());
    final String siteName = settings.getSiteName();
    if (siteName != null && !siteName.isBlank()) actor.put("name", siteName);
    actor.put("url", base());
    actor.put("inbox", id + "/inbox");
    actor.put("outbox", id + "/outbox");
    actor.put("manuallyApprovesFollowers", true);
    ActivityStreamController.putPublicKey(
        actor, id, actorKeyService.forInstance().getPublicKeyPem());
    return ok(actor);
  }

  /** The instance actor posts nothing. */
  @GetMapping(
      value = PATH + "/outbox",
      produces = {ACTIVITY_JSON_VALUE, LD_JSON_VALUE})
  public ResponseEntity<JsonNode> outbox() {
    final ObjectNode outbox = JsonNodeFactory.instance.objectNode();
    outbox.put("@context", AS_CONTEXT);
    outbox.put("id", actorKeyService.instanceActorId() + "/outbox");
    outbox.put("type", "OrderedCollection");
    outbox.put("totalItems", 0);
    outbox.putArray("orderedItems");
    return ok(outbox);
  }

  /** The site's host name, which is also the instance actor's name. */
  private String host() {
    return URI.create(base()).getHost().toLowerCase(Locale.ROOT);
  }

  /** Same as {@link WebFingerController}: ids are always https. */
  private String base() {
    return settings.getBaseUri().replace("http://", "https://");
  }

  private static ResponseEntity<JsonNode> ok(final JsonNode body) {
    return ResponseEntity.ok().contentType(ACTIVITY_JSON).body(body);
  }
}
