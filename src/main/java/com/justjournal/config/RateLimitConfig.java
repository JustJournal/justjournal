package com.justjournal.config;

import com.giffing.bucket4j.spring.boot.starter.config.cache.ProxyManagerWrapper;
import com.giffing.bucket4j.spring.boot.starter.config.cache.SyncCacheResolver;
import com.giffing.bucket4j.spring.boot.starter.config.cache.redis.jedis.JedisCacheResolver;
import com.giffing.bucket4j.spring.boot.starter.context.RateLimitResult;
import com.giffing.bucket4j.spring.boot.starter.context.RateLimitResultWrapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis backing for the bucket4j rate limit filter (configured under {@code bucket4j} in
 * application.yml).
 *
 * <p>The servlet filter only works with a synchronous cache, and Jedis is the only synchronous
 * Redis client bucket4j supports, so it gets its own small pool next to the Lettuce connection
 * used for caching. If Redis is down, requests are let through rather than failing the site.
 */
@Slf4j
@Configuration
// matchIfMissing like the starter itself: profiles without bucket4j settings still load the
// starter, which refuses to start without this cache
@ConditionalOnProperty(prefix = "bucket4j", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

  static final int TIMEOUT_MS = 1_000;
  static final long WARN_INTERVAL_MS = 60_000;

  @Bean(destroyMethod = "close")
  public JedisPool rateLimitJedisPool(final Environment env) {
    // read directly: the filter, and so this pool, is built with the web server, which can be
    // before ${...} placeholders are resolvable
    final String host = env.getProperty("spring.data.redis.host", "localhost");
    final int port = env.getProperty("spring.data.redis.port", Integer.class, 6379);
    final JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(16);
    config.setMaxIdle(8);
    config.setMinIdle(0);
    config.setMaxWait(Duration.ofMillis(TIMEOUT_MS));
    config.setJmxEnabled(false);
    return new JedisPool(config, host, port, TIMEOUT_MS);
  }

  /** Replaces the starter's Jedis resolver, which would turn a Redis outage into 500s. */
  @Bean
  public SyncCacheResolver rateLimitCacheResolver(final JedisPool rateLimitJedisPool) {
    return new FailOpenCacheResolver(new JedisCacheResolver(rateLimitJedisPool));
  }

  /** Lets requests through, logging at most once a minute, when the bucket store can't be reached. */
  static final class FailOpenCacheResolver implements SyncCacheResolver {

    private static final RateLimitResultWrapper ALLOWED =
        new RateLimitResultWrapper(
            RateLimitResult.builder().consumed(true).remainingTokens(0).build());

    private final SyncCacheResolver delegate;
    private final AtomicLong lastWarning = new AtomicLong();

    FailOpenCacheResolver(final SyncCacheResolver delegate) {
      this.delegate = delegate;
    }

    @Override
    public ProxyManagerWrapper resolve(final String cacheName) {
      final ProxyManagerWrapper proxy = delegate.resolve(cacheName);
      return (key, numTokens, estimate, config, listener, version, strategy) -> {
        try {
          return proxy.tryConsumeAndReturnRemaining(
              key, numTokens, estimate, config, listener, version, strategy);
        } catch (final RuntimeException e) {
          warn(e);
          return ALLOWED;
        }
      };
    }

    private void warn(final RuntimeException e) {
      final long now = System.currentTimeMillis();
      final long last = lastWarning.get();
      if (now - last >= WARN_INTERVAL_MS && lastWarning.compareAndSet(last, now)) {
        log.warn("Rate limiting skipped, bucket store unavailable: {}", e.toString());
      }
    }
  }
}
