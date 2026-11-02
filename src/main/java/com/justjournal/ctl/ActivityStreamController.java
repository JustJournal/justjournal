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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.model.UserBio;
import com.justjournal.model.UserPic;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.AvatarService;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.EntryService;
import com.justjournal.utility.DateConvert;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
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
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

/**
 * Read only ActivityPub: a journal as a Person actor, its public entries as objects, and an outbox
 * of them, so fediverse servers can look up a profile and its posts. The actor and entry share
 * their URL with the HTML pages in {@link UsersController}; which one answers depends on the Accept
 * header.
 *
 * <p>Nothing is delivered to other servers and the inbox does not take activities, so a journal
 * can't be followed yet.
 *
 * @author Lucas Holt
 */
@RestController
@Transactional(readOnly = true)
public class ActivityStreamController {
  public static final String ACTIVITY_JSON_VALUE = "application/activity+json";
  public static final String LD_JSON_VALUE = "application/ld+json";
  static final MediaType ACTIVITY_JSON = MediaType.parseMediaType(ACTIVITY_JSON_VALUE);

  static final String AS_CONTEXT = "https://www.w3.org/ns/activitystreams";
  static final String PUBLIC = AS_CONTEXT + "#Public";

  /** Titled entries are articles. */
  static final String TYPE_ARTICLE = "Article";

  /** Entries without a subject are short posts. */
  static final String TYPE_NOTE = "Note";

  /** Entries per outbox page, the same as the JSON feed. */
  static final int PAGE_SIZE = 15;

  private static final String PATH_USERNAME = "username";
  private static final String USERS = "users/";
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  private final Settings settings;
  private final UserRepository userRepository;
  private final EntryRepository entryRepository;
  private final EntryService entryService;
  private final EntryAccessService entryAccessService;
  private final EntryAttachmentService entryAttachmentService;
  private final AvatarService avatarService;
  private final UserPicRepository userPicRepository;

  public ActivityStreamController(
      final Settings settings,
      final UserRepository userRepository,
      final EntryRepository entryRepository,
      final EntryService entryService,
      final EntryAccessService entryAccessService,
      final EntryAttachmentService entryAttachmentService,
      final AvatarService avatarService,
      final UserPicRepository userPicRepository) {
    this.settings = settings;
    this.userRepository = userRepository;
    this.entryRepository = entryRepository;
    this.entryService = entryService;
    this.entryAccessService = entryAccessService;
    this.entryAttachmentService = entryAttachmentService;
    this.avatarService = avatarService;
    this.userPicRepository = userPicRepository;
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

    final Journal journal = user.getJournals().iterator().next();
    final String id = actorId(user);
    final ObjectNode actor = withContext();
    actor.put("id", id);
    actor.put("type", "Person");
    actor.put("preferredUsername", user.getUsername());
    putText(actor, "name", isBlank(journal.getName()) ? user.getUsername() : journal.getName());
    putText(actor, "summary", summary(user.getBio()));
    actor.put("url", id);
    if (avatarService.isAvatarAvailable(user)) {
      final ObjectNode icon = actor.putObject("icon");
      icon.put("type", "Image");
      putText(
          icon,
          "mediaType",
          userPicRepository.findById(user.getId()).map(UserPic::getMimeType).orElse(null));
      icon.put("url", base() + "Avatar/" + user.getId());
    }
    actor.put("inbox", id + "/inbox");
    actor.put("outbox", id + "/outbox");
    actor.put("followers", id + "/followers");
    actor.put("following", id + "/following");
    if (journal.getSince() != null) {
      actor.put("published", DateConvert.encode3339(journal.getSince()));
    }
    actor.put("manuallyApprovesFollowers", false);
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
    return emptyCollection(username, "/followers");
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
      items.add(create(entry, attachments.get(entry.getId())));
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

    final ObjectNode object = withContext();
    object.setAll(entryObject(entry, entryAttachmentService.forFeed(List.of(entry)).get(id)));
    return ok(object);
  }

  /**
   * Which ActivityStreams type an entry is published as: titled entries are articles, the rest
   * notes. Mastodon shows a note's text and only the title and a link for an article.
   */
  static String objectType(final Entry entry) {
    return isBlank(entry.getSubject()) ? TYPE_NOTE : TYPE_ARTICLE;
  }

