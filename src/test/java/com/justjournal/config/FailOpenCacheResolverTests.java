package com.justjournal.config;

import static org.junit.jupiter.api.Assertions.*;

import com.giffing.bucket4j.spring.boot.starter.config.cache.ProxyManagerWrapper;
import com.giffing.bucket4j.spring.boot.starter.context.RateLimitResult;
import com.giffing.bucket4j.spring.boot.starter.context.RateLimitResultWrapper;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;

class FailOpenCacheResolverTests {

  private static RateLimitResultWrapper consume(final ProxyManagerWrapper proxy) {
    return proxy.tryConsumeAndReturnRemaining("key", 1, false, null, null, 1L, null);
  }

  @Test
  void storeDown_requestAllowed() {
    final ProxyManagerWrapper failing =
        (key, tokens, estimate, config, listener, version, strategy) -> {
          throw new JedisConnectionException("Connection refused");
        };
    final RateLimitConfig.FailOpenCacheResolver resolver =
        new RateLimitConfig.FailOpenCacheResolver(name -> failing);

    final RateLimitResultWrapper result = consume(resolver.resolve("buckets"));

    assertTrue(result.getRateLimitResult().isConsumed());
  }

  @Test
  void storeUp_resultPassedThrough() {
    final RateLimitResultWrapper rejected =
        new RateLimitResultWrapper(RateLimitResult.builder().consumed(false).build());
    final RateLimitConfig.FailOpenCacheResolver resolver =
        new RateLimitConfig.FailOpenCacheResolver(
            name -> (key, tokens, estimate, config, listener, version, strategy) -> rejected);

    assertSame(rejected, consume(resolver.resolve("buckets")));
  }
}
