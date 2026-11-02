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

import static com.justjournal.services.activitypub.ActivityPubFetcher.sameHost;
import static com.justjournal.services.activitypub.ActivityPubFetcher.text;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.model.FollowingState;
import com.justjournal.model.RemoteFollowing;
import com.justjournal.model.RemotePost;
import com.justjournal.model.User;
import com.justjournal.repository.RemoteFollowingRepository;
import com.justjournal.repository.RemotePostRepository;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public posts by fediverse accounts that writers here follow, kept for {@code
 * activitypub.remote-posts.keep-days} to show on the followers' friends pages.
 *
 * <ul>
 *   <li>{@code Create} or {@code Update} of a Note or Article is stored when its author, who must
 *       be the sender, is followed with an accepted Follow by at least one writer, and the post is
 *       addressed to the public. Followers-only and direct posts are ignored; an Update that makes
 *       a stored post non-public removes it.
 *   <li>{@code Delete} of a stored post removes it, when its author sends it.
 *   <li>A reply to one of this site's entries is left alone: it is a comment, not a post.
 * </ul>
 *
 * <p>Content is cleaned down to text, paragraphs, line breaks and links; attachments are kept as
 * links, never shown as images.
 */
@Slf4j
@Service
public class RemotePostService {

  static final Set<String> POST_TYPES =
      Set.of(ActivityStreamsObjects.TYPE_NOTE, ActivityStreamsObjects.TYPE_ARTICLE);

  /** The public collection, as it is spelled compacted and expanded. */
  static final Set<String> PUBLIC =
      Set.of(ActivityStreamsObjects.PUBLIC, "as:Public", "Public");

  /** Most attachments kept per post. */
  static final int MAX_ATTACHMENTS = 8;

  /** Longest content warning kept. */
  static final int MAX_SUMMARY_LENGTH = 1000;

  /** Text, paragraphs, line breaks and links that don't pass on any credit. */
  static final Safelist SAFELIST =
      Safelist.none()
          .addTags("p", "br", "a")
          .addAttributes("a", "href")
          .addProtocols("a", "href", "http", "https")
          .addEnforcedAttribute("a", "rel", "nofollow ugc noopener");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** A link to an attachment. */
  public record Link(String url, String name) {}

  /** A post as the friends page shows it: who wrote it, and its attachments as links. */
  public record FriendsPagePost(RemotePost post, RemoteFollowing account, List<Link> attachments) {}

  private final RemotePostRepository repository;
  private final RemoteFollowingRepository followingRepository;
  private final FederationPolicy federationPolicy;
  private final ActivityStreamsObjects objects;

  @Setter
  @Value("${activitypub.remote-posts.keep-days:30}")
  private int keepDays = 30;

  public RemotePostService(
      final RemotePostRepository repository,
      final RemoteFollowingRepository followingRepository,
      final FederationPolicy federationPolicy,
      final ActivityStreamsObjects objects) {
    this.repository = repository;
    this.followingRepository = followingRepository;
    this.federationPolicy = federationPolicy;
    this.objects = objects;
  }

  /**
   * Act on a Create, Update or Delete of a post.
   *
   * @param sender the actor whose key signed it, which is also the activity's actor
   */
  @Transactional
  public InboxService.Outcome receive(final JsonNode activity, final String sender) {
    final String type = text(activity, "type");
    if (type == null || sender == null) return InboxService.Outcome.IGNORED;
    return switch (type) {
      case "Create" -> store(activity.get("object"), sender, false);
      case "Update" -> store(activity.get("object"), sender, true);
      case "Delete" -> delete(InboxService.id(activity.get("object")), sender);
      default -> InboxService.Outcome.IGNORED;
    };
  }

