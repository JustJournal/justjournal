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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.core.Settings;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.services.AvatarService;
import com.justjournal.services.EntryService;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.SafeHttpFetcher;
import com.justjournal.services.link.UrlPolicy;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Finding a fediverse account to follow: handles, WebFinger, and the actor document. */
class RemoteAccountResolverTests {

  private static final String ALICE = "https://remote.social/users/alice";
  private static final String WEBFINGER =
      "https://remote.social/.well-known/webfinger?resource=acct%3Aalice%40remote.social";

  private final ObjectMapper mapper = new ObjectMapper();
  private SafeHttpFetcher httpFetcher;
  private ActivityPubFetcher fetcher;
  private RemoteAccountResolver resolver;

  @BeforeEach
  void setUp() throws Exception {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    final ActivityStreamsObjects objects =
        new ActivityStreamsObjects(
            settings,
            mock(EntryService.class),
            mock(AvatarService.class),
            mock(UserPicRepository.class),
            mock(ActorKeyService.class));

    httpFetcher = mock(SafeHttpFetcher.class);
    fetcher = mock(ActivityPubFetcher.class);
    // the real address checks, so private and odd addresses are refused as they would be
    final UrlPolicy urlPolicy = new UrlPolicy();
    when(fetcher.normalize(anyString()))
        .thenAnswer(inv -> urlPolicy.normalize((String) inv.getArgument(0)));
    resolver = new RemoteAccountResolver(httpFetcher, fetcher, objects);

    serveWebFinger(ALICE);
    serveActor(ALICE, actor(ALICE));
  }

  // ---- fixtures -------------------------------------------------------------------------

  private ObjectNode actor(final String id) {
    final ObjectNode actor = mapper.createObjectNode();
    actor.put("id", id);
    actor.put("type", "Person");
    actor.put("preferredUsername", "alice");
    actor.put("name", "Alice");
    actor.put("url", "https://remote.social/@alice");
    actor.put("inbox", id + "/inbox");
    actor.putObject("endpoints").put("sharedInbox", "https://remote.social/inbox");
    return actor;
  }

  private void serveWebFinger(final String href) throws Exception {
    final ObjectNode jrd = mapper.createObjectNode();
    jrd.put("subject", "acct:alice@remote.social");
    final var links = jrd.putArray("links");
    links
        .addObject()
        .put("rel", "http://webfinger.net/rel/profile-page")
        .put("type", "text/html")
        .put("href", "https://remote.social/@alice");
    links.addObject().put("rel", "self").put("type", "application/activity+json").put("href", href);
    serveJrd(URI.create(WEBFINGER), mapper.writeValueAsBytes(jrd));
  }

  private void serveJrd(final URI finalUri, final byte[] body) throws Exception {
    doReturn(new SafeHttpFetcher.Result(finalUri, "application/jrd+json", null, body))
        .when(httpFetcher)
        .fetch(any(), eq(RemoteAccountResolver.JRD_TYPES), anyInt());
  }

  private void failJrd(final Exception e) throws Exception {
    doThrow(e).when(httpFetcher).fetch(any(), eq(RemoteAccountResolver.JRD_TYPES), anyInt());
  }

  /** Serve a document at an address; the fetch ends where it started. */
  private void serveActor(final String at, final ObjectNode document) throws Exception {
    doReturn(
            new SafeHttpFetcher.Result(
                URI.create(at),
                "application/activity+json",
                StandardCharsets.UTF_8,
                mapper.writeValueAsBytes(document)))
        .when(fetcher)
        .signedGet(argThat(url -> url != null && url.fetchUri().toString().equals(at)), anyInt());
  }

  private String refusal(final String input) {
    return assertThrows(RemoteAccountResolver.RefusedException.class, () -> resolver.resolve(input))
        .getMessage();
  }

  // ---- handles --------------------------------------------------------------------------

  @Test
  void parseHandle_takesTheUsualSpellings() {
    for (final String input :
        new String[] {
          "@alice@remote.social", "alice@remote.social", "acct:alice@remote.social",
          "  @alice@Remote.Social  "
        }) {
      final RemoteAccountResolver.Handle handle = RemoteAccountResolver.parseHandle(input);
      assertEquals("alice", handle.user(), input);
      assertEquals("remote.social", handle.host(), input);
      assertEquals("@alice@remote.social", handle.toString());
    }
  }

  @Test
  void parseHandle_refusesWhatIsNotAHandle() {
    for (final String input :
        new String[] {
          null,
          "",
          "alice",
          "@alice",
          "a@b@c",
          "alice@remote.social/path",
          "alice@remote.social:8443",
          "alice@remote.social?x",
          "alice@[::1]",
          "ali ce@remote.social",
          "https://remote.social/@alice"
        }) {
      assertNull(RemoteAccountResolver.parseHandle(input), input);
    }
  }

  // ---- WebFinger and the actor ----------------------------------------------------------

  @Test
  void handle_isLookedUpWithWebFingerOverHttps_andTheActorFetched() throws Exception {
    final RemoteAccountResolver.RemoteAccount account = resolver.resolve("@alice@remote.social");

    final ArgumentCaptor<NormalizedUrl> url = ArgumentCaptor.forClass(NormalizedUrl.class);
    verify(httpFetcher).fetch(url.capture(), eq(RemoteAccountResolver.JRD_TYPES), anyInt());
    assertEquals(WEBFINGER, url.getValue().fetchUri().toString());
    assertEquals(URI.create(ALICE), account.id());
    assertEquals(URI.create(ALICE + "/inbox"), account.inbox());
    assertEquals(URI.create("https://remote.social/inbox"), account.sharedInbox());
    assertEquals("@alice@remote.social", account.handle());
    assertEquals("Alice", account.displayName());
    assertEquals("https://remote.social/@alice", account.profileUrl());
  }

