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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the real HttpClient set up against a local server. The test resolver answers every
 * {@code *.fetch-test.com} name with 127.0.0.1 and lets loopback through, except where a test
 * says otherwise; the strict production rule is tested separately.
 */
class SafeHttpFetcherTests {

  private static final Set<String> HTML = Set.of("text/html", "application/xhtml+xml");
  private static final int CAP = 64 * 1024;
  private static final byte[] PAGE =
      "<html><head><title>Café</title></head></html>".getBytes(StandardCharsets.ISO_8859_1);
  private static byte[] gzipBomb;

  private HttpServer server;
  private int port;
  private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
  private final Map<String, String> requestHeaders = new ConcurrentHashMap<>();
  private final List<URI> hops = new CopyOnWriteArrayList<>();
  private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void makeBomb() throws IOException {
    final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
      final byte[] zeros = new byte[1024 * 1024];
      for (int i = 0; i < 64; i++) gzip.write(zeros); // 64 MiB of zeros
    }
    gzipBomb = compressed.toByteArray();
  }

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();

    handle("/page", ex -> {
      requestHeaders.put("User-Agent", String.valueOf(ex.getRequestHeaders().getFirst("User-Agent")));
      requestHeaders.put("Accept", String.valueOf(ex.getRequestHeaders().getFirst("Accept")));
      requestHeaders.put("Cookie", String.valueOf(ex.getRequestHeaders().getFirst("Cookie")));
      send(ex, 200, "text/html; charset=ISO-8859-1", PAGE);
    });
    handle("/xhtml", ex -> send(ex, 200, "application/xhtml+xml", PAGE));
    handle("/signed", ex -> {
      for (final String name : List.of("X-Hop", "Accept", "Host")) {
        requestHeaders.put(name, String.valueOf(ex.getRequestHeaders().getFirst(name)));
      }
      send(ex, 200, "application/activity+json", "{}".getBytes(StandardCharsets.UTF_8));
    });
    handle("/to-signed", ex -> redirect(ex, 302, "/signed"));
    handle("/nocharset", ex -> send(ex, 200, "TEXT/HTML", PAGE));
    handle("/relative", ex -> redirect(ex, 302, "/page"));
    handle("/dir/relative-dots", ex -> redirect(ex, 301, "../page?x=1#frag"));
    handle("/other-host", ex -> redirect(ex, 307, "http://other.fetch-test.com:" + port + "/page"));
    handle("/protocol-relative", ex -> redirect(ex, 308, "//other.fetch-test.com:" + port + "/page"));
    handle("/see-other", ex -> redirect(ex, 303, "/page"));
    handle("/cookie", ex -> {
      ex.getResponseHeaders().add("Set-Cookie", "session=secret; Path=/");
      redirect(ex, 302, "/page");
    });
    handle("/chain", ex -> {
      final int left = Integer.parseInt(ex.getRequestURI().getQuery());
      redirect(ex, 302, left == 0 ? "/page" : "/chain?" + (left - 1));
    });
    handle("/to-loopback", ex -> redirect(ex, 302, "http://127.0.0.1:" + port + "/page"));
    handle("/to-metadata", ex -> redirect(ex, 302, "http://169.254.169.254/latest/meta-data/"));
    handle("/to-localhost", ex -> redirect(ex, 302, "http://localhost/page"));
    handle("/to-shortener", ex -> redirect(ex, 302, "https://bit.ly/abc"));
    handle("/to-blocked", ex -> redirect(ex, 302, "https://www.blocked.com/"));
    handle("/to-port", ex -> redirect(ex, 302, "http://site.fetch-test.com:8080/page"));
    handle("/to-ftp", ex -> redirect(ex, 302, "ftp://site.fetch-test.com/page"));
    handle("/to-private-name", ex -> redirect(ex, 302, "http://internal.fetch-test.com:" + port + "/page"));
    handle("/no-location", ex -> {
      ex.sendResponseHeaders(302, -1);
      ex.close();
    });
    handle("/bad-location", ex -> redirect(ex, 302, "http://exa mple.com/"));
    handle("/big-length", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/html");
      ex.sendResponseHeaders(200, CAP + 1);
      try (OutputStream os = ex.getResponseBody()) {
        os.write(new byte[CAP + 1]);
      } catch (final IOException ignored) {
        // client hung up, as expected
      }
    });
    handle("/exact-length", ex -> send(ex, 200, "text/html", new byte[CAP]));
    handle("/big-chunked", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/html");
      ex.sendResponseHeaders(200, 0);
      try (OutputStream os = ex.getResponseBody()) {
        for (int i = 0; i < 64; i++) os.write(new byte[16 * 1024]); // 1 MiB
      } catch (final IOException ignored) {
        // client hung up, as expected
      }
    });
    handle("/gzip-bomb", ex -> {
      ex.getResponseHeaders().add("Content-Encoding", "gzip");
      send(ex, 200, "text/html", gzipBomb);
    });
    handle("/gzip-small", ex -> {
      final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
        gzip.write(PAGE);
      }
      ex.getResponseHeaders().add("Content-Encoding", "gzip");
      send(ex, 200, "text/html; charset=ISO-8859-1", compressed.toByteArray());
    });
    handle("/trickle", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/html");
      ex.sendResponseHeaders(200, 0);
      try (OutputStream os = ex.getResponseBody()) {
        for (int i = 0; i < 50; i++) {
          os.write('x');
          os.flush();
          Thread.sleep(100);
        }
      } catch (final IOException | InterruptedException ignored) {
        // client hung up, as expected
      }
    });
    handle("/stall-headers", ex -> {
      try {
        Thread.sleep(5_000);
      } catch (final InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      send(ex, 200, "text/html", PAGE);
    });
    handle("/json", ex -> send(ex, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
    handle("/no-type", ex -> send(ex, 200, null, PAGE));
    handle("/status", ex -> {
      final int status = Integer.parseInt(ex.getRequestURI().getQuery());
      ex.sendResponseHeaders(status, -1);
      ex.close();
    });

    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    for (final AutoCloseable closeable : closeables) closeable.close();
    server.stop(0);
  }

  private interface Handler {
    void handle(HttpExchange exchange) throws IOException;
  }

  private void handle(final String path, final Handler handler) {
    server.createContext(
        path,
        ex -> {
          hits.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
          handler.handle(ex);
        });
  }

  private static void send(
      final HttpExchange ex, final int status, final String type, final byte[] body)
      throws IOException {
    if (type != null) ex.getResponseHeaders().add("Content-Type", type);
    ex.sendResponseHeaders(status, body.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(body);
    } catch (final IOException ignored) {
      // client hung up
    }
  }

  private static void redirect(final HttpExchange ex, final int status, final String location)
      throws IOException {
    ex.getResponseHeaders().add("Location", location);
    ex.sendResponseHeaders(status, -1);
    ex.close();
  }

  private int hits(final String path) {
    final AtomicInteger count = hits.get(path);
    return count == null ? 0 : count.get();
  }

  /** Resolver for the test names: 10.0.0.1 for internal.*, 127.0.0.1 for the rest. */
  private SafeDnsResolver resolver(final Predicate<InetAddress> allowed) {
    final SafeDnsResolver resolver =
        new SafeDnsResolver(2_000, allowed) {
          @Override
          protected InetAddress[] lookup(final String host) throws UnknownHostException {
            if (!host.endsWith(".fetch-test.com")) return super.lookup(host);
            final byte[] address =
                host.startsWith("internal.") ? new byte[] {10, 0, 0, 1} : new byte[] {127, 0, 0, 1};
            return new InetAddress[] {InetAddress.getByAddress(host, address)};
          }
        };
    closeables.add(resolver::close);
    return resolver;
  }

  private static boolean loopbackOrPublic(final InetAddress address) {
    return address.isLoopbackAddress() || PublicAddresses.isPublic(address);
  }

  /** The real policy, except that the test server's own names and port count as allowed. */
  private LinkSafetyService linkSafety() {
    final UrlPolicy policy = new UrlPolicy();
    policy.setBlockedDomains(List.of("blocked.com"));
    final LinkSafetyService service =
        new LinkSafetyService(
            policy,
            mock(SafeDnsResolver.class),
            mock(SpamhausDblClient.class),
            mock(SafeBrowsingClient.class)) {
          @Override
          public NormalizedUrl checkHop(final URI next) throws LinkRejectedException {
            hops.add(next);
            if (next.getPort() == port && next.getHost().endsWith(".fetch-test.com")) {
              final URI fetch =
                  URI.create(next.toString().replaceFirst("#.*$", ""));
              return new NormalizedUrl(next, fetch, next.getHost());
            }
            return super.checkHop(next);
          }
        };
    closeables.add(service::close);
    return service;
  }

  private SafeHttpFetcher fetcher(final SafeDnsResolver resolver) {
    final SafeHttpFetcher fetcher =
        new SafeHttpFetcher(
            SafeHttpFetcher.buildClient(resolver, 2_000, 2_000, "JustJournal test"), linkSafety());
    closeables.add(fetcher::close);
    return fetcher;
  }

  private SafeHttpFetcher fetcher() {
    return fetcher(resolver(SafeHttpFetcherTests::loopbackOrPublic));
  }

  private NormalizedUrl url(final String path) {
    return url("site.fetch-test.com", path);
  }

  private NormalizedUrl url(final String host, final String path) {
    final URI uri = URI.create("http://" + host + ":" + port + path);
    return new NormalizedUrl(uri, uri, host);
  }

  @Test
  void fetch_returnsBodyTypeAndCharset() throws Exception {
    final SafeHttpFetcher.Result result = fetcher().fetch(url("/page"), HTML, CAP);
    assertArrayEquals(PAGE, result.body());
    assertEquals("text/html", result.contentType());
    assertEquals(StandardCharsets.ISO_8859_1, result.charset());
    assertEquals(url("/page").fetchUri(), result.finalUri());
    assertEquals("JustJournal test", requestHeaders.get("User-Agent"));
    assertTrue(requestHeaders.get("Accept").contains("text/html"), requestHeaders.get("Accept"));
  }

  @Test
  void fetch_addsHeadersWorkedOutForEachHop() throws Exception {
    final List<URI> asked = new CopyOnWriteArrayList<>();
    final String accept = "application/activity+json, application/ld+json; profile=\"x\"";
    final SafeHttpFetcher.Result result =
        fetcher()
            .fetch(
                url("/to-signed"),
                Set.of("application/activity+json"),
                CAP,
                target -> {
                  asked.add(target);
                  return Map.of(
                      "X-Hop", target.getPath(),
                      "Accept", accept,
                      "Host", target.getHost() + ":" + target.getPort());
                });

    assertEquals(List.of(url("/to-signed").fetchUri(), url("/signed").fetchUri()), asked);
    assertEquals("/signed", requestHeaders.get("X-Hop"));
    assertEquals(accept, requestHeaders.get("Accept"));
    assertEquals("site.fetch-test.com:" + port, requestHeaders.get("Host"));
    assertEquals("application/activity+json", result.contentType());
  }

  @Test
  void fetch_contentTypeMatchIgnoresCase_charsetMayBeMissing() throws Exception {
    final SafeHttpFetcher.Result result = fetcher().fetch(url("/nocharset"), HTML, CAP);
    assertEquals("text/html", result.contentType());
    assertNull(result.charset());
    assertEquals("application/xhtml+xml", fetcher().fetch(url("/xhtml"), HTML, CAP).contentType());
  }

  @Test
  void fetch_followsRelativeRedirect() throws Exception {
    final SafeHttpFetcher.Result result = fetcher().fetch(url("/relative"), HTML, CAP);
    assertEquals(url("/page").fetchUri(), result.finalUri());
    assertEquals(List.of(url("/page").fetchUri()), hops);
  }

  @Test
  void fetch_resolvesDotSegmentsAndDropsFragmentFromNextRequest() throws Exception {
    final SafeHttpFetcher.Result result = fetcher().fetch(url("/dir/relative-dots"), HTML, CAP);
    assertEquals(URI.create("http://site.fetch-test.com:" + port + "/page?x=1"), result.finalUri());
  }

  @Test
  void fetch_followsEveryRedirectKind() throws Exception {
    assertEquals(
        URI.create("http://other.fetch-test.com:" + port + "/page"),
        fetcher().fetch(url("/other-host"), HTML, CAP).finalUri());
    assertEquals(
        URI.create("http://other.fetch-test.com:" + port + "/page"),
        fetcher().fetch(url("/protocol-relative"), HTML, CAP).finalUri());
    assertEquals(url("/page").fetchUri(), fetcher().fetch(url("/see-other"), HTML, CAP).finalUri());
  }

  @Test
  void fetch_neverSendsCookies() throws Exception {
    fetcher().fetch(url("/cookie"), HTML, CAP);
    assertEquals("null", requestHeaders.get("Cookie"));
  }

  @Test
  void fetch_allowsFiveRedirects() throws Exception {
    assertEquals(url("/page").fetchUri(), fetcher().fetch(url("/chain?4"), HTML, CAP).finalUri());
    assertEquals(5, hops.size());
  }

  @Test
  void fetch_refusesSixRedirects() {
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/chain?5"), HTML, CAP));
    assertTrue(e.permanent());
    assertEquals(302, e.status());
    assertEquals(0, hits("/page"));
  }

  @Test
  void fetch_maxRedirectsIsConfigurable() {
    final SafeHttpFetcher fetcher = fetcher();
    fetcher.setMaxRedirects(0);
    assertThrows(FetchException.class, () -> fetcher.fetch(url("/relative"), HTML, CAP));
  }

  @Test
  void redirectToIpLiteral_isRefused() {
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        assertThrows(LinkRejectedException.class, () -> fetcher().fetch(url("/to-loopback"), HTML, CAP))
            .reason());
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        assertThrows(LinkRejectedException.class, () -> fetcher().fetch(url("/to-metadata"), HTML, CAP))
            .reason());
    assertEquals(0, hits("/page"));
  }

  @Test
  void redirectToLocalhost_isRefused() {
    assertEquals(
        LinkRejectedException.Reason.PRIVATE_ADDRESS,
        assertThrows(
                LinkRejectedException.class, () -> fetcher().fetch(url("/to-localhost"), HTML, CAP))
            .reason());
  }

  @Test
  void redirectToShortener_isRefused() {
    assertEquals(
        LinkRejectedException.Reason.SHORTENER,
        assertThrows(
                LinkRejectedException.class, () -> fetcher().fetch(url("/to-shortener"), HTML, CAP))
            .reason());
  }

  @Test
  void redirectToBlockedDomain_isRefused() {
    assertEquals(
        LinkRejectedException.Reason.BLOCKLISTED,
        assertThrows(LinkRejectedException.class, () -> fetcher().fetch(url("/to-blocked"), HTML, CAP))
            .reason());
  }

  @Test
  void redirectToOtherPortOrScheme_isRefused() {
    assumeTrue(port != 8080);
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        assertThrows(LinkRejectedException.class, () -> fetcher().fetch(url("/to-port"), HTML, CAP))
            .reason());
    assertEquals(
        LinkRejectedException.Reason.SYNTAX,
        assertThrows(LinkRejectedException.class, () -> fetcher().fetch(url("/to-ftp"), HTML, CAP))
            .reason());
  }

  /** The hop passes the policy, but its name resolves to a private address at connect time. */
  @Test
  void redirectToNameWithPrivateAddress_isRefusedAtConnect() {
    assertEquals(
        LinkRejectedException.Reason.PRIVATE_ADDRESS,
        assertThrows(
                LinkRejectedException.class,
                () -> fetcher().fetch(url("/to-private-name"), HTML, CAP))
            .reason());
    assertEquals(0, hits("/page"));
  }

  @Test
  void redirectWithoutLocation_isPermanentFailure() {
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/no-location"), HTML, CAP));
    assertTrue(e.permanent());
    assertEquals(302, e.status());
  }

  @Test
  void redirectToUnparseableLocation_isPermanentFailure() {
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/bad-location"), HTML, CAP));
    assertTrue(e.permanent());
  }

  /** The production rule: a name that resolves to loopback is refused before connecting. */
  @Test
  void strictResolver_refusesLoopback() {
    final SafeHttpFetcher fetcher = fetcher(resolver(PublicAddresses::isPublic));
    assertEquals(
        LinkRejectedException.Reason.PRIVATE_ADDRESS,
        assertThrows(LinkRejectedException.class, () -> fetcher.fetch(url("/page"), HTML, CAP))
            .reason());
    assertEquals(0, hits("/page"));
  }

  @Test
  void productionConstructor_refusesLocalhost() {
    final SafeDnsResolver resolver = new SafeDnsResolver(2_000);
    closeables.add(resolver::close);
    final SafeHttpFetcher fetcher =
        new SafeHttpFetcher(resolver, linkSafety(), 2_000, 2_000, "JustJournal test");
    closeables.add(fetcher::close);
    assertThrows(LinkRejectedException.class, () -> fetcher.fetch(url("localhost", "/page"), HTML, CAP));
    assertEquals(0, hits("/page"));
  }

  /** The name was public when the link was saved, but points at loopback when fetched. */
  @Test
  void dnsRebinding_isRefusedAtConnect() throws Exception {
    final AtomicInteger lookups = new AtomicInteger();
    final SafeDnsResolver rebinding =
        new SafeDnsResolver(2_000, PublicAddresses::isPublic) {
          @Override
          protected InetAddress[] lookup(final String host) throws UnknownHostException {
            final byte[] address =
                lookups.getAndIncrement() == 0
                    ? new byte[] {93, (byte) 184, (byte) 216, 34}
                    : new byte[] {127, 0, 0, 1};
            return new InetAddress[] {InetAddress.getByAddress(host, address)};
          }
        };
    closeables.add(rebinding::close);

    // the check at save time sees a public address
    assertEquals(1, rebinding.resolve("rebind.fetch-test.com").length);

    final SafeHttpFetcher fetcher = fetcher(rebinding);
    assertEquals(
        LinkRejectedException.Reason.PRIVATE_ADDRESS,
        assertThrows(
                LinkRejectedException.class,
                () -> fetcher.fetch(url("rebind.fetch-test.com", "/page"), HTML, CAP))
            .reason());
    assertEquals(0, hits("/page"));
  }

  @Test
  void contentLengthOverCap_isRefusedBeforeReading() {
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/big-length"), HTML, CAP));
    assertTrue(e.permanent());
    assertTrue(e.getMessage().contains("larger than"), e.getMessage());
  }

  @Test
  void bodyExactlyAtCap_isAccepted() throws Exception {
    assertEquals(CAP, fetcher().fetch(url("/exact-length"), HTML, CAP).body().length);
  }

  @Test
  void chunkedBodyOverCap_isCutOff() {
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/big-chunked"), HTML, CAP));
    assertTrue(e.permanent());
    assertTrue(e.getMessage().contains("larger than"), e.getMessage());
  }

  @Test
  void gzipBomb_isCutOffByDecompressedSize() {
    assertTrue(gzipBomb.length < CAP * 2, "bomb is " + gzipBomb.length + " bytes compressed");
    final long start = System.currentTimeMillis();
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/gzip-bomb"), HTML, CAP));
    assertTrue(e.getMessage().contains("larger than"), e.getMessage());
    assertTrue(System.currentTimeMillis() - start < 3_000);
  }

  @Test
  void gzipBody_isDecompressed() throws Exception {
    assertArrayEquals(PAGE, fetcher().fetch(url("/gzip-small"), HTML, CAP).body());
  }

  @Test
  void slowTrickle_hitsTheDeadline() {
    final SafeHttpFetcher fetcher = fetcher();
    fetcher.setMaxDownloadMs(700);
    final long start = System.currentTimeMillis();
    final IOException e =
        assertThrows(IOException.class, () -> fetcher.fetch(url("/trickle"), HTML, CAP));
    assertFalse(e instanceof FetchException);
    assertTrue(e.getMessage().contains("longer than"), e.getMessage());
    assertTrue(System.currentTimeMillis() - start < 2_500);
  }

  /** The read timeout alone would wait 2s; the deadline cuts in sooner. */
  @Test
  void stalledHeaders_hitTheDeadline() {
    final SafeHttpFetcher fetcher = fetcher();
    fetcher.setMaxDownloadMs(500);
    final long start = System.currentTimeMillis();
    final IOException e =
        assertThrows(IOException.class, () -> fetcher.fetch(url("/stall-headers"), HTML, CAP));
    assertTrue(e.getMessage().contains("longer than"), e.getMessage());
    assertTrue(System.currentTimeMillis() - start < 1_800, "deadline did not abort the request");
  }

  @Test
  void wrongContentType_isPermanentFailure() {
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/json"), HTML, CAP));
    assertTrue(e.permanent());
    assertEquals(200, e.status());
    assertTrue(e.getMessage().contains("application/json"), e.getMessage());
  }

  @Test
  void missingContentType_isPermanentFailure() {
    assertTrue(
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/no-type"), HTML, CAP))
            .permanent());
  }

  @Test
  void clientErrors_arePermanent() {
    for (final int status : new int[] {400, 401, 403, 404, 410, 451}) {
      final FetchException e =
          assertThrows(
              FetchException.class, () -> fetcher().fetch(url("/status?" + status), HTML, CAP));
      assertEquals(status, e.status());
      assertTrue(e.permanent(), String.valueOf(status));
    }
  }

  @Test
  void serverErrors_areTransient() {
    for (final int status : new int[] {500, 502, 503, 504}) {
      final FetchException e =
          assertThrows(
              FetchException.class, () -> fetcher().fetch(url("/status?" + status), HTML, CAP));
      assertEquals(status, e.status());
      assertFalse(e.permanent(), String.valueOf(status));
    }
  }

  @Test
  void otherSuccessStatuses_areNotAccepted() {
    final FetchException e =
        assertThrows(FetchException.class, () -> fetcher().fetch(url("/status?204"), HTML, CAP));
    assertEquals(204, e.status());
  }

  @Test
  void connectionRefused_isPlainIoException() throws IOException {
    final HttpServer closed = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final int closedPort = closed.getAddress().getPort();
    closed.stop(0);
    final URI uri = URI.create("http://site.fetch-test.com:" + closedPort + "/");
    final IOException e =
        assertThrows(
            IOException.class,
            () -> fetcher().fetch(new NormalizedUrl(uri, uri, "site.fetch-test.com"), HTML, CAP));
    assertFalse(e instanceof FetchException);
  }
}