  private InboxService.Outcome store(
      final JsonNode object, final String sender, final boolean update) {
    if (object == null || !object.isObject() || !POST_TYPES.contains(text(object, "type")))
      return InboxService.Outcome.IGNORED;
    final String objectId = text(object, "id");
    final URI actor = RemoteAccountResolver.httpUrl(sender);
    final URI objectUri = RemoteAccountResolver.httpUrl(objectId);
    if (actor == null
        || objectUri == null
        || !sameHost(objectUri, actor.getHost().toLowerCase(Locale.ROOT))
        || !attributedTo(object.get("attributedTo"), sender)
        || isReplyToUs(object)) {
      return InboxService.Outcome.IGNORED;
    }

    final Optional<RemotePost> existing = repository.findByObjectId(objectId);
    if (existing.isPresent() && !existing.get().getActorId().equals(sender))
      return InboxService.Outcome.IGNORED;
    if (!isPublic(object)) {
      if (!update || existing.isEmpty()) return InboxService.Outcome.IGNORED;
      repository.delete(existing.get());
      return InboxService.Outcome.HANDLED;
    }
    if (!followingRepository.existsByActorIdAndState(sender, FollowingState.ACCEPTED))
      return InboxService.Outcome.IGNORED;
    // the same Create again, perhaps through another follower's inbox
    if (!update && existing.isPresent()) return InboxService.Outcome.IGNORED;

    final Date now = new Date();
    Date published = date(text(object, "published"));
    if (published == null || published.after(now)) published = now;
    if (existing.isEmpty() && published.before(cutoff(now))) return InboxService.Outcome.IGNORED;

    final RemotePost post = existing.orElseGet(RemotePost::new);
    post.setObjectId(objectId);
    post.setActorId(sender);
    post.setUrl(link(object.get("url"), objectUri.toASCIIString()));
    post.setName(truncate(plain(text(object, "name")), RemotePost.MAX_NAME_LENGTH));
    post.setSummary(truncate(plain(text(object, "summary")), MAX_SUMMARY_LENGTH));
    post.setContent(clean(content(object)));
    post.setAttachments(attachments(object.get("attachment")));
    if (existing.isEmpty()) post.setPublished(published);
    if (update) post.setUpdated(now);
    repository.save(post);
    return InboxService.Outcome.HANDLED;
  }

  private InboxService.Outcome delete(final String objectId, final String sender) {
    if (objectId == null || objectId.equals(sender)) return InboxService.Outcome.IGNORED;
    final Optional<RemotePost> existing = repository.findByObjectId(objectId);
    if (existing.isEmpty() || !existing.get().getActorId().equals(sender))
      return InboxService.Outcome.IGNORED;
    repository.delete(existing.get());
    return InboxService.Outcome.HANDLED;
  }

  /**
   * The newest posts by accounts a writer follows, for their own friends page. Nothing while the
   * writer's journal doesn't federate.
   */
  @Transactional(readOnly = true)
  public List<FriendsPagePost> forFriendsPage(final User user, final int limit) {
    if (!federationPolicy.federates(user)) return List.of();
    final List<RemotePost> posts =
        repository.findFollowedBy(user.getId(), FollowingState.ACCEPTED, PageRequest.of(0, limit));
    if (posts.isEmpty()) return List.of();
    final Map<String, RemoteFollowing> accounts =
        followingRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
            .collect(Collectors.toMap(RemoteFollowing::getActorId, Function.identity()));
    final List<FriendsPagePost> result = new ArrayList<>(posts.size());
    for (final RemotePost post : posts) {
      final RemoteFollowing account = accounts.get(post.getActorId());
      if (account != null) result.add(new FriendsPagePost(post, account, links(post)));
    }
    return result;
  }

  /**
   * Remove posts older than {@code activitypub.remote-posts.keep-days}, and posts by accounts
   * nobody here follows any more.
   *
   * @return how many were removed
   */
  @Transactional
  public int purge(final Date now) {
    return repository.deletePublishedBefore(cutoff(now))
        + repository.deleteUnfollowed(FollowingState.ACCEPTED);
  }

  private Date cutoff(final Date now) {
    return new Date(now.getTime() - Duration.ofDays(keepDays).toMillis());
  }

  /** Whether the object answers one of this site's actors or entries. */
  private boolean isReplyToUs(final JsonNode object) {
    final String inReplyTo = InboxService.id(object.get("inReplyTo"));
    return inReplyTo != null && inReplyTo.startsWith(objects.base() + "users/");
  }

