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

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.ActorKey;
import com.justjournal.model.User;
import com.justjournal.repository.ActorKeyRepository;
import com.justjournal.repository.UserRepository;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Date;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The key pairs local actors sign with: one per user and one for the instance actor. A key is made
 * the first time it is asked for.
 *
 * <p>Reads and the insert run in transactions of their own, so a key can be made while serving a
 * read only request, and so a failed insert doesn't spoil the caller's transaction. When two
 * requests make the same key at once, the unique keys on the table let one insert win; the other
 * reads the winner's row back, so everyone ends up with the same key.
 */
@Slf4j
@Service
public class ActorKeyService {
  /** {@code key_name} of the instance actor's key. */
  public static final String INSTANCE = "instance";

  static final int KEY_SIZE = 2048;

  private static final String USERS = "users/";
  private static final String ACTOR = "actor";

  private final ActorKeyRepository actorKeyRepository;
  private final UserRepository userRepository;
  private final Settings settings;
  private final TransactionTemplate readTransaction;
  private final TransactionTemplate writeTransaction;

  public ActorKeyService(
      final ActorKeyRepository actorKeyRepository,
      final UserRepository userRepository,
      final Settings settings,
      final PlatformTransactionManager transactionManager) {
    this.actorKeyRepository = actorKeyRepository;
    this.userRepository = userRepository;
    this.settings = settings;
    this.readTransaction = new TransactionTemplate(transactionManager);
    this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.readTransaction.setReadOnly(true);
    this.writeTransaction = new TransactionTemplate(transactionManager);
    this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** The user's key, made now if they have none yet. */
  public ActorKey forUser(final User user) {
    final Integer userId = user.getId();
    return getOrCreate(
        () -> actorKeyRepository.findByUserId(userId),
        key -> key.setUserId(userId),
        "user " + userId);
  }

  /** The instance actor's key, made now if there is none yet. */
  public ActorKey forInstance() {
    return getOrCreate(
        () -> actorKeyRepository.findByKeyName(INSTANCE),
        key -> key.setKeyName(INSTANCE),
        "the instance actor");
  }

  /** The instance actor, which signs requests made on behalf of the site rather than a user. */
  public String instanceActorId() {
    return base() + ACTOR;
  }

  /** A user's actor, the same address {@code ActivityStreamController} serves. */
  public String userActorId(final String username) {
    return base() + USERS + username;
  }

  /** The id other servers know an actor's key by. */
  public static String keyId(final String actorId) {
    return actorId + "#main-key";
  }

  /**
   * The private key of a local actor, the user's or the instance's.
   *
   * @throws IllegalArgumentException if the actor isn't one of ours
   */
  public PrivateKey privateKeyFor(final String actorId) {
    final ActorKey key;
    if (instanceActorId().equals(actorId)) {
      key = forInstance();
    } else {
      final String prefix = base() + USERS;
      final String username =
          actorId != null && actorId.startsWith(prefix) ? actorId.substring(prefix.length()) : null;
      if (username == null || !Login.isUserName(username))
        throw new IllegalArgumentException("Not a local actor: " + actorId);
      final User user = userRepository.findByUsername(username);
      if (user == null) throw new IllegalArgumentException("No such user: " + username);
      key = forUser(user);
    }
    try {
      return Pem.privateKey(key.getPrivateKeyPem());
    } catch (final GeneralSecurityException e) {
      throw new IllegalStateException("Stored private key " + key.getId() + " is unreadable", e);
    }
  }

  private ActorKey getOrCreate(
      final Supplier<Optional<ActorKey>> find,
      final Consumer<ActorKey> owner,
      final String whose) {
    final ActorKey existing = readTransaction.execute(status -> find.get().orElse(null));
    if (existing != null) return existing;

    final ActorKey created = newKey();
    owner.accept(created);
    try {
      final ActorKey saved =
          writeTransaction.execute(status -> actorKeyRepository.saveAndFlush(created));
      log.info("Made an ActivityPub key for {}", whose);
      return saved;
    } catch (final DataIntegrityViolationException e) {
      // someone else made it first
      final ActorKey winner = readTransaction.execute(status -> find.get().orElse(null));
      if (winner == null) throw e;
      return winner;
    }
  }

  private static ActorKey newKey() {
    final KeyPairGenerator generator;
    try {
      generator = KeyPairGenerator.getInstance("RSA");
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA is not available", e);
    }
    generator.initialize(KEY_SIZE);
    final KeyPair pair = generator.generateKeyPair();
    final ActorKey key = new ActorKey();
    key.setPublicKeyPem(Pem.encode(pair.getPublic()));
    key.setPrivateKeyPem(Pem.encode(pair.getPrivate()));
    key.setCreated(new Date());
    return key;
  }

  /** Same as the controllers: ids are always https. */
  private String base() {
    return settings.getBaseUri().replace("http://", "https://");
  }
}
