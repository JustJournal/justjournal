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
package com.justjournal.services.link;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.DnsResolver;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Downloads pages and images from links writers add, without letting the link reach anything but
 * the public internet or tie the server up.
 *
 * <p>Every connection resolves through {@link SafeDnsResolver}, so a private address is refused at
 * connect time whatever the name resolved to when the link was saved. Redirects are followed by
 * hand, at most {@code link-preview.max-redirects} of them, and each target goes through {@link
 * LinkSafetyService#checkHop}. There are no cookies, no authentication and no proxy (the system
 * proxy settings are deliberately not used, since a proxy would resolve names itself).
 *
 * <p>The size cap counts the bytes after HttpClient has decompressed them, so a small compressed
 * response can't expand past it. The whole fetch, redirects included, must finish within {@code
 * link-preview.max-download-ms}; the request is aborted when the time is up, even mid-read.
 */
@Slf4j
@Component
public class SafeHttpFetcher {

  /** What a successful fetch got. {@code charset} is null when the response didn't name one. */
  public record Result(URI finalUri, String contentType, Charset charset, byte[] body) {}

  private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

  private final CloseableHttpClient httpClient;
  private final LinkSafetyService linkSafety;
  private final ScheduledThreadPoolExecutor deadlines;

  @Setter
  @Value("${link-preview.max-redirects:5}")
  private int maxRedirects = 5;

  @Setter
  @Value("${link-preview.max-download-ms:20000}")
  private long maxDownloadMs = 20_000;

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public SafeHttpFetcher(
      final SafeDnsResolver resolver,
      final LinkSafetyService linkSafety,
      @Value("${link-preview.connect-timeout-ms:5000}") final int connectTimeoutMs,
      @Value("${link-preview.read-timeout-ms:10000}") final int readTimeoutMs,
      @Value("${link-preview.user-agent:JustJournal link preview (+https://www.justjournal.com/)}")
          final String userAgent) {
    this(buildClient(resolver, connectTimeoutMs, readTimeoutMs, userAgent), linkSafety);
  }

  SafeHttpFetcher(final CloseableHttpClient httpClient, final LinkSafetyService linkSafety) {
    this.httpClient = httpClient;
    this.linkSafety = linkSafety;
    this.deadlines = new ScheduledThreadPoolExecutor(1, LinkThreads.daemonThreads("link-fetch"));
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
        .setMaxConnTotal(10)
        .setMaxConnPerRoute(2)
        .build();
  }

  /**
   * Fetch a link, following redirects.
   *
   * @param start a link that passed {@link LinkSafetyService#checkForSave}
   * @param acceptTypes media types to accept, lower case, such as {@code text/html}
   * @param maxBytes largest body to accept, after decompression
   * @return where the fetch ended up and what it got
   * @throws LinkRejectedException if a redirect or a connection leads somewhere it may not
   * @throws FetchException if the server answered with something unusable
   * @throws IOException on connection failures and timeouts, which are worth retrying
   */
  public Result fetch(final NormalizedUrl start, final Set<String> acceptTypes, final int maxBytes)
      throws IOException, LinkRejectedException {
    final Set<String> accepted =
        acceptTypes.stream().map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    final long deadline = System.currentTimeMillis() + maxDownloadMs;
    URI current = start.fetchUri();
    int redirects = 0;

    while (true) {
      final long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) throw tooSlow();
      final HttpGet get = new HttpGet(current);
      get.setHeader(HttpHeaders.ACCEPT, String.join(", ", accepted));
      final ScheduledFuture<?> timer = deadlines.schedule(get::abort, remaining, TimeUnit.MILLISECONDS);
      try (CloseableHttpResponse response = execute(get)) {
        final int status = response.getStatusLine().getStatusCode();

        if (REDIRECTS.contains(status)) {
          final Header location = response.getFirstHeader(HttpHeaders.LOCATION);
          get.abort(); // never read a redirect's body
          if (location == null || location.getValue() == null || location.getValue().isBlank())
            throw new FetchException(status, true, "Redirect without a Location header");
          if (++redirects > maxRedirects)
            throw new FetchException(status, true, "More than " + maxRedirects + " redirects");
          current = linkSafety.checkHop(resolve(current, location.getValue(), status)).fetchUri();
          continue;
        }

        if (status != 200) {
          get.abort();
          throw new FetchException(status, status >= 400 && status < 500, "HTTP status " + status);
        }
        return read(get, response, current, accepted, maxBytes, deadline);
      } catch (final FetchException e) {
        throw e;
      } catch (final IOException e) {
        if (System.currentTimeMillis() >= deadline) throw tooSlow();
        throw e;
      } finally {
        timer.cancel(false);
      }
    }
  }

  private CloseableHttpResponse execute(final HttpGet get)
      throws IOException, LinkRejectedException {
    try {
      return httpClient.execute(get);
    } catch (final IOException e) {
      for (Throwable cause = e; cause != null; cause = cause.getCause()) {
        if (cause instanceof NonPublicAddressException)
          throw LinkRejectedException.of(
              LinkRejectedException.Reason.PRIVATE_ADDRESS, get.getURI().getHost());
      }
      throw e;
    }
  }

  private Result read(
      final HttpGet get,
      final CloseableHttpResponse response,
      final URI uri,
      final Set<String> accepted,
      final int maxBytes,
      final long deadline)
      throws IOException {
    final HttpEntity entity = response.getEntity();
    if (entity == null) throw new FetchException(200, true, "Response has no body");

    final String value = entity.getContentType() == null ? null : entity.getContentType().getValue();
    final String mimeType = mimeTypeOf(value);
    if (mimeType == null || !accepted.contains(mimeType)) {
      get.abort();
      throw new FetchException(200, true, "Unexpected content type " + mimeType);
    }
    // the length of a compressed response is unknown (-1) here; the read loop catches those
    if (entity.getContentLength() > maxBytes) {
      get.abort();
      throw new FetchException(200, true, "Response larger than " + maxBytes + " bytes");
    }

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final byte[] buffer = new byte[8192];
    try (InputStream in = entity.getContent()) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        if (out.size() + read > maxBytes) {
          get.abort();
          throw new FetchException(200, true, "Response larger than " + maxBytes + " bytes");
        }
        out.write(buffer, 0, read);
        if (System.currentTimeMillis() > deadline) {
          get.abort();
          throw tooSlow();
        }
      }
    }
    return new Result(uri, mimeType, charsetOf(value), out.toByteArray());
  }

  private static URI resolve(final URI current, final String location, final int status)
      throws FetchException {
    try {
      return current.resolve(new URI(location.strip()));
    } catch (final URISyntaxException | IllegalArgumentException e) {
      throw new FetchException(status, true, "Redirect to an unusable Location");
    }
  }

  static String mimeTypeOf(final String contentType) {
    if (contentType == null) return null;
    final int semicolon = contentType.indexOf(';');
    final String type =
        (semicolon < 0 ? contentType : contentType.substring(0, semicolon))
            .strip()
            .toLowerCase(Locale.ROOT);
    return type.isEmpty() ? null : type;
  }

  private static Charset charsetOf(final String contentType) {
    if (contentType == null) return null;
    try {
      return ContentType.parse(contentType).getCharset();
    } catch (final RuntimeException e) {
      log.debug("Unusable charset in content type: {}", e.getMessage());
      return null;
    }
  }

  private IOException tooSlow() {
    return new IOException("Download took longer than " + maxDownloadMs + "ms");
  }

  @PreDestroy
  public void close() throws IOException {
    deadlines.shutdownNow();
    httpClient.close();
  }
}
