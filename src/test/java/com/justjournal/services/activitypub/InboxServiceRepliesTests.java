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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.model.User;
import com.justjournal.services.activitypub.InboxService.Outcome;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.NormalizedUrl;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** How the inbox hands replies to {@link RemoteReplyService}. */
class InboxServiceRepliesTests {

  private static final String ALICE = "https://remote.social/users/alice";
  private static final String NOTE = ALICE + "/statuses/1";

  private final ObjectMapper mapper = new ObjectMapper();
  private FollowerService followerService;
  private ActivityPubFetcher fetcher;
  private RemoteReplyService remoteReplies;
  private InboxService inboxService;
  private User bob;

  @BeforeEach
  void setUp() throws Exception {
    followerService = mock(FollowerService.class);
    fetcher = mock(ActivityPubFetcher.class);
    remoteReplies = mock(RemoteReplyService.class);
    inboxService =
        new InboxService(
            followerService,
            mock(DeliveryService.class),
            mock(FederationPolicy.class),
            mock(ActorKeyService.class),
            fetcher,
            remoteReplies);
    when(fetcher.normalize(anyString()))
        .thenAnswer(
            inv -> {
              final URI uri = URI.create(inv.getArgument(0));
              return new NormalizedUrl(uri, uri, uri.getHost());
            });
    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
  }

  private ObjectNode activity(final String type) {
    final ObjectNode activity = mapper.createObjectNode();
    activity.put("id", NOTE + "#" + type);
    activity.put("type", type);
    activity.put("actor", ALICE);
    return activity;
  }

  @Test
  void createAndUpdateGoToTheReplies() {
    final ObjectNode create = activity("Create");
    final ObjectNode update = activity("Update");
    when(remoteReplies.create(bob, create, ALICE)).thenReturn(Outcome.HANDLED);
    when(remoteReplies.update(update, ALICE)).thenReturn(Outcome.HANDLED);

    assertEquals(Outcome.HANDLED, inboxService.handle(bob, create, ALICE));
    assertEquals(Outcome.HANDLED, inboxService.handle(bob, update, ALICE));
  }

  @Test
  void deleteOfANoteGoesToTheReplies() {
    final ObjectNode tombstone = activity("Delete");
    tombstone.putObject("object").put("id", NOTE).put("type", "Tombstone");
    final ObjectNode bare = activity("Delete");
    bare.put("object", NOTE);
    when(remoteReplies.delete(NOTE, ALICE)).thenReturn(Outcome.HANDLED);

    assertEquals(Outcome.HANDLED, inboxService.handle(bob, tombstone, ALICE));
    assertEquals(Outcome.HANDLED, inboxService.handle(bob, bare, ALICE));
    verify(followerService, never()).removeEverywhere(any());
    verify(remoteReplies, never()).removeActor(anyString());
  }

  @Test
  void deleteOfTheActorRemovesItsReplies() {
    final ObjectNode delete = activity("Delete");
    delete.put("object", ALICE);

    assertEquals(Outcome.HANDLED, inboxService.handle(bob, delete, ALICE));
    verify(followerService).removeEverywhere(URI.create(ALICE));
    verify(remoteReplies).removeActor(ALICE);
  }

  @Test
  void aGoneActorThatOnlyRepliedHasItsRepliesRemoved() throws Exception {
    final ObjectNode delete = activity("Delete");
    delete.put("object", ALICE);
    when(remoteReplies.hasReplies(ALICE)).thenReturn(true);
    when(fetcher.unsignedGet(any(), anyInt()))
        .thenThrow(new FetchException(410, true, "HTTP status 410"));

    assertEquals(Outcome.HANDLED, inboxService.deleteOfGoneActor(delete));
    verify(remoteReplies).removeActor(ALICE);
  }

  @Test
  void aGoneActorWithNoRepliesOrFollowsIsIgnored() throws Exception {
    final ObjectNode delete = activity("Delete");
    delete.put("object", ALICE);

    assertEquals(Outcome.IGNORED, inboxService.deleteOfGoneActor(delete));
    verify(fetcher, never()).unsignedGet(any(), anyInt());
    verify(remoteReplies, never()).removeActor(anyString());
  }
}
