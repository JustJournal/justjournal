package com.justjournal.repository.cache;

import static com.justjournal.core.CacheKeys.ADMIN_TWO_FACTOR_ATTEMPTS_KEY;
import static com.justjournal.core.CacheKeys.ADMIN_TWO_FACTOR_CODE_KEY;
import static com.justjournal.core.CacheKeys.ADMIN_TWO_FACTOR_THROTTLE_KEY;

import java.time.Duration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The admin login's mailed code, kept in redis apart from member two factor state so it expires on
 * its own: the hashed code, a resend throttle and a failed attempt counter, each keyed by the
 * admin username.
 *
 * @author Lucas Holt
 */
@Component
public class AdminTwoFactorRepository {

  private final ReactiveRedisTemplate<String, String> reactiveRedisTemplateString;

  private final ReactiveValueOperations<String, String> valOperations;

  public AdminTwoFactorRepository(
      final ReactiveRedisTemplate<String, String> reactiveRedisTemplateString) {
    this.reactiveRedisTemplateString = reactiveRedisTemplateString;
    this.valOperations = reactiveRedisTemplateString.opsForValue();
  }

  /** Store the hash of the code last mailed, replacing any earlier one. */
  public Mono<Boolean> saveCode(final String username, final String codeHash, final Duration ttl) {
    return valOperations.set(ADMIN_TWO_FACTOR_CODE_KEY + username, codeHash, ttl);
  }

  /** @return hash of the outstanding code, or empty if none or expired */
  public Mono<String> getCode(final String username) {
    return valOperations.get(ADMIN_TWO_FACTOR_CODE_KEY + username);
  }

  public Mono<Boolean> deleteCode(final String username) {
    return valOperations.delete(ADMIN_TWO_FACTOR_CODE_KEY + username);
  }

  /** @return true if a code may be mailed now, false if one went out within the window */
  public Mono<Boolean> tryAcquireSend(final String username, final Duration window) {
    return valOperations.setIfAbsent(ADMIN_TWO_FACTOR_THROTTLE_KEY + username, "1", window);
  }

  /** Allow another code to be mailed right away, after one that could not be sent. */
  public Mono<Boolean> releaseSend(final String username) {
    return valOperations.delete(ADMIN_TWO_FACTOR_THROTTLE_KEY + username);
  }

  /** @return number of failed code attempts in the window, including this one */
  public Mono<Long> incrementAttempts(final String username, final Duration window) {
    final String key = ADMIN_TWO_FACTOR_ATTEMPTS_KEY + username;
    return valOperations
        .increment(key)
        .flatMap(
            count ->
                count == 1L
                    ? reactiveRedisTemplateString.expire(key, window).thenReturn(count)
                    : Mono.just(count));
  }

  /** @return failed code attempts in the current window, or empty if none */
  public Mono<String> getAttempts(final String username) {
    return valOperations.get(ADMIN_TWO_FACTOR_ATTEMPTS_KEY + username);
  }

  public Mono<Boolean> clearAttempts(final String username) {
    return valOperations.delete(ADMIN_TWO_FACTOR_ATTEMPTS_KEY + username);
  }
}