  /** Whether the actor is among those the object is attributed to. */
  private static boolean attributedTo(final JsonNode attributedTo, final String actor) {
    if (attributedTo != null && attributedTo.isArray()) {
      for (final JsonNode one : attributedTo) {
        if (actor.equals(InboxService.id(one))) return true;
      }
      return false;
    }
    return actor.equals(InboxService.id(attributedTo));
  }

  /** Whether {@code to} or {@code cc} names the public collection. */
  static boolean isPublic(final JsonNode object) {
    return addresses(object.get("to")) || addresses(object.get("cc"));
  }

  private static boolean addresses(final JsonNode audience) {
    if (audience == null) return false;
    if (audience.isArray()) {
      for (final JsonNode one : audience) {
        if (PUBLIC.contains(InboxService.id(one))) return true;
      }
      return false;
    }
    return PUBLIC.contains(InboxService.id(audience));
  }

  /** The content, or failing that the first of its translations. */
  private static String content(final JsonNode object) {
    final String content = text(object, "content");
    if (content != null) return content;
    final JsonNode map = object.get("contentMap");
    if (map != null && map.isObject()) {
      final Iterator<JsonNode> values = map.elements();
      while (values.hasNext()) {
        final JsonNode value = values.next();
        if (value.isTextual()) return value.asText();
      }
    }
    return "";
  }

  /** HTML cleaned down to text, paragraphs, line breaks and links. */
  static String clean(final String html) {
    if (html == null || html.isBlank()) return "";
    return Jsoup.clean(html, "", SAFELIST, new Document.OutputSettings().prettyPrint(false));
  }

  /** Plain text from what may be HTML. */
  private static String plain(final String html) {
    if (html == null) return null;
    final String text = Jsoup.parseBodyFragment(html).text().strip();
    return text.isEmpty() ? null : text;
  }

  /**
   * The first http(s) address in a {@code url} or {@code href}, which may be a string, a Link or
   * a list of them.
   */
  private static String link(final JsonNode url, final String fallback) {
    if (url == null) return fallback;
    if (url.isArray()) {
      for (final JsonNode one : url) {
        final String found = link(one, null);
        if (found != null) return found;
      }
      return fallback;
    }
    final String value = url.isTextual() ? url.asText() : url.isObject() ? text(url, "href") : null;
    final URI uri = RemoteAccountResolver.httpUrl(value);
    return uri == null ? fallback : uri.toASCIIString();
  }

  /** Attachments as a JSON array of links, or null when there are none. */
  private static String attachments(final JsonNode attachment) {
    if (attachment == null) return null;
    final List<JsonNode> items = new ArrayList<>();
    if (attachment.isArray()) attachment.forEach(items::add);
    else items.add(attachment);

    final List<Link> links = new ArrayList<>();
    for (final JsonNode item : items) {
      if (links.size() >= MAX_ATTACHMENTS) break;
      final String url =
          item.isObject()
              ? link(item.has("url") ? item.get("url") : item.get("href"), null)
              : link(item, null);
      if (url == null) continue;
      final String name = item.isObject() ? plain(text(item, "name")) : null;
      links.add(new Link(url, truncate(name, RemotePost.MAX_NAME_LENGTH)));
    }
    if (links.isEmpty()) return null;
    try {
      return MAPPER.writeValueAsString(links);
    } catch (final JsonProcessingException e) {
      return null;
    }
  }

  /** The stored attachments. */
  static List<Link> links(final RemotePost post) {
    if (post.getAttachments() == null || post.getAttachments().isBlank()) return List.of();
    try {
      return MAPPER.readValue(post.getAttachments(), new TypeReference<List<Link>>() {});
    } catch (final JsonProcessingException e) {
      log.debug("Unreadable attachments on remote post {}", post.getId());
      return List.of();
    }
  }

  private static Date date(final String value) {
    if (value == null) return null;
    try {
      return Date.from(OffsetDateTime.parse(value).toInstant());
    } catch (final DateTimeParseException e) {
      return null;
    }
  }

  private static String truncate(final String value, final int max) {
    if (value == null || value.length() <= max) return value;
    return value.substring(0, max);
  }
}
