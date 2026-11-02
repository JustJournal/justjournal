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

import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NonPublicAddressException;
import com.justjournal.services.link.SafeDnsResolver;
import com.justjournal.services.link.UrlPolicy;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.DnsResolver;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Posts activities to inboxes on other servers. Inbox URLs come from those servers, so they are
 * treated as hostile: https only, the host must pass {@link UrlPolicy} (no IP literals, local
 * names, unusual ports or blocked domains), and every connection resolves through {@link
 * SafeDnsResolver}, so it only ever reaches the public address that was checked. Redirects are
 * not followed, there are no cookies, authentication or proxy, and the response body is read only
 * up to a small cap and then thrown away. The whole request must finish within {@code
 * activitypub.delivery.max-request-ms}; it is aborted when the time is up.
 */
@Slf4j
@Component
public class InboxClient {

  public static final String ACTIVITY_JSON = "application/activity+json";
  static final ContentType ACTIVITY_CONTENT_TYPE = ContentType.create(ACTIVITY_JSON);

  /** Most of a response body read before the connection is dropped instead. */
  static final int MAX_RESPONSE_BYTES = 16 * 1024;

  /** Headers HttpClient sets itself from the body; a signer must not set them twice. */
  private static final Set<String> RESERVED_HEADERS =
      Set.of("content-length", "transfer-encoding", "content-type");

  private final CloseableHttpClient httpClient;
  private final UrlPolicy urlPolicy;
  private final ScheduledThreadPoolExecutor deadlines;

  /** Allows plain http inboxes, for testing against a local server only. */
  @Setter
  @Value("${activitypub.delivery.allow-http:false}")
  private boolean allowHttp;

  @Setter
  @Value("${activitypub.delivery.max-request-ms:30000}")
  private long maxRequestMs = 30_000;

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public InboxClient(
      final SafeDnsResolver resolver,
      final UrlPolicy urlPolicy,
      @Value("${activitypub.delivery.connect-timeout-ms:5000}") final int connectTimeoutMs,
      @Value("${activitypub.delivery.read-timeout-ms:10000}") final int readTimeoutMs,
      @Value("${activitypub.delivery.user-agent:JustJournal (+https://www.justjournal.com/)}")
          final String userAgent) {
    this(buildClient(resolver, connectTimeoutMs, readTimeoutMs, userAgent), urlPolicy);
  }

  InboxClient(final CloseableHttpClient httpClient, final UrlPolicy urlPolicy) {
    this.httpClient = httpClient;
    this.urlPolicy = urlPolicy;
    final AtomicInteger count = new AtomicInteger();
    this.deadlines =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              final Thread thread =
                  new Thread(runnable, "inbox-deadline-" + count.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
    this.deadlines.setRemoveOnCancelPolicy(true);
  }

  /** The HttpClient set up as described above; package visible so tests use the real thing. */
  static CloseableHttpClient buildClient(
      final DnsResolver resolver,
      final int connectTimeoutMs,
      final int readTimeoutMs,
      final String userAgent) {
    return HttpClients.custom()
        .setDnsResolver(resolver)
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .setRedirectsEnabled(false)
                .setAuthenticationEnabled(false)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build())
        .disableRedirectHandling()
        .disableCookieManagement()
        .disableAuthCaching()
        .disableAutomaticRetries()
        .setUserAgent(userAgent)
        .setMaxConnTotal(20)
        .setMaxConnPerRoute(2)
        .build();
  }

  /**
   * Check an inbox URL before anything is signed or sent.
   *
   * @param inbox the inbox as another server gave it
   * @return the URL to sign and post to: the same inbox, host in lower case, no fragment
   * @throws InboxRefusedException if it may never be posted to
   */
  public URI check(final URI inbox) throws InboxRefusedException {
    if (inbox == null || inbox.getScheme() == null) {
      throw new InboxRefusedException("Inbox is not an absolute URL");
    }
    final String scheme = inbox.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
      throw new InboxRefusedException("Inbox is not https");
    }
    try {
      return urlPolicy.normalize(inbox).fetchUri();
    } catch (final LinkRejectedException e) {
      throw new InboxRefusedException("Inbox refused: " + e.reason());
    }
  }

  /**
   * Post an activity.
   *
   * @param inbox where to post it; checked again here
   * @param body the activity as JSON
   * @param headers extra headers, such as the signature
   * @return the HTTP status of the response; a redirect is returned, not followed
   * @throws InboxRefusedException if the inbox may never be posted to
   * @throws IOException on connection failures and timeouts, which are worth retrying
   */
  public int post(final URI inbox, final byte[] body, final Map<String, String> headers)
      throws InboxRefusedException, IOException {
    final URI target = check(inbox);
    final HttpPost post = new HttpPost(target);
    post.setHeader(HttpHeaders.ACCEPT, ACTIVITY_JSON + ", application/ld+json");
    if (headers != null) {
      headers.forEach(
          (name, value) -> {
            if (name != null && value != null && !RESERVED_HEADERS.contains(lower(name))) {
              post.setHeader(name, value);
            }
          });
    }
    post.setEntity(new ByteArrayEntity(body, ACTIVITY_CONTENT_TYPE));

    final ScheduledFuture<?> timer =
        deadlines.schedule(post::abort, maxRequestMs, TimeUnit.MILLISECONDS);
    final long deadline = System.currentTimeMillis() + maxRequestMs;
    try (CloseableHttpResponse response = execute(post)) {
      final int status = response.getStatusLine().getStatusCode();
      discard(post, response.getEntity(), deadline);
      return status;
    } catch (final IOException e) {
      if (System.currentTimeMillis() >= deadline) {
        throw new IOException("Delivery took longer than " + maxRequestMs + "ms");
      }
      throw e;
    } finally {
      timer.cancel(false);
    }
  }

  private CloseableHttpResponse execute(final HttpPost post)
      throws IOException, InboxRefusedException {
    try {
      return httpClient.execute(post);
    } catch (final IOException e) {
      for (Throwable cause = e; cause != null; cause = cause.getCause()) {
        if (cause instanceof NonPublicAddressException) {
          throw new InboxRefusedException("Inbox host does not resolve to a public address");
        }
      }
      throw e;
    }
  }

  /**
   * Read a little of the body, so a small response leaves the connection reusable; anything
   * bigger, slower than the deadline, or broken, drops the connection. The status is already
   * known, so nothing here fails the delivery.
   */
  private static void discard(final HttpPost post, final HttpEntity entity, final long deadline) {
    if (entity == null) return;
    if (entity.getContentLength() > MAX_RESPONSE_BYTES) {
      post.abort();
      return;
    }
    final byte[] buffer = new byte[4096];
    int total = 0;
    try (InputStream in = entity.getContent()) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        total += read;
        if (total > MAX_RESPONSE_BYTES || System.currentTimeMillis() > deadline) {
          post.abort();
          return;
        }
      }
    } catch (final IOException e) {
      post.abort();
    }
  }

  private static String lower(final String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  @PreDestroy
  public void close() throws IOException {
    deadlines.shutdownNow();
    httpClient.close();
  }
}
