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
package com.justjournal.services.activitypub;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.Journal;
import com.justjournal.model.User;
import com.justjournal.model.UserBio;
import com.justjournal.model.UserPic;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.services.AvatarService;
import com.justjournal.services.EntryService;
import com.justjournal.utility.DateConvert;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

/**
 * The ActivityStreams JSON for a journal and its entries: the actor, an entry as an object, and the
 * activities that carry them. The pages that serve them and the activities delivered to followers
 * are built here, so both say the same thing about an entry.
 *
 * <p>Nothing here checks who may see what; callers only pass users whose journal anyone may read
 * and entries that are publicly visible.
 *
 * @author Lucas Holt
 */
@Component
public class ActivityStreamsObjects {
  public static final String AS_CONTEXT = "https://www.w3.org/ns/activitystreams";

  /** Defines publicKey and publicKeyPem; documents that carry a key name it too. */
  public static final String SECURITY_CONTEXT = "https://w3id.org/security/v1";

  public static final String PUBLIC = AS_CONTEXT + "#Public";

  /** Titled entries are articles. */
  public static final String TYPE_ARTICLE = "Article";

  /** Entries without a subject are short posts. */
  public static final String TYPE_NOTE = "Note";

  private static final String USERS = "users/";
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  private final Settings settings;
  private final EntryService entryService;
  private final AvatarService avatarService;
  private final UserPicRepository userPicRepository;
  private final ActorKeyService actorKeyService;

  public ActivityStreamsObjects(
      final Settings settings,
      final EntryService entryService,
      final AvatarService avatarService,
      final UserPicRepository userPicRepository,
      final ActorKeyService actorKeyService) {
    this.settings = settings;
    this.entryService = entryService;
    this.avatarService = avatarService;
    this.userPicRepository = userPicRepository;
    this.actorKeyService = actorKeyService;
  }

  /** Same as {@code WebFingerController}: ids are always https. */
  public String base() {
    return settings.getBaseUri().replace("http://", "https://");
  }

  /** The address WebFinger gives out for the user, which is also their journal's page. */
  public String actorId(final User user) {
    return base() + USERS + user.getUsername();
  }

  /** The id of one of the user's entries, which is also its page. */
  public String entryId(final User user, final int entryId) {
    return actorId(user) + "/entry/" + entryId;
  }

  /**
   * The user as a Person, with the context and public key, as served at the actor's address.
   *
   * @param user a user with a journal anyone may read
   */
  public ObjectNode actor(final User user) {
    final Journal journal =
        user.getJournals() == null || user.getJournals().isEmpty()
            ? null
            : user.getJournals().iterator().next();
    final String id = actorId(user);
    final ObjectNode actor = withKeyContext();
    actor.put("id", id);
    actor.put("type", "Person");
    actor.put("preferredUsername", user.getUsername());
    putText(
        actor,
        "name",
        journal == null || isBlank(journal.getName()) ? user.getUsername() : journal.getName());
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
    if (journal != null && journal.getSince() != null) {
      actor.put("published", DateConvert.encode3339(journal.getSince()));
    }
    actor.put("manuallyApprovesFollowers", false);
    putPublicKey(actor, id, actorKeyService.forUser(user).getPublicKeyPem());
    return actor;
  }

  /** The entry with the context, as served at its address. */
  public ObjectNode entry(final Entry entry, final FeedAttachments attachments) {
    final ObjectNode object = withContext();
    object.setAll(entryObject(entry, attachments));
    return object;
  }

