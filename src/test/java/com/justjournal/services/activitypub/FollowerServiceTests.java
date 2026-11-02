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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.RemoteFollower;
import com.justjournal.model.User;
import com.justjournal.repository.RemoteFollowerRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FollowerServiceTests {

  private RemoteFollowerRepository repository;
  private FollowerService service;
  private User bob;

  @BeforeEach
  void setUp() {
    repository = mock(RemoteFollowerRepository.class);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    service = new FollowerService(repository);
    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
  }

  private static RemoteFollower follower(
      final String actor, final String inbox, final String sharedInbox) {
    final RemoteFollower follower = new RemoteFollower();
    follower.setUserId(7);
    follower.setActorId(actor);
    follower.setInbox(inbox);
    follower.setSharedInbox(sharedInbox);
    return follower;
  }

  @Test
  void deliveryInboxes_useTheSharedInboxOncePerServer() {
    when(repository.findByUserIdOrderByIdAsc(7))
        .thenReturn(
            List.of(
                follower(
                    "https://mastodon.social/users/a",
                    "https://mastodon.social/users/a/inbox",
                    "https://mastodon.social/inbox"),
                follower(
                    "https://mastodon.social/users/b",
                    "https://mastodon.social/users/b/inbox",
                    "https://mastodon.social/inbox"),
                follower("https://small.example/u/c", "https://small.example/u/c/inbox", null),
                follower("https://small.example/u/d", "https://small.example/u/d/inbox", " "),
                follower(
                    "https://other.example/users/e",
                    "https://other.example/users/e/inbox",
                    "https://other.example/inbox")));

    assertEquals(
        List.of(
            URI.create("https://mastodon.social/inbox"),
            URI.create("https://small.example/u/c/inbox"),
            URI.create("https://small.example/u/d/inbox"),
            URI.create("https://other.example/inbox")),
        List.copyOf(service.deliveryInboxes(bob)));
  }

  @Test
  void deliveryInboxes_withNoFollowers_isEmpty() {
    when(repository.findByUserIdOrderByIdAsc(7)).thenReturn(List.of());

    assertTrue(service.deliveryInboxes(bob).isEmpty());
  }

  @Test
  void add_storesANewFollower() {
    final RemoteFollower saved =
        service.add(
            bob,
            URI.create("https://mastodon.social/users/a"),
            URI.create("https://mastodon.social/users/a/inbox"),
            URI.create("https://mastodon.social/inbox"));

    assertEquals(7, saved.getUserId());
    assertEquals("https://mastodon.social/users/a", saved.getActorId());
    assertEquals("https://mastodon.social/users/a/inbox", saved.getInbox());
    assertEquals("https://mastodon.social/inbox", saved.getSharedInbox());
  }

  @Test
  void add_again_updatesTheInboxesInsteadOfAddingTwice() {
    final RemoteFollower existing =
        follower("https://mastodon.social/users/a", "https://old.example/inbox", "x");
    when(repository.findByUserIdAndActorId(7, "https://mastodon.social/users/a"))
        .thenReturn(Optional.of(existing));

    final RemoteFollower saved =
        service.add(
            bob,
            URI.create("https://mastodon.social/users/a"),
            URI.create("https://mastodon.social/users/a/inbox"),
            null);

    assertSame(existing, saved);
    assertEquals("https://mastodon.social/users/a/inbox", saved.getInbox());
    assertNull(saved.getSharedInbox());
  }

  @Test
  void add_refusesUrlsThatAreNotAbsoluteHttp() {
    final URI inbox = URI.create("https://mastodon.social/inbox");
    assertThrows(
        IllegalArgumentException.class,
        () -> service.add(bob, URI.create("/users/a"), inbox, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.add(bob, URI.create("mailto:a@example.com"), inbox, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.add(bob, URI.create("ftp://example.com/a"), inbox, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.add(
                bob,
                URI.create("https://example.com/" + "a".repeat(RemoteFollower.MAX_URL_LENGTH)),
                inbox,
                null));
    verify(repository, never()).save(any());
  }

  @Test
  void remove_andCount() {
    when(repository.deleteByUserIdAndActorId(7, "https://mastodon.social/users/a")).thenReturn(1L);
    when(repository.countByUserId(7)).thenReturn(4L);

    assertTrue(service.remove(bob, URI.create("https://mastodon.social/users/a")));
    assertFalse(service.remove(bob, URI.create("https://mastodon.social/users/z")));
    assertFalse(service.remove(bob, null));
    assertEquals(4, service.count(bob));
  }

  @Test
  void removeEverywhere_andFollowsAny() {
    when(repository.deleteByActorId("https://mastodon.social/users/a")).thenReturn(3L);
    when(repository.existsByActorId("https://mastodon.social/users/a")).thenReturn(true);

    assertTrue(service.followsAny(URI.create("https://mastodon.social/users/a")));
    assertFalse(service.followsAny(URI.create("https://mastodon.social/users/z")));
    assertFalse(service.followsAny(null));
    assertEquals(3, service.removeEverywhere(URI.create("https://mastodon.social/users/a")));
    assertEquals(0, service.removeEverywhere(null));
  }
}
