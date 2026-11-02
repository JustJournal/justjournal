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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.ActorKey;
import com.justjournal.model.User;
import com.justjournal.repository.ActorKeyRepository;
import com.justjournal.repository.UserRepository;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

class ActorKeyServiceTests {

  private ActorKeyRepository repository;
  private UserRepository userRepository;
  private ActorKeyService service;
  private User bob;

  @BeforeEach
  void setUp() {
    repository = mock(ActorKeyRepository.class);
    userRepository = mock(UserRepository.class);
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    when(repository.findByUserId(any())).thenReturn(Optional.empty());
    when(repository.findByKeyName(any())).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any(ActorKey.class))).thenAnswer(inv -> inv.getArgument(0));
    service =
        new ActorKeyService(
            repository, userRepository, settings, mock(PlatformTransactionManager.class));

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    when(userRepository.findByUsername("bob")).thenReturn(bob);
  }

  private static ActorKey stored(final Integer userId, final String keyName) {
    final ActorKey key = new ActorKey();
    key.setUserId(userId);
    key.setKeyName(keyName);
    key.setPublicKeyPem(TestKeys.PUBLIC_PEM);
    key.setPrivateKeyPem(TestKeys.PRIVATE_PEM);
    return key;
  }

  @Test
  void actorIds_areHttps() {
    assertEquals("https://www.justjournal.com/actor", service.instanceActorId());
    assertEquals("https://www.justjournal.com/users/bob", service.userActorId("bob"));
    assertEquals(
        "https://www.justjournal.com/users/bob#main-key",
        ActorKeyService.keyId("https://www.justjournal.com/users/bob"));
  }

  @Test
  void forUser_makesAnRsa2048KeyTheFirstTime() throws Exception {
    final ActorKey key = service.forUser(bob);

    verify(repository).saveAndFlush(key);
    assertEquals(7, key.getUserId());
    assertNull(key.getKeyName());
    assertNotNull(key.getCreated());
    final RSAPublicKey publicKey = (RSAPublicKey) Pem.publicKey(key.getPublicKeyPem());
    final RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) Pem.privateKey(key.getPrivateKeyPem());
    assertEquals(2048, publicKey.getModulus().bitLength());
    assertEquals(publicKey.getModulus(), privateKey.getModulus());
    assertEquals(publicKey.getPublicExponent(), privateKey.getPublicExponent());
  }

  @Test
  void forUser_returnsTheStoredKey() {
    final ActorKey stored = stored(7, null);
    when(repository.findByUserId(7)).thenReturn(Optional.of(stored));

    assertSame(stored, service.forUser(bob));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void forInstance_usesTheInstanceRow() {
    final ActorKey made = service.forInstance();
    assertEquals(ActorKeyService.INSTANCE, made.getKeyName());
    assertNull(made.getUserId());

    final ActorKey stored = stored(null, ActorKeyService.INSTANCE);
    when(repository.findByKeyName(ActorKeyService.INSTANCE)).thenReturn(Optional.of(stored));
    assertSame(stored, service.forInstance());
  }

  @Test
  void forUser_losingTheRace_readsTheWinnersKey() {
    final ActorKey winner = stored(7, null);
    when(repository.findByUserId(7)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
    when(repository.saveAndFlush(any(ActorKey.class)))
        .thenThrow(new DataIntegrityViolationException("Duplicate entry '7'"));

    assertSame(winner, service.forUser(bob));
  }

  @Test
  void forUser_rethrowsWhenTheInsertFailsForAnotherReason() {
    when(repository.saveAndFlush(any(ActorKey.class)))
        .thenThrow(new DataIntegrityViolationException("no such user"));
    assertThrows(DataIntegrityViolationException.class, () -> service.forUser(bob));
  }

  /** A repository that, like the table's unique key, takes only the first row per user. */
  @Test
  void forUser_concurrentFirstRequestsAllGetTheSameKey() throws Exception {
    final Map<Integer, ActorKey> table = new ConcurrentHashMap<>();
    when(repository.findByUserId(7)).thenAnswer(inv -> Optional.ofNullable(table.get(7)));
    when(repository.saveAndFlush(any(ActorKey.class)))
        .thenAnswer(
            inv -> {
              final ActorKey key = inv.getArgument(0);
              if (table.putIfAbsent(key.getUserId(), key) != null)
                throw new DataIntegrityViolationException("Duplicate entry");
              return key;
            });

    final int threads = 6;
    final ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      final CountDownLatch start = new CountDownLatch(1);
      final List<Future<ActorKey>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return service.forUser(bob);
                }));
      }
      start.countDown();
      for (final Future<ActorKey> result : results) {
        final ActorKey got = result.get(); // wait before reading the table
        assertSame(table.get(7), got);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void privateKeyFor_localActors() throws Exception {
    when(repository.findByUserId(7)).thenReturn(Optional.of(stored(7, null)));
    when(repository.findByKeyName(ActorKeyService.INSTANCE))
        .thenReturn(Optional.of(stored(null, ActorKeyService.INSTANCE)));
    final byte[] expected = TestKeys.privateKey().getEncoded();

    assertArrayEquals(
        expected, service.privateKeyFor("https://www.justjournal.com/users/bob").getEncoded());
    assertArrayEquals(
        expected, service.privateKeyFor("https://www.justjournal.com/actor").getEncoded());
  }

  @Test
  void privateKeyFor_refusesOtherActors() {
    for (final String actor :
        new String[] {
          "https://elsewhere.social/users/bob",
          "http://www.justjournal.com/users/bob",
          "https://www.justjournal.com/users/nobody",
          "https://www.justjournal.com/users/bob/inbox",
          "https://www.justjournal.com/users/",
          null
        }) {
      assertThrows(IllegalArgumentException.class, () -> service.privateKeyFor(actor), actor);
    }
    verify(repository, never()).saveAndFlush(any());
  }
}