  @Test
  void profileAddress_isReadAgainFromTheActorsOwnId() throws Exception {
    final ObjectNode served = actor(ALICE);
    serveActor("https://remote.social/@alice", served);

    final RemoteAccountResolver.RemoteAccount account =
        resolver.resolve("https://remote.social/@alice");

    assertEquals(URI.create(ALICE), account.id());
    assertEquals("@alice@remote.social", account.handle());
    verify(fetcher)
        .signedGet(
            argThat(u -> u.fetchUri().toString().equals(ALICE)),
            anyInt());
    verifyNoInteractions(httpFetcher);
  }

  @Test
  void webFinger_withoutAnActivityPubLink_isRefused() throws Exception {
    final ObjectNode jrd = mapper.createObjectNode();
    jrd.putArray("links")
        .addObject()
        .put("rel", "self")
        .put("type", "text/html")
        .put("href", ALICE);
    serveJrd(URI.create(WEBFINGER), mapper.writeValueAsBytes(jrd));

    assertTrue(refusal("@alice@remote.social").contains("can be followed"));
  }

  @Test
  void webFinger_notFound_isRefused_butAnOutageMayBeRetried() throws Exception {
    failJrd(new FetchException(404, true, "HTTP status 404"));
    assertTrue(refusal("@alice@remote.social").startsWith("No account @alice@remote.social"));

    failJrd(new FetchException(503, false, "HTTP status 503"));
    assertThrows(IOException.class, () -> resolver.resolve("@alice@remote.social"));
  }

  @Test
  void webFinger_endingOverPlainHttp_isRefused() throws Exception {
    serveJrd(
        URI.create("http://remote.social/.well-known/webfinger"),
        "{}".getBytes(StandardCharsets.UTF_8));

    refusal("@alice@remote.social");
    verify(fetcher, never()).signedGet(any(), anyInt());
  }

  @Test
  void actorOverPlainHttp_isRefused() throws Exception {
    serveWebFinger("http://remote.social/users/alice");
    refusal("@alice@remote.social");
    refusal("http://remote.social/users/alice");
    verify(fetcher, never()).signedGet(any(), anyInt());
  }

  @Test
  void documentThatIsNotAnActor_isRefused() throws Exception {
    final ObjectNode note = actor(ALICE);
    note.put("type", "Note");
    serveActor(ALICE, note);
    refusal("@alice@remote.social");

    final ObjectNode noInbox = actor(ALICE);
    noInbox.remove("inbox");
    serveActor(ALICE, noInbox);
    refusal("@alice@remote.social");
  }

  @Test
  void actorRedirectedToAnotherHost_isRefused() throws Exception {
    doReturn(
            new SafeHttpFetcher.Result(
                URI.create("https://elsewhere.example/users/alice"),
                "application/activity+json",
                StandardCharsets.UTF_8,
                mapper.writeValueAsBytes(actor(ALICE))))
        .when(fetcher)
        .signedGet(any(), anyInt());

    refusal("@alice@remote.social");
  }

  // ---- what may not be reached ------------------------------------------------------------

  @Test
  void privateAndSpecialAddresses_areRefusedWithoutFetching() throws Exception {
    for (final String input :
        new String[] {
          "@alice@localhost",
          "@alice@intranet",
          "@alice@printer.local",
          "@alice@127.0.0.1",
          "@alice@10.0.0.8",
          "https://localhost/users/alice",
          "https://192.168.1.1/users/alice",
          "https://remote.social:8443/users/alice"
        }) {
      assertEquals("That server can't be reached from here.", refusal(input), input);
    }
    verifyNoInteractions(httpFetcher);
    verify(fetcher, never()).signedGet(any(), anyInt());
  }

  @Test
  void aRedirectOrConnectionToAPrivateAddress_isRefused() throws Exception {
    failJrd(
        LinkRejectedException.of(LinkRejectedException.Reason.PRIVATE_ADDRESS, "remote.social"));
    assertEquals("That server can't be reached from here.", refusal("@alice@remote.social"));

    serveWebFinger(ALICE);
    doThrow(
            LinkRejectedException.of(LinkRejectedException.Reason.PRIVATE_ADDRESS, "remote.social"))
        .when(fetcher)
        .signedGet(any(), anyInt());
    assertEquals("That server can't be reached from here.", refusal("@alice@remote.social"));
  }

  @Test
  void accountsOnThisSite_areRefused() throws Exception {
    for (final String input :
        new String[] {
          "@bob@justjournal.com",
          "@bob@www.justjournal.com",
          "https://www.justjournal.com/users/bob"
        }) {
      assertTrue(refusal(input).contains("on this site"), input);
    }
    verifyNoInteractions(httpFetcher);
    verify(fetcher, never()).signedGet(any(), anyInt());

    // nor when another server's WebFinger points here
    serveWebFinger("https://www.justjournal.com/users/bob");
    assertTrue(refusal("@alice@remote.social").contains("on this site"));
    verify(fetcher, never()).signedGet(any(), anyInt());
  }

  @Test
  void somethingElse_isRefusedWithHowToEnterIt() {
    assertEquals(RemoteAccountResolver.USAGE, refusal("alice"));
    assertEquals(RemoteAccountResolver.USAGE, refusal("ftp://remote.social/alice"));
  }
}
