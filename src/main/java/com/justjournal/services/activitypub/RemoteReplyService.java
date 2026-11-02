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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.model.RemoteReply;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.RemoteReplyRepository;
import com.justjournal.services.CommentMailService;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.activitypub.InboxService.Outcome;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Replies to journal entries from other fediverse servers, kept as {@link RemoteReply} and shown
 * with the entry's comments. {@link InboxService} hands over:
 *
 * <ul>
 *   <li>a {@code Create} of a public Note or Article in reply to one of the journal's entries. It
 *       is stored when federation is on for the site and the journal, anyone may see the entry,
 *       the entry takes comments, and the signed sender wrote it. The author's name, username and
 *       profile come from their actor document. The same reply delivered again is stored once.
 *   <li>an {@code Update} of a stored reply by its author: the text is replaced.
 *   <li>a {@code Delete} of a stored reply by its author: it is removed.
 *   <li>the deletion of an actor: all its replies are removed.
 * </ul>
 *
 * <p>The text is cleaned down to paragraphs, line breaks and links, which get {@code rel="nofollow
 * ugc noopener"}; pictures, scripts and everything else go. Replies to replies are not taken.
 */
@Slf4j
@Service
public class RemoteReplyService {

  /** Longest reply kept, in characters of cleaned HTML; longer ones are cut to plain text. */
  static final int MAX_CONTENT_LENGTH = 10_000;

  /** Longest author name kept. */
  static final int MAX_NAME_LENGTH = 100;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** The kinds of object taken as a reply. */
  private static final Set<String> TYPES =
      Set.of(ActivityStreamsObjects.TYPE_NOTE, ActivityStreamsObjects.TYPE_ARTICLE);

  /** The ways the public collection is named in to and cc. */
  private static final Set<String> PUBLIC =
      Set.of(ActivityStreamsObjects.PUBLIC, "as:Public", "Public");

  /** A username as other servers have them; anything else isn't shown as a handle. */
  private static final Pattern USERNAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,64}");

  private static final Pattern ENTRY_NUMBER = Pattern.compile("[1-9][0-9]{0,8}");

  private static final Safelist SAFELIST =
      new Safelist()
          .addTags("p", "br", "a")
          .addAttributes("a", "href")
          .addProtocols("a", "href", "http", "https")
          .addEnforcedAttribute("a", "rel", "nofollow ugc noopener");

  private static final Document.OutputSettings OUTPUT =
      new Document.OutputSettings().prettyPrint(false);

  private final RemoteReplyRepository repository;
  private final EntryRepository entryRepository;
  private final FederationPolicy federationPolicy;
  private final EntryAccessService entryAccessService;
  private final ActorKeyService actorKeyService;
  private final ActivityPubFetcher fetcher;
  private final CommentMailService commentMailService;

  /** The same cap as for key documents: what is fetched here is the same actor document. */
  @Setter
  @Value("${activitypub.key-fetch.max-bytes:262144}")
  private int maxBytes = 262_144;

  public RemoteReplyService(
      final RemoteReplyRepository repository,
      final EntryRepository entryRepository,
      final FederationPolicy federationPolicy,
      final EntryAccessService entryAccessService,
      final ActorKeyService actorKeyService,
      final ActivityPubFetcher fetcher,
      final CommentMailService commentMailService) {
    this.repository = repository;
    this.entryRepository = entryRepository;
    this.federationPolicy = federationPolicy;
    this.entryAccessService = entryAccessService;
    this.actorKeyService = actorKeyService;
    this.fetcher = fetcher;
    this.commentMailService = commentMailService;
  }

  /**
   * Store a reply to one of the journal's entries.
   *
   * @param user the journal whose inbox took the Create
   * @param create the Create activity
   * @param sender the actor whose key signed it
   * @return HANDLED when stored or stored before, RETRY when the author's actor couldn't be
   *     fetched for now, IGNORED for anything else
   */
  public Outcome create(final User user, final JsonNode create, final String sender) {
    final JsonNode object = create.get("object");
    final Entry entry = acceptedReplyTo(user, object, sender);
    if (entry == null) return Outcome.IGNORED;

    final String actorId = asciiUrl(sender);
    final String objectId = ownObjectId(object, actorId);
    if (objectId == null) return Outcome.IGNORED;
    if (repository.findByObjectId(objectId).isPresent()) return Outcome.HANDLED;

    final String content = clean(text(object, "content"));
    if (content == null) return Outcome.IGNORED;

    final Author author;
    try {
      author = fetchAuthor(sender);
    } catch (final IOException e) {
      log.debug("Could not fetch {}, who replied to {}: {}", sender, entry.getId(), e.getMessage());
      return Outcome.RETRY;
    } catch (final RefusedActorException e) {
      log.debug("Not taking a reply to {} from {}: {}", entry.getId(), sender, e.getMessage());
      return Outcome.IGNORED;
    }
    return store(entry, object, objectId, actorId, author, content);
  }

  /**
   * The entry a Create's object replies to, when it is a public reply by its sender to an entry
   * that takes comments from the fediverse; otherwise null.
   */
  private Entry acceptedReplyTo(final User user, final JsonNode object, final String sender) {
    if (object == null || !object.isObject() || !TYPES.contains(text(object, "type"))) return null;
    final Entry entry = repliedTo(user, InboxService.id(object.get("inReplyTo")));
    if (entry == null) return null;
    if (!federationPolicy.isEnabled() || !federationPolicy.federates(entry.getUser())) return null;
    if (!entryAccessService.isPubliclyVisible(entry) || entry.getAllowComments() != PrefBool.Y)
      return null;
    if (!sender.equals(InboxService.id(object.get("attributedTo"))) || !isPublic(object))
      return null;
    return entry;
  }

  /**
   * The object's id, when it is on its sender's server: a server names only its own objects, so
   * nobody can take the id of someone else's reply. Otherwise null.
   */
  private static String ownObjectId(final JsonNode object, final String actorId) {
    final String objectId = asciiUrl(text(object, "id"));
    if (actorId == null || objectId == null || !sameHost(URI.create(objectId), host(actorId)))
      return null;
    return objectId;
  }

  /** Save a new reply and tell the entry's owner, if they asked for comment mail. */
  private Outcome store(
      final Entry entry,
      final JsonNode object,
      final String objectId,
      final String actorId,
      final Author author,
      final String content) {
    final Date now = new Date();
    final RemoteReply reply = new RemoteReply();
    reply.setEntryId(entry.getId());
    reply.setObjectId(objectId);
    reply.setActorId(actorId);
    reply.setAuthorName(author.name());
    reply.setAuthorHandle(author.handle());
    reply.setAuthorUrl(author.url());
    reply.setContent(content);
    reply.setPublished(published(text(object, "published"), now));
    reply.setCreated(now);
    try {
      repository.saveAndFlush(reply);
    } catch (final DataIntegrityViolationException e) {
      // the same reply, delivered twice at once
      return Outcome.HANDLED;
    }
    log.info("Stored reply {} to entry {} from {}", objectId, entry.getId(), actorId);

    final String from =
        author.handle() == null ? author.name() : author.name() + " (" + author.handle() + ")";
    commentMailService.commentAdded(entry, from, "(no subject)", Jsoup.parse(content).text());
    return Outcome.HANDLED;
  }

  /**
   * Replace the text of a stored reply, when its author sent the Update.
   *
   * @return HANDLED when changed, IGNORED when it isn't a reply stored here or not the sender's
   */
  public Outcome update(final JsonNode update, final String sender) {
    final JsonNode object = update.get("object");
    if (object == null || !object.isObject() || !TYPES.contains(text(object, "type")))
      return Outcome.IGNORED;
    final RemoteReply reply = byAuthor(text(object, "id"), sender);
    if (reply == null || !sender.equals(InboxService.id(object.get("attributedTo"))))
      return Outcome.IGNORED;
    final String content = clean(text(object, "content"));
    if (content == null) return Outcome.IGNORED;
    reply.setContent(content);
    reply.setUpdated(new Date());
    repository.save(reply);
    return Outcome.HANDLED;
  }

  /**
   * Remove a stored reply, when its author sent the Delete.
   *
   * @param objectId the id of the deleted object
   * @return HANDLED when removed, IGNORED when it isn't a reply stored here or not the sender's
   */
  public Outcome delete(final String objectId, final String sender) {
    final RemoteReply reply = byAuthor(objectId, sender);
    if (reply == null) return Outcome.IGNORED;
    repository.delete(reply);
    log.info("Removed reply {} to entry {}: its author deleted it", objectId, reply.getEntryId());
    return Outcome.HANDLED;
  }

  /**
   * Remove every reply by an actor whose account is gone.
   *
   * @return how many were removed
   */
  public long removeActor(final String actorId) {
    final String actor = asciiUrl(actorId);
    return actor == null ? 0 : repository.deleteByActorId(actor);
  }

  /** Whether the actor has replies stored here. */
  public boolean hasReplies(final String actorId) {
    final String actor = asciiUrl(actorId);
    return actor != null && repository.existsByActorId(actor);
  }

  /** The stored reply with this id, when the sender wrote it; otherwise null. */
  private RemoteReply byAuthor(final String objectId, final String sender) {
    final String id = asciiUrl(objectId);
    final String actor = asciiUrl(sender);
    if (id == null || actor == null) return null;
    final RemoteReply reply = repository.findByObjectId(id).orElse(null);
    return reply != null && actor.equals(reply.getActorId()) ? reply : null;
  }

  /** The journal's entry an object replies to, or null when it replies to anything else. */
  private Entry repliedTo(final User user, final String inReplyTo) {
    if (inReplyTo == null) return null;
    final String prefix = actorKeyService.userActorId(user.getUsername()) + "/entry/";
    if (!inReplyTo.startsWith(prefix)) return null;
    final String number = inReplyTo.substring(prefix.length());
    if (!ENTRY_NUMBER.matcher(number).matches()) return null;
    final int entryId;
    try {
      entryId = Integer.parseInt(number);
    } catch (final NumberFormatException e) {
      return null;
    }
    final Entry entry = entryRepository.findById(entryId).orElse(null);
    if (entry == null || entry.getUser() == null || entry.getUser().getId() != user.getId())
      return null;
    return entry;
  }

  /** Whether the object is addressed to everyone: a direct or followers-only reply is not shown. */
  private static boolean isPublic(final JsonNode object) {
    return addressesPublic(object.get("to")) || addressesPublic(object.get("cc"));
  }

  private static boolean addressesPublic(final JsonNode audience) {
    if (audience == null) return false;
    if (audience.isTextual()) return PUBLIC.contains(audience.asText());
    if (!audience.isArray()) return false;
    for (final JsonNode item : audience) {
      if (item.isTextual() && PUBLIC.contains(item.asText())) return true;
    }
    return false;
  }

  /**
   * HTML from another server cleaned down to text, paragraphs, line breaks and http(s) links,
   * which get {@code rel="nofollow ugc noopener"}. Too long a reply becomes its plain text, cut.
   *
   * @return the cleaned HTML, or null when no text is left
   */
  static String clean(final String html) {
    if (html == null) return null;
    final String cleaned = Jsoup.clean(html, "", SAFELIST, OUTPUT).strip();
    final String text = Jsoup.parse(cleaned).text().strip();
    if (text.isEmpty()) return null;
    if (cleaned.length() <= MAX_CONTENT_LENGTH) return cleaned;
    return "<p>" + Entities.escape(cap(text, MAX_CONTENT_LENGTH)) + "…</p>";
  }

  /** When the reply says it was written; never later than now. */
  private static Date published(final String value, final Date now) {
    if (value == null) return now;
    try {
      final Date date = Date.from(OffsetDateTime.parse(value).toInstant());
      return date.after(now) ? now : date;
    } catch (final DateTimeParseException e) {
      return now;
    }
  }

  /** The author of a reply, as shown with it. */
  private record Author(String name, String handle, String url) {}

  /** Why an author's actor document can't be used; asking again won't help. */
  private static final class RefusedActorException extends Exception {
    RefusedActorException(final String message) {
      super(message);
    }
  }

  /**
   * Fetch an author's actor, signed by the instance actor, for their name, username and profile.
   *
   * @throws IOException when the fetch failed in a way that may pass
   * @throws RefusedActorException when the actor may not be fetched or its document is unusable
   */
  private Author fetchAuthor(final String actorId) throws IOException, RefusedActorException {
    final NormalizedUrl url;
    final SafeHttpFetcher.Result result;
    try {
      url = fetcher.normalize(actorId);
      result = fetcher.signedGet(url, maxBytes);
    } catch (final LinkRejectedException e) {
      throw new RefusedActorException("refused address: " + e.getMessage());
    } catch (final FetchException e) {
      if (e.permanent()) throw new RefusedActorException(e.getMessage());
      throw e;
    }
    if (!sameHost(result.finalUri(), url.asciiHost()))
      throw new RefusedActorException("redirected to " + result.finalUri().getHost());

    final JsonNode actor;
    try {
      actor = MAPPER.readTree(result.body());
    } catch (final IOException e) {
      throw new RefusedActorException("the actor is not JSON");
    }
    if (actor == null || !actor.isObject() || !actorId.equals(text(actor, "id")))
      throw new RefusedActorException("the document is not that actor");

    final String username = plain(text(actor, "preferredUsername"), MAX_NAME_LENGTH);
    final String handle =
        username == null || !USERNAME.matcher(username).matches()
            ? null
            : "@" + username + "@" + url.asciiHost();
    String name = plain(text(actor, "name"), MAX_NAME_LENGTH);
    if (name == null) name = handle == null ? cap(actorId, MAX_NAME_LENGTH) : username;
    final String profile = asciiUrl(text(actor, "url"));
    return new Author(name, handle, profile == null ? asciiUrl(actorId) : profile);
  }

  /** Text with control characters and runs of white space made single spaces, cut; or null. */
  private static String plain(final String value, final int max) {
    if (value == null) return null;
    final String text = value.replaceAll("[\\p{Cc}\\p{Cf}\\s]+", " ").strip();
    return text.isEmpty() ? null : cap(text, max);
  }

  /** At most max characters, without splitting a character in two. */
  private static String cap(final String value, final int max) {
    if (value.length() <= max) return value;
    final int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
    return value.substring(0, end);
  }

  /** An absolute http(s) URL as the ASCII the URL columns hold, or null. */
  private static String asciiUrl(final String value) {
    if (value == null) return null;
    try {
      return FollowerService.url(new URI(value), "URL");
    } catch (final URISyntaxException | IllegalArgumentException e) {
      return null;
    }
  }

  private static String host(final String url) {
    final String host = URI.create(url).getHost();
    return host == null ? null : host.toLowerCase(Locale.ROOT);
  }
}
