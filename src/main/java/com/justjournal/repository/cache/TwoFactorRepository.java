/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2003-2023 Lucas Holt
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
package com.justjournal.repository.cache;

import static com.justjournal.core.CacheKeys.TWO_FACTOR_ATTEMPTS_KEY;
import static com.justjournal.core.CacheKeys.TWO_FACTOR_EMAIL_KEY;
import static com.justjournal.core.CacheKeys.TWO_FACTOR_EMAIL_THROTTLE_KEY;
import static com.justjournal.core.CacheKeys.TWO_FACTOR_TOTP_PENDING_KEY;
import static com.justjournal.core.CacheKeys.TWO_FACTOR_TOTP_USED_KEY;

import java.time.Duration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Short lived two factor state lives in redis so it expires on its own: hashed e-mail codes,
 * attempt counters, and authenticator secrets that have not been confirmed yet.
 *
 * @author Lucas Holt
 */
@Component
public class TwoFactorRepository {

  private final ReactiveRedisTemplate<String, String> reactiveRedisTemplateString;

  private final ReactiveValueOperations<String, String> valOperations;

  public TwoFactorRepository(
      final ReactiveRedisTemplate<String, String> reactiveRedisTemplateString) {
    this.reactiveRedisTemplateString = reactiveRedisTemplateString;
    this.valOperations = reactiveRedisTemplateString.opsForValue();
  }

  /** Store the hash of the code last mailed to a user, replacing any earlier one. */
  public Mono<Boolean> saveEmailCode(final int userId, final String codeHash, final Duration ttl) {
    return valOperations.set(TWO_FACTOR_EMAIL_KEY + userId, codeHash, ttl);
  }

  /** @return hash of the outstanding mailed code, or empty if none or expired */
  public Mono<String> getEmailCode(final int userId) {
    return valOperations.get(TWO_FACTOR_EMAIL_KEY + userId);
  }

  public Mono<Boolean> deleteEmailCode(final int userId) {
    return valOperations.delete(TWO_FACTOR_EMAIL_KEY + userId);
  }

  /** @return true if a code may be mailed now, false if one went out within the window */
  public Mono<Boolean> tryAcquireEmail(final int userId, final Duration window) {
    return valOperations.setIfAbsent(TWO_FACTOR_EMAIL_THROTTLE_KEY + userId, "1", window);
  }

  /** @return number of failed second factor attempts in the window, including this one */
  public Mono<Long> incrementAttempts(final int userId, final Duration window) {
    final String key = TWO_FACTOR_ATTEMPTS_KEY + userId;
    return valOperations
        .increment(key)
        .flatMap(
            count ->
                count == 1L
                    ? reactiveRedisTemplateString.expire(key, window).thenReturn(count)
                    : Mono.just(count));
  }

  public Mono<Boolean> clearAttempts(final int userId) {
    return valOperations.delete(TWO_FACTOR_ATTEMPTS_KEY + userId);
  }

  /** Hold an authenticator secret while the user proves their app has it. */
  public Mono<Boolean> savePendingTotpSecret(
      final int userId, final String secret, final Duration ttl) {
    return valOperations.set(TWO_FACTOR_TOTP_PENDING_KEY + userId, secret, ttl);
  }

  public Mono<String> getPendingTotpSecret(final int userId) {
    return valOperations.get(TWO_FACTOR_TOTP_PENDING_KEY + userId);
  }

  public Mono<Boolean> deletePendingTotpSecret(final int userId) {
    return valOperations.delete(TWO_FACTOR_TOTP_PENDING_KEY + userId);
  }

  /**
   * Record that a time step's code was accepted so the same code can't be replayed.
   *
   * @return true if the step was unused, false if that code was already accepted
   */
  public Mono<Boolean> markTotpStepUsed(final int userId, final long step, final Duration ttl) {
    return valOperations.setIfAbsent(TWO_FACTOR_TOTP_USED_KEY + userId + ":" + step, "1", ttl);
  }
}
