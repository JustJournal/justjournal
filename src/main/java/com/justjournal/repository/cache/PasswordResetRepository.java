/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
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
package com.justjournal.repository.cache;

import static com.justjournal.core.CacheKeys.PASSWORD_RESET_IP_KEY;
import static com.justjournal.core.CacheKeys.PASSWORD_RESET_KEY;
import static com.justjournal.core.CacheKeys.PASSWORD_RESET_THROTTLE_KEY;
import static com.justjournal.core.CacheKeys.PASSWORD_RESET_USER_KEY;

import java.time.Duration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Password reset tokens live in redis so they expire on their own. Only a hash of each token is
 * stored, keyed to the user id it resets.
 *
 * @author Lucas Holt
 */
@Component
public class PasswordResetRepository {

  private final ReactiveRedisTemplate<String, String> reactiveRedisTemplateString;

  private final ReactiveValueOperations<String, String> valOperations;

  public PasswordResetRepository(
      final ReactiveRedisTemplate<String, String> reactiveRedisTemplateString) {
    this.reactiveRedisTemplateString = reactiveRedisTemplateString;
    this.valOperations = reactiveRedisTemplateString.opsForValue();
  }

  /**
   * Store a token for a user, replacing any outstanding token so only the newest link works.
   *
   * @param tokenHash hash of the token sent to the user
   * @param userId user the token resets
   * @param ttl how long the token is good for
   */
  public Mono<Boolean> saveToken(final String tokenHash, final int userId, final Duration ttl) {
    return valOperations
        .getAndSet(PASSWORD_RESET_USER_KEY + userId, tokenHash)
        .flatMap(old -> valOperations.delete(PASSWORD_RESET_KEY + old))
        .then(reactiveRedisTemplateString.expire(PASSWORD_RESET_USER_KEY + userId, ttl))
        .then(valOperations.set(PASSWORD_RESET_KEY + tokenHash, Integer.toString(userId), ttl));
  }

  /**
   * Fetch and remove a token in one step so it can only be used once.
   *
   * @param tokenHash hash of the token the user presented
   * @return user id, or empty if the token is unknown or expired
   */
  public Mono<Integer> consumeToken(final String tokenHash) {
    return valOperations
        .getAndDelete(PASSWORD_RESET_KEY + tokenHash)
        .map(Integer::parseInt)
        .flatMap(
            userId ->
                valOperations.delete(PASSWORD_RESET_USER_KEY + userId).thenReturn(userId));
  }

  /**
   * Limit how often reset mail goes to one account.
   *
   * @return true if a reset may be sent now, false if one was sent recently
   */
  public Mono<Boolean> tryAcquireUser(final int userId, final Duration window) {
    return valOperations.setIfAbsent(PASSWORD_RESET_THROTTLE_KEY + userId, "1", window);
  }

  /**
   * Count reset requests from an address within a window.
   *
   * @return number of requests from the address in the current window, including this one
   */
  public Mono<Long> incrementIp(final String ip, final Duration window) {
    final String key = PASSWORD_RESET_IP_KEY + ip;
    return valOperations
        .increment(key)
        .flatMap(
            count ->
                count == 1L
                    ? reactiveRedisTemplateString.expire(key, window).thenReturn(count)
                    : Mono.just(count));
  }
}