  private ObjectNode create(final Entry entry, final FeedAttachments attachments) {
    final ObjectNode object = entryObject(entry, attachments);
    final ObjectNode create = JSON.objectNode();
    create.put("id", object.get("id").asText() + "#create");
    create.put("type", "Create");
    create.put("actor", object.get("attributedTo").asText());
    create.set("published", object.get("published"));
    create.set("to", object.get("to").deepCopy());
    create.set("cc", object.get("cc").deepCopy());
    create.set("object", object);
    return create;
  }

  private ObjectNode entryObject(final Entry entry, final FeedAttachments attachments) {
    final User user = entry.getUser();
    final String actor = actorId(user);
    final String id = actor + "/entry/" + entry.getId();
    final String type = objectType(entry);

    final ObjectNode object = JSON.objectNode();
    object.put("id", id);
    object.put("type", type);
    if (TYPE_ARTICLE.equals(type)) object.put("name", entry.getSubject());
    object.put("url", id);
    object.put("attributedTo", actor);
    object.putArray("to").add(PUBLIC);
    object.putArray("cc").add(actor + "/followers");
    object.put("content", content(entry, attachments));
    object.put("published", DateConvert.encode3339(entry.getDate()));
    // Mastodon marks a post edited whenever it has an updated time
    final Date modified = entry.getModified();
    if (modified != null && modified.after(entry.getDate())) {
      object.put("updated", DateConvert.encode3339(modified));
    }

    if (!entry.getTags().isEmpty()) {
      final ArrayNode tags = object.putArray("tag");
      entry.getTags().stream()
          .map(t -> t.getTag().getName())
          .sorted()
          .forEach(
              name -> {
                final ObjectNode tag = tags.addObject();
                tag.put("type", "Hashtag");
                tag.put(
                    "href",
                    actor + "/tag/" + UriUtils.encodePathSegment(name, StandardCharsets.UTF_8));
                tag.put("name", "#" + name);
              });
    }

    if (attachments != null && (attachments.hasImage() || attachments.hasLink())) {
      final ArrayNode list = object.putArray("attachment");
      if (attachments.hasImage()) {
        final ObjectNode image = list.addObject();
        image.put("type", "Image");
        putText(image, "mediaType", attachments.imageType());
        image.put("url", attachments.imageUrl());
        putText(image, "name", attachments.imageAlt());
      }
      // the link's own preview picture stays out: it isn't the author's picture
      if (attachments.hasLink()) {
        final ObjectNode link = list.addObject();
        link.put("type", "Link");
        link.put("href", attachments.linkUrl());
        putText(link, "name", attachments.linkTitle());
        link.put("mediaType", MediaType.TEXT_HTML_VALUE);
      }
    }
    return object;
  }

  /**
   * The entry as HTML. Servers such as Mastodon build a link preview from links in the content, not
   * from attachments, so the attached link goes at the end unless the entry links it already.
   */
  private String content(final Entry entry, final FeedAttachments attachments) {
    final String html =
        entry.getBody() == null
            ? ""
            : Objects.toString(
                entryService.convertBodyToHtml(entry.getFormat(), entry.getBody()), "");
    if (attachments == null || !attachments.hasLink() || linksTo(html, attachments.linkUrl())) {
      return html;
    }
    final String title =
        isBlank(attachments.linkTitle()) ? attachments.linkUrl() : attachments.linkTitle();
    return html
        + "<p><a href=\""
        + HtmlUtils.htmlEscape(attachments.linkUrl())
        + "\">"
        + HtmlUtils.htmlEscape(title)
        + "</a></p>";
  }

  private static boolean linksTo(final String html, final String url) {
    final String quoted = Pattern.quote(url) + "|" + Pattern.quote(HtmlUtils.htmlEscape(url));
    return Pattern.compile("href\\s*=\\s*[\"'](" + quoted + ")[\"']", Pattern.CASE_INSENSITIVE)
        .matcher(html)
        .find();
  }

  /** The biography is plain text; keep it that way in HTML. */
  private static String summary(final UserBio bio) {
    if (bio == null || isBlank(bio.getBio())) return null;
    return "<p>" + HtmlUtils.htmlEscape(bio.getBio().strip()).replaceAll("\\R", "<br>") + "</p>";
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
    return base() + USERS + user.getUsername();
  }

  /** Same as {@link WebFingerController}: ids are always https. */
  private String base() {
    return settings.getBaseUri().replace("http://", "https://");
  }

  private static ObjectNode withContext() {
    final ObjectNode node = JSON.objectNode();
    node.put("@context", AS_CONTEXT);
    return node;
  }

  private static void putText(final ObjectNode node, final String name, final String value) {
    if (!isBlank(value)) node.put(name, value);
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
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
