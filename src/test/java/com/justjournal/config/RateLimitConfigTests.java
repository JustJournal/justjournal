package com.justjournal.config;

import static org.junit.jupiter.api.Assertions.*;

import com.giffing.bucket4j.spring.boot.starter.config.cache.Bucket4jCacheConfiguration;
import com.giffing.bucket4j.spring.boot.starter.config.cache.SyncCacheResolver;
import com.giffing.bucket4j.spring.boot.starter.config.filter.servlet.Bucket4JAutoConfigurationServletFilter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;

/**
 * Runs the bucket4j filter with the production (default profile) settings on a real Tomcat, with
 * the buckets in a Redis on localhost:6379; skipped when there is none. The filter is only
 * installed on an embedded server, so MockMvc can't be used.
 */
@EnabledIf("redisAvailable")
@SpringBootTest(
    classes = RateLimitConfigTests.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
class RateLimitConfigTests {

  static final int BURST = 50;

  private final HttpClient http = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private SyncCacheResolver cacheResolver;

  static boolean redisAvailable() {
    try (Jedis jedis = new Jedis("localhost", 6379, 500)) {
      return "PONG".equals(jedis.ping());
    } catch (final RuntimeException e) {
      return false;
    }
  }

  /** A fresh public address per test, so buckets left in Redis by an earlier run don't interfere. */
  private static String randomIp() {
    final ThreadLocalRandom r = ThreadLocalRandom.current();
    return "203.0." + r.nextInt(256) + "." + r.nextInt(1, 255);
  }

  /** Request as Apache would send it, from a trusted proxy (localhost) with X-Forwarded-For. */
  private int status(final String path, final String clientIp) throws Exception {
    final HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Forwarded-For", clientIp)
            .build();
    return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private void useUpBurst(final String clientIp) throws Exception {
    for (int i = 0; i < BURST; i++) {
      assertEquals(200, status("/users/jjsite", clientIp), "request " + (i + 1));
    }
  }

  /**
   * The burst bucket refills greedily, one token every 200 ms, so a slow machine earns a few
   * tokens back while the burst is being used up and the request right after it may still pass.
   * Keep sending until one is rejected; that must happen within another burst's worth of requests,
   * which only a client slower than the refill itself could miss. The header is built per request
   * so that a spoofed address which did get through would land in a fresh bucket each time.
   */
  private void assertRejected(final Supplier<String> forwardedFor) throws Exception {
    for (int i = 0; i < BURST; i++) {
      if (status("/users/jjsite", forwardedFor.get()) == 429) {
        return;
      }
    }
    fail("no request was rejected within " + BURST + " requests past the burst");
  }

  @Test
  void usesFailOpenResolver() {
    assertInstanceOf(RateLimitConfig.FailOpenCacheResolver.class, cacheResolver);
  }

  @Test
  void pages_rejectedOnceBurstUsedUp() throws Exception {
    final String ip = randomIp();
    useUpBurst(ip);
    assertRejected(() -> ip);
  }

  @Test
  void limitIsPerForwardedClientAddress() throws Exception {
    final String ip = randomIp();
    useUpBurst(ip);
    assertRejected(() -> ip);
    assertEquals(200, status("/users/jjsite", randomIp()));
  }

  @Test
  void spoofedForwardedFor_ignoredBeforeTrustedProxy() throws Exception {
    final String ip = randomIp();
    useUpBurst(ip);
    // a client can't escape its bucket by prepending a made-up address; Apache appends the real one
    assertRejected(() -> randomIp() + ", " + ip);
  }

  @Test
  void staticFiles_notLimited() throws Exception {
    final String ip = randomIp();
    useUpBurst(ip);
    assertRejected(() -> ip);
    assertEquals(200, status("/static/app.js", ip));
    assertEquals(200, status("/favicon.ico", ip));
    assertEquals(200, status("/Avatar/1", ip));
  }

  @SpringBootConfiguration
  @ImportAutoConfiguration({
    ConfigurationPropertiesAutoConfiguration.class,
    ServletWebServerFactoryAutoConfiguration.class,
    EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    WebMvcAutoConfiguration.class,
    Bucket4jCacheConfiguration.class,
    Bucket4JAutoConfigurationServletFilter.class
  })
  @Import({RateLimitConfig.class, TestController.class})
  static class TestApp {}

  @RestController
  static class TestController {
    @GetMapping({"/users/{user}", "/static/{file}", "/favicon.ico", "/Avatar/{id}"})
    String ok() {
      return "ok";
    }
  }
}