  /**
   * The entry as an Article or Note, without a context so it can sit inside an activity.
   *
   * @param attachments what of its picture and link anyone may see, or null
   */
  public ObjectNode entryObject(final Entry entry, final FeedAttachments attachments) {
    final User user = entry.getUser();
    final String actor = actorId(user);
    final String id = entryId(user, entry.getId());
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
   * The Create of an entry as the outbox lists it, without a context. Its id is the same every
   * time, so the outbox and the Create delivered to followers agree.
   */
  public ObjectNode create(final Entry entry, final FeedAttachments attachments) {
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

  /** The Create of an entry, to deliver on its own. */
  public ObjectNode createActivity(final Entry entry, final FeedAttachments attachments) {
    final ObjectNode activity = withContext();
    activity.setAll(create(entry, attachments));
    return activity;
  }

  /**
   * An Update of an entry, to deliver on its own. The object's updated time is the time of the
   * change, which servers such as Mastodon need to be later than the last one they saw.
   *
   * @param when when the entry changed; also makes the activity's id unique
   */
  public ObjectNode updateActivity(
      final Entry entry, final FeedAttachments attachments, final Date when) {
    final ObjectNode object = entryObject(entry, attachments);
    final String updated = DateConvert.encode3339(when);
    object.put("updated", updated);
    final ObjectNode update = withContext();
    update.put("id", object.get("id").asText() + "#update/" + when.getTime());
    update.put("type", "Update");
    update.put("actor", object.get("attributedTo").asText());
    update.put("published", updated);
    update.set("to", object.get("to").deepCopy());
    update.set("cc", object.get("cc").deepCopy());
    update.set("object", object);
    return update;
  }

  /**
   * A Delete of an entry that anyone could see before, to deliver on its own. The object is a
   * Tombstone, since the entry may be gone or hidden.
   *
   * @param when when the entry was removed or hidden; makes the activity's id unique
   */
  public ObjectNode deleteActivity(final User user, final int entryId, final Date when) {
    final String actor = actorId(user);
    final String id = entryId(user, entryId);
    final ObjectNode delete = withContext();
    delete.put("id", id + "#delete/" + when.getTime());
    delete.put("type", "Delete");
    delete.put("actor", actor);
    delete.putArray("to").add(PUBLIC);
    delete.putArray("cc").add(actor + "/followers");
    final ObjectNode tombstone = delete.putObject("object");
    tombstone.put("id", id);
    tombstone.put("type", "Tombstone");
    return delete;
  }

  /**
   * An Update of the actor, after its name, biography or avatar changed.
   *
   * @param when when it changed; makes the activity's id unique
   */
  public ObjectNode updateActorActivity(final User user, final Date when) {
    final ObjectNode actor = actor(user);
    actor.remove("@context");
    final String id = actorId(user);
    final ObjectNode update = withKeyContext();
    update.put("id", id + "#update/" + when.getTime());
    update.put("type", "Update");
    update.put("actor", id);
    update.putArray("to").add(PUBLIC);
    update.putArray("cc").add(id + "/followers");
    update.set("object", actor);
    return update;
  }

  /**
   * Which ActivityStreams type an entry is published as: titled entries are articles, the rest
   * notes. Mastodon shows a note's text and only the title and a link for an article.
   */
  public static String objectType(final Entry entry) {
    return isBlank(entry.getSubject()) ? TYPE_NOTE : TYPE_ARTICLE;
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

  public static ObjectNode withContext() {
    final ObjectNode node = JSON.objectNode();
    node.put("@context", AS_CONTEXT);
    return node;
  }

  /** The context of a document that carries a public key. */
  public static ObjectNode withKeyContext() {
    final ObjectNode node = JSON.objectNode();
    node.putArray("@context").add(AS_CONTEXT).add(SECURITY_CONTEXT);
    return node;
  }

  /** The actor's key, which other servers check our signatures with. */
  public static void putPublicKey(final ObjectNode actor, final String actorId, final String pem) {
    final ObjectNode publicKey = actor.putObject("publicKey");
    publicKey.put("id", ActorKeyService.keyId(actorId));
    publicKey.put("owner", actorId);
    publicKey.put("publicKeyPem", pem);
  }

  private static void putText(final ObjectNode node, final String name, final String value) {
    if (!isBlank(value)) node.put(name, value);
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
