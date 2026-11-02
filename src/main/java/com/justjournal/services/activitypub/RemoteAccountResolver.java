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
import com.justjournal.model.RemoteFollowing;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Finds a fediverse account a writer wants to follow, given as {@code @user@host} or as the
 * address of its profile or actor. A handle is looked up with WebFinger over https; the actor
 * document is then fetched signed by the instance actor. Every request goes through {@link
 * SafeHttpFetcher}, so only public addresses are reached, and nothing on this site is followed.
 */
@Component
public class RemoteAccountResolver {

  /** What a WebFinger answer may be. */
  static final Set<String> JRD_TYPES = Set.of("application/jrd+json", "application/json");

  /** The actor types an account can be. */
  static final Set<String> ACTOR_TYPES =
      Set.of("Person", "Service", "Application", "Group", "Organization");

  static final String USAGE =
      "Enter the account as @name@server, or as the address of its profile.";

  /** {@code user@host}, after any {@code acct:} or {@code @} in front is dropped. */
  private static final Pattern HANDLE =
      Pattern.compile("([A-Za-z0-9._~-]{1,64})@([^@/\\\\\\s:?#%\\[\\]]{1,253})");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** An account as {@code user@host}; the host is lower case. */
  public record Handle(String user, String host) {
    @Override
    public String toString() {
      return "@" + user + "@" + host;
    }
  }

  /**
   * A remote account, ready to follow.
   *
   * @param id the actor's id
   * @param inbox the actor's own inbox
   * @param sharedInbox its server's shared inbox, or null
   * @param handle the account as {@code @user@host}
   * @param displayName its name, or null
   * @param profileUrl its profile page
   */
  public record RemoteAccount(
      URI id,
      URI inbox,
      URI sharedInbox,
      String handle,
      String displayName,
      String profileUrl) {}

  /** Why an account can't be followed; asking again won't help. The message is for the writer. */
  public static final class RefusedException extends Exception {
    public RefusedException(final String message) {
      super(message);
    }
  }

  private final SafeHttpFetcher httpFetcher;
  private final ActivityPubFetcher fetcher;
  private final ActivityStreamsObjects objects;

  /** The same cap as for key documents; WebFinger answers are smaller still. */
  @Setter
  @Value("${activitypub.key-fetch.max-bytes:262144}")
  private int maxBytes = 262_144;

  public RemoteAccountResolver(
      final SafeHttpFetcher httpFetcher,
      final ActivityPubFetcher fetcher,
      final ActivityStreamsObjects objects) {
    this.httpFetcher = httpFetcher;
    this.fetcher = fetcher;
    this.objects = objects;
  }

  /**
   * Find an account.
   *
   * @param input {@code @user@host}, {@code user@host}, {@code acct:user@host}, or an https address
   * @throws RefusedException when the input is unusable, the address may not be fetched, the
   *     account is on this site, or what came back is not an actor
   * @throws IOException when a server couldn't be reached; trying later may work
   */
  public RemoteAccount resolve(final String input) throws IOException, RefusedException {
    final Handle handle = parseHandle(input);
    if (handle != null) {
      if (isLocalHost(handle.host())) throw local();
      return fetchActor(webFinger(handle), handle.toString());
    }
    final String text = input == null ? "" : input.strip();
    if (!text.regionMatches(true, 0, "https://", 0, 8)) throw new RefusedException(USAGE);
    return fetchActor(text, null);
  }

  /** The input as a handle, or null when it isn't one. */
  static Handle parseHandle(final String input) {
    if (input == null) return null;
    String text = input.strip();
    if (text.regionMatches(true, 0, "acct:", 0, 5)) text = text.substring(5);
    if (text.startsWith("@")) text = text.substring(1);
    final Matcher matcher = HANDLE.matcher(text);
    if (!matcher.matches()) return null;
    return new Handle(matcher.group(1), matcher.group(2).toLowerCase(Locale.ROOT));
  }

  /**
   * Ask the account's server, over https, for its actor's address.
   *
   * @return the address of the actor document
   */
  String webFinger(final Handle handle) throws IOException, RefusedException {
    final String resource = "acct:" + handle.user() + "@" + handle.host();
    final NormalizedUrl url =
        normalize(
            "https://"
                + handle.host()
                + "/.well-known/webfinger?resource="
                + URLEncoder.encode(resource, StandardCharsets.UTF_8));
    final SafeHttpFetcher.Result result;
    try {
      // a redirect, to the server that really hosts the domain's accounts, is checked as it goes
      result = httpFetcher.fetch(url, JRD_TYPES, maxBytes);
    } catch (final LinkRejectedException e) {
      throw unreachable();
    } catch (final FetchException e) {
      if (e.permanent()) throw new RefusedException("No account " + handle + " was found.");
      throw e;
    }
    if (!"https".equalsIgnoreCase(result.finalUri().getScheme())) throw unreachable();

    final JsonNode links = parse(result.body()).get("links");
    if (links != null && links.isArray()) {
      for (final JsonNode link : links) {
        final String type = text(link, "type");
        if ("self".equals(text(link, "rel"))
            && type != null
            && (type.startsWith("application/activity+json")
                || type.startsWith("application/ld+json"))
            && text(link, "href") != null) {
          return text(link, "href");
        }
      }
    }
    throw new RefusedException(handle + " is not an account that can be followed from here.");
  }

