package com.justjournal.rss;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

/**
 * Downloads remote RSS feeds with hard limits, so a slow or hostile feed server can't tie up the
 * calling thread (and any database connection it holds) indefinitely.
 */
@Slf4j
@Component
public class FeedFetcher {

  static final int CONNECT_TIMEOUT_MS = 5_000;
  static final int READ_TIMEOUT_MS = 10_000;
  /** Upper bound on the whole download; the read timeout alone allows a slow trickle forever. */
  static final long MAX_DOWNLOAD_MS = 30_000;
  static final int MAX_BYTES = 2 * 1024 * 1024;

  private final CloseableHttpClient httpClient;

  public FeedFetcher() {
    this(
        HttpClients.custom()
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT_MS)
                    .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                    .setSocketTimeout(READ_TIMEOUT_MS)
                    .build())
            .setUserAgent("JustJournal RSS Reader (+https://www.justjournal.com/)")
            .setMaxConnTotal(10)
            .setMaxConnPerRoute(2)
            .build());
  }

  FeedFetcher(final CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /** Result of a fetch; {@code body} is only set for a 200 response. */
  public record Result(int statusCode, String body) {
    public boolean isOk() {
      return statusCode == 200 && body != null;
    }
  }

  /**
   * Fetch a feed.
   *
   * @param uri feed address
   * @return the status code and, for a 200, the decoded body
   * @throws IOException on connection failure, timeout, or a feed larger than {@link #MAX_BYTES}
   */
  public Result fetch(final String uri) throws IOException {
    final HttpGet get = new HttpGet(uri);
    try (CloseableHttpResponse response = httpClient.execute(get)) {
      final int code = response.getStatusLine().getStatusCode();
      final HttpEntity entity = response.getEntity();
      if (code != 200 || entity == null) {
        return new Result(code, null);
      }

      final Charset charset = charsetOf(entity);
      final long deadline = System.currentTimeMillis() + MAX_DOWNLOAD_MS;
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final byte[] buffer = new byte[8192];
      try (InputStream in = entity.getContent()) {
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
          if (out.size() > MAX_BYTES) {
            get.abort();
            throw new IOException("Feed larger than " + MAX_BYTES + " bytes: " + uri);
          }
          if (System.currentTimeMillis() > deadline) {
            get.abort();
            throw new IOException("Feed download took longer than " + MAX_DOWNLOAD_MS + "ms: " + uri);
          }
        }
      }
      return new Result(code, out.toString(charset));
    }
  }

  private static Charset charsetOf(final HttpEntity entity) {
    try {
      final ContentType type = ContentType.get(entity);
      if (type != null && type.getCharset() != null) {
        return type.getCharset();
      }
    } catch (final RuntimeException e) {
      log.debug("Unusable content type on feed: {}", e.getMessage());
    }
    return StandardCharsets.UTF_8;
  }

  @PreDestroy
  public void close() throws IOException {
    httpClient.close();
  }
}
