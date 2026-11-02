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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.Login;
import com.justjournal.model.Entry;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.activitypub.ActivityStreamsObjects;
import com.justjournal.services.activitypub.FollowerService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read only ActivityPub: a journal as a Person actor, its public entries as objects, and an outbox
 * of them, so fediverse servers can look up a profile and its posts. The actor and entry share
 * their URL with the HTML pages in {@link UsersController}; which one answers depends on the Accept
 * header.
 *
 * <p>The JSON itself is built by {@link ActivityStreamsObjects}, which also builds what {@link
 * com.justjournal.services.activitypub.ActivityPublisher} delivers to followers when a journal that
 * federates changes: a Create when an entry becomes public, an Update when a public entry or the
 * profile changes, and a Delete when a public entry is deleted or hidden.
 *
 * @author Lucas Holt
 */
@RestController
@Transactional(readOnly = true)
public class ActivityStreamController {
  public static final String ACTIVITY_JSON_VALUE = "application/activity+json";
  public static final String LD_JSON_VALUE = "application/ld+json";
  static final MediaType ACTIVITY_JSON = MediaType.parseMediaType(ACTIVITY_JSON_VALUE);

  static final String AS_CONTEXT = ActivityStreamsObjects.AS_CONTEXT;
  /** Defines publicKey and publicKeyPem; documents that carry a key name it too. */
  static final String SECURITY_CONTEXT = ActivityStreamsObjects.SECURITY_CONTEXT;
  static final String PUBLIC = ActivityStreamsObjects.PUBLIC;

  /** Titled entries are articles. */
  static final String TYPE_ARTICLE = ActivityStreamsObjects.TYPE_ARTICLE;

  /** Entries without a subject are short posts. */
  static final String TYPE_NOTE = ActivityStreamsObjects.TYPE_NOTE;

  /** Entries per outbox page, the same as the JSON feed. */
  static final int PAGE_SIZE = 15;

  private static final String PATH_USERNAME = "username";
  private static final String USERS = "users/";

  private final UserRepository userRepository;
  private final EntryRepository entryRepository;
  private final EntryAccessService entryAccessService;
  private final EntryAttachmentService entryAttachmentService;
  private final FollowerService followerService;
  private final ActivityStreamsObjects objects;

  public ActivityStreamController(
      final UserRepository userRepository,
      final EntryRepository entryRepository,
      final EntryAccessService entryAccessService,
      final EntryAttachmentService entryAttachmentService,
      final FollowerService followerService,
      final ActivityStreamsObjects objects) {
    this.userRepository = userRepository;
    this.entryRepository = entryRepository;
    this.entryAccessService = entryAccessService;
    this.entryAttachmentService = entryAttachmentService;
    this.followerService = followerService;
    this.objects = objects;
  }

  /**
   * The old address of the actor. It never served an actor, so send everyone to the one WebFinger
   * gives out, for good.
   */
  @GetMapping("/@/{username}")
  public ResponseEntity<Void> legacy(@PathVariable(PATH_USERNAME) final String username) {
    if (!Login.isUserName(username)) return ResponseEntity.notFound().build();
    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
        .header(HttpHeaders.LOCATION, base() + USERS + username)
        .build();
  }

  @GetMapping(
      value = "/users/{username}",
      produces = {ACTIVITY_JSON_VALUE, LD_JSON_VALUE})
  public ResponseEntity<JsonNode> actor(@PathVariable(PATH_USERNAME) final String username) {
    final User user = publicUser(username);
    if (user == null) return notFound();

    final ObjectNode actor = objects.actor(user);
    return ok(actor);
  }

  /**
   * Nothing is accepted here yet. 405 with an empty Allow header says the resource exists but
   * takes no methods; other servers treat a 4xx as final, so they don't keep retrying a Follow as
   * they would after a 5xx, and nothing claims the activity was taken.
   */
  @RequestMapping("/users/{username}/inbox")
  public ResponseEntity<Void> inbox(@PathVariable(PATH_USERNAME) final String username) {
    if (publicUser(username) == null) return ResponseEntity.notFound().build();
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .header(HttpHeaders.ALLOW, "")
        .build();
  }

  @GetMapping(
      value = "/users/{username}/followers",
      produces = {ACTIVITY_JSON_VALUE, LD_JSON_VALUE})
  public ResponseEntity<JsonNode> followers(@PathVariable(PATH_USERNAME) final String username) {
    final User user = publicUser(username);
    if (user == null) return notFound();

    // how many, but not who: the list of followers stays private
    final ObjectNode collection = withContext();
    collection.put("id", actorId(user) + "/followers");
    collection.put("type", "OrderedCollection");
    collection.put("totalItems", followerService.count(user));
    return ok(collection);
  }