  /**
   * Fetch and check an actor. A profile page that serves the actor under another id is read again
   * from that id, so the document always comes from the actor's own address.
   *
   * @param handle the handle it was found by, or null to make one from the actor
   */
  private RemoteAccount fetchActor(final String url, final String handle)
      throws IOException, RefusedException {
    JsonNode actor = fetchJson(url);
    final String id = text(actor, "id");
    if (id == null) throw notAnAccount();
    if (!id.equals(url)) {
      actor = fetchJson(id);
      if (!id.equals(text(actor, "id"))) throw notAnAccount();
    }
    if (!ACTOR_TYPES.contains(text(actor, "type"))) throw notAnAccount();

    final URI actorId = httpsUrl(id);
    if (actorId == null) throw notAnAccount();
    if (isLocalHost(actorId.getHost())) throw local();
    final URI inbox = httpUrl(text(actor, "inbox"));
    if (inbox == null) throw notAnAccount();
    final URI sharedInbox = httpUrl(text(actor.get("endpoints"), "sharedInbox"));

    final String host = actorId.getHost().toLowerCase(Locale.ROOT);
    final String name = text(actor, "preferredUsername");
    String accountHandle = handle;
    if (accountHandle == null) {
      accountHandle = name == null || name.isBlank() ? host : "@" + name.strip() + "@" + host;
    }
    final URI profile = httpUrl(text(actor, "url"));
    return new RemoteAccount(
        actorId,
        inbox,
        sharedInbox,
        truncate(accountHandle, RemoteFollowing.MAX_HANDLE_LENGTH),
        truncate(blankToNull(text(actor, "name")), RemoteFollowing.MAX_NAME_LENGTH),
        profile == null ? actorId.toASCIIString() : profile.toASCIIString());
  }

  /** GET an ActivityStreams document from its own host, signed by the instance actor. */
  private JsonNode fetchJson(final String address) throws IOException, RefusedException {
    final URI uri = httpsUrl(address);
    if (uri == null) throw notAnAccount();
    if (isLocalHost(uri.getHost())) throw local();
    final NormalizedUrl url = normalize(address);
    final SafeHttpFetcher.Result result;
    try {
      result = fetcher.signedGet(url, maxBytes);
    } catch (final LinkRejectedException e) {
      throw unreachable();
    } catch (final FetchException e) {
      if (e.permanent()) throw notAnAccount();
      throw e;
    }
    if (!sameHost(result.finalUri(), url.asciiHost())) throw notAnAccount();
    return parse(result.body());
  }

  private NormalizedUrl normalize(final String address) throws RefusedException {
    try {
      return fetcher.normalize(address);
    } catch (final LinkRejectedException e) {
      throw unreachable();
    }
  }

  private static JsonNode parse(final byte[] body) throws RefusedException {
    final JsonNode node;
    try {
      node = MAPPER.readTree(body);
    } catch (final IOException e) {
      throw notAnAccount();
    }
    if (node == null || !node.isObject()) throw notAnAccount();
    return node;
  }

  /** Whether a host is this site's, with or without a leading www. */
  boolean isLocalHost(final String host) {
    if (host == null) return false;
    final String local = withoutWww(URI.create(objects.base()).getHost());
    return local != null && local.equals(withoutWww(host));
  }

  private static String withoutWww(final String host) {
    if (host == null) return null;
    final String lower = host.toLowerCase(Locale.ROOT);
    return lower.startsWith("www.") ? lower.substring(4) : lower;
  }

  /** An https address {@link FollowerService} will store, or null. */
  private static URI httpsUrl(final String value) {
    final URI uri = httpUrl(value);
    return uri != null && "https".equalsIgnoreCase(uri.getScheme()) ? uri : null;
  }

  /** An address {@link FollowerService} will store, or null. */
  static URI httpUrl(final String value) {
    if (value == null) return null;
    try {
      final URI uri = new URI(value);
      FollowerService.url(uri, "address");
      return uri;
    } catch (final URISyntaxException | IllegalArgumentException e) {
      return null;
    }
  }

  private static String blankToNull(final String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String truncate(final String value, final int max) {
    if (value == null || value.length() <= max) return value;
    return value.substring(0, max);
  }

  private static RefusedException local() {
    return new RefusedException(
        "That account is on this site. Add it as a friend from its journal instead.");
  }

  private static RefusedException unreachable() {
    return new RefusedException("That server can't be reached from here.");
  }

  private static RefusedException notAnAccount() {
    return new RefusedException("That address is not an account that can be followed.");
  }
}
