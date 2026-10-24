package com.justjournal.rss;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeedFetcherTests {

  private HttpServer server;
  private String base;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/feed",
        ex -> {
          final byte[] body = "<rss><channel><title>Café</title></channel></rss>".getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/rss+xml; charset=UTF-8");
          ex.sendResponseHeaders(200, body.length);
          try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    server.createContext(
        "/gone",
        ex -> {
          ex.sendResponseHeaders(410, -1);
          ex.close();
        });
    server.createContext(
        "/huge",
        ex -> {
          ex.sendResponseHeaders(200, 0);
          final byte[] chunk = new byte[64 * 1024];
          try (OutputStream os = ex.getResponseBody()) {
            for (int i = 0; i < 64; i++) os.write(chunk); // 4 MB
          } catch (final IOException ignored) {
            // client hung up, as expected
          }
        });
    server.createContext(
        "/stall",
        ex -> {
          ex.sendResponseHeaders(200, 0);
          try {
            Thread.sleep(5_000);
          } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
          ex.close();
        });
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void fetch_decodesBodyWithResponseCharset() throws IOException {
    final FeedFetcher.Result result = new FeedFetcher().fetch(base + "/feed");
    assertTrue(result.isOk());
    assertTrue(result.body().contains("Café"), result.body());
  }

  @Test
  void fetch_returnsStatusWithoutBody_whenNot200() throws IOException {
    final FeedFetcher.Result result = new FeedFetcher().fetch(base + "/gone");
    assertEquals(410, result.statusCode());
    assertFalse(result.isOk());
    assertNull(result.body());
  }

  @Test
  void fetch_rejectsOversizedFeed() {
    final IOException e =
        assertThrows(IOException.class, () -> new FeedFetcher().fetch(base + "/huge"));
    assertTrue(e.getMessage().contains("larger than"), e.getMessage());
  }

  @Test
  void fetch_givesUpOnStalledServer() {
    // same client setup as production, with a short read timeout so the test stays fast
    final FeedFetcher fetcher =
        new FeedFetcher(
            HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom().setSocketTimeout(300).build())
                .build());
    final long start = System.currentTimeMillis();
    assertThrows(IOException.class, () -> fetcher.fetch(base + "/stall"));
    assertTrue(System.currentTimeMillis() - start < 4_000);
  }
}