  @GetMapping(
      value = "/users/{username}/following",
      produces = {ACTIVITY_JSON_VALUE, LD_JSON_VALUE})
  public ResponseEntity<JsonNode> following(@PathVariable(PATH_USERNAME) final String username) {
    return emptyCollection(username, "/following");
  }

  /** Public entries, newest first: the collection itself, or one page of it when asked. */
  @GetMapping(
      value = "/users/{username}/outbox",
      produces = {ACTIVITY_JSON_VALUE, LD_JSON_VALUE})
  public ResponseEntity<JsonNode> outbox(
      @PathVariable(PATH_USERNAME) final String username,
      @RequestParam(value = "page", required = false) final Integer page) {
    final User user = publicUser(username);
    if (user == null) return notFound();

    final String outbox = actorId(user) + "/outbox";
    if (page == null) {
      final ObjectNode collection = withContext();
      collection.put("id", outbox);
      collection.put("type", "OrderedCollection");
      collection.put("totalItems", publicEntries(user, 0, 1).getTotalElements());
      collection.put("first", outbox + "?page=1");
      return ok(collection);
    }

    if (page < 1) return notFound();
    final Page<Entry> entries = publicEntries(user, page - 1, PAGE_SIZE);
    if (page > 1 && entries.getContent().isEmpty()) return notFound();

    final Map<Integer, FeedAttachments> attachments =
        entryAttachmentService.forFeed(entries.getContent());
    final ObjectNode result = withContext();
    result.put("id", outbox + "?page=" + page);
    result.put("type", "OrderedCollectionPage");
    result.put("partOf", outbox);
    final ArrayNode items = result.putArray("orderedItems");
    for (final Entry entry : entries.getContent()) {
      items.add(objects.create(entry, attachments.get(entry.getId())));
    }
    if (entries.hasNext()) result.put("next", outbox + "?page=" + (page + 1));
    if (page > 1) result.put("prev", outbox + "?page=" + (page - 1));
    return ok(result);
  }

  @GetMapping(
      value = "/users/{username}/entry/{id}",
      produces = {ACTIVITY_JSON_VALUE, LD_JSON_VALUE})
  public ResponseEntity<JsonNode> entry(
      @PathVariable(PATH_USERNAME) final String username, @PathVariable("id") final int id) {
    final Entry entry = entryRepository.findById(id).orElse(null);
    if (entry == null
        || !entryAccessService.isPubliclyVisible(entry)
        || !entry.getUser().getUsername().equalsIgnoreCase(username)) {
      return notFound();
    }

    return ok(objects.entry(entry, entryAttachmentService.forFeed(List.of(entry)).get(id)));
  }

  /**
   * Which ActivityStreams type an entry is published as: titled entries are articles, the rest
   * notes.
   */
  static String objectType(final Entry entry) {
    return ActivityStreamsObjects.objectType(entry);
  }

  private ResponseEntity<JsonNode> emptyCollection(final String username, final String path) {
    final User user = publicUser(username);
    if (user == null) return notFound();

    final ObjectNode collection = withContext();
    collection.put("id", actorId(user) + path);
    collection.put("type", "OrderedCollection");
    collection.put("totalItems", 0);
    return ok(collection);
  }

  private Page<Entry> publicEntries(final User user, final int page, final int size) {
    return entryRepository.findByUserAndSecurityAndDraft(
        user,
        Security.PUBLIC,
        PrefBool.N,
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"))));
  }

  /** The user, when anyone may read their journal. */
  private User publicUser(final String username) {
    if (!Login.isUserName(username)) return null;
    final User user = userRepository.findByUsername(username);
    if (user == null || user.getJournals().isEmpty()) return null;
    if (user.getJournals().stream().anyMatch(Journal::isOwnerViewOnly)) return null;
    return user;
  }

  /** The address WebFinger gives out for the user, which is also their journal's page. */
  private String actorId(final User user) {
    return objects.actorId(user);
  }

  /** Same as {@link WebFingerController}: ids are always https. */
  private String base() {
    return objects.base();
  }

  private static ObjectNode withContext() {
    return ActivityStreamsObjects.withContext();
  }

  /** The context of a document that carries a public key. */
  static ObjectNode withKeyContext() {
    return ActivityStreamsObjects.withKeyContext();
  }

  /** The actor's key, which other servers check our signatures with. */
  static void putPublicKey(final ObjectNode actor, final String actorId, final String pem) {
    ActivityStreamsObjects.putPublicKey(actor, actorId, pem);
  }

  /** The actor and entry URLs also serve HTML, so caches must keep the two apart. */
  private static ResponseEntity<JsonNode> ok(final JsonNode body) {
    return ResponseEntity.ok()
        .contentType(ACTIVITY_JSON)
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
        .body(body);
  }

  private static ResponseEntity<JsonNode> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
        .build();
  }
}
