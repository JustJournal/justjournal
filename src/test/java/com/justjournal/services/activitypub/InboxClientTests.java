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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.PublicAddresses;
import com.justjournal.services.link.SafeDnsResolver;
import com.justjournal.services.link.UrlPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The inbox POST client. Refusals use the real {@link UrlPolicy} and the strict public address
 * rule; the request tests run the real HttpClient against a local server, with a resolver that
 * answers {@code *.inbox-test.com} with 127.0.0.1 and a policy that lets its port through.
 */
class InboxClientTests {

  private static final byte[] BODY =
      "{\"type\":\"Create\",\"name\":\"Café\"}".getBytes(StandardCharsets.UTF_8);

  private HttpServer server;
  private int port;
  private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
  private final Map<String, String> received = new ConcurrentHashMap<>();
  private final List<byte[]> bodies = new CopyOnWriteArrayList<>();
  private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    port = server.getAddress().getPort();
    handle(
        "/inbox",
        ex -> {
          for (final String name : List.of("Content-Type", "Signature", "Accept")) {
            received.put(name, String.valueOf(ex.getRequestHeaders().getFirst(name)));
          }
          received.put("Method", ex.getRequestMethod());
          bodies.add(ex.getRequestBody().readAllBytes());
          send(ex, 202, new byte[0]);
        });
    handle("/redirect", ex -> {
      ex.getResponseHeaders().add("Location", "/inbox");
      send(ex, 302, new byte[0]);
    });
    handle("/busy", ex -> send(ex, 503, "try later".getBytes(StandardCharsets.UTF_8)));
    handle("/gone", ex -> send(ex, 410, new byte[0]));
    handle("/huge", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/html");
      ex.sendResponseHeaders(200, 0);
      try (OutputStream os = ex.getResponseBody()) {
        final byte[] chunk = new byte[64 * 1024];
        for (int i = 0; i < 64; i++) os.write(chunk); // 4 MiB, chunked
      } catch (final IOException ignored) {
        // the client hung up, as expected
      }
    });
    handle("/slow", ex -> {
      try {
        Thread.sleep(3000);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      send(ex, 202, new byte[0]);
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

  private static void send(final HttpExchange ex, final int status, final byte[] body)
      throws IOException {
    ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      try (OutputStream os = ex.getResponseBody()) {
        os.write(body);
      }
    }
    ex.close();
  }

  private int hits(final String path) {
    final AtomicInteger count = hits.get(path);
    return count == null ? 0 : count.get();
  }

  /** A resolver with fixed answers, so no test touches real DNS. */
  private static SafeDnsResolver resolver(
      final Map<String, String> answers, final Predicate<InetAddress> allowed) {
    return new SafeDnsResolver(2000, allowed) {
      @Override
      protected InetAddress[] lookup(final String host) throws UnknownHostException {
        String address = answers.get(host);
        if (address == null && host.endsWith(".inbox-test.com")) address = answers.get("*");
        if (address == null) throw new UnknownHostException(host);
        return new InetAddress[] {InetAddress.getByName(address)};
      }
    };
  }

  /** The client the application builds: real policy, public addresses only. */
  private InboxClient strictClient(final Map<String, String> answers) {
    final SafeDnsResolver resolver = resolver(answers, PublicAddresses::isPublic);
    final InboxClient client =
        new InboxClient(InboxClient.buildClient(resolver, 1000, 1000, "test"), new UrlPolicy());
    closeables.add(client::close);
    closeables.add(resolver::close);
    return client;
  }

  /** A client for the local server: plain http, any port, loopback allowed. */
  private InboxClient localClient(final int readTimeoutMs) throws LinkRejectedException {
    final SafeDnsResolver resolver = resolver(Map.of("*", "127.0.0.1"), a -> true);
    final UrlPolicy lenient = mock(UrlPolicy.class);
    when(lenient.normalize(any(URI.class)))
        .thenAnswer(
            inv -> {
              final URI uri = inv.getArgument(0);
              return new NormalizedUrl(uri, uri, uri.getHost());
            });
    final InboxClient client =
        new InboxClient(InboxClient.buildClient(resolver, 1000, readTimeoutMs, "test"), lenient);
    client.setAllowHttp(true);
    closeables.add(client::close);
    closeables.add(resolver::close);
    return client;
  }

  private URI local(final String path) {
    return URI.create("http://site.inbox-test.com:" + port + path);
  }

  // ---- refused before anything is sent -----------------------------------------------------

  @Test
  void refusesPlainHttp() {
    final InboxClient client = strictClient(Map.of("*", "93.184.216.34"));

    assertThrows(
        InboxRefusedException.class,
        () -> client.post(URI.create("http://mastodon.inbox-test.com/inbox"), BODY, Map.of()));
    assertThrows(
        InboxRefusedException.class, () -> client.check(URI.create("ftp://inbox-test.com/x")));
    assertThrows(InboxRefusedException.class, () -> client.check(URI.create("/inbox")));
    assertThrows(InboxRefusedException.class, () -> client.check(null));
  }

  @Test
  void allowsHttpOnlyWhenConfigured() throws Exception {
    final InboxClient client = strictClient(Map.of());
    client.setAllowHttp(true);

    assertEquals(
        URI.create("http://mastodon.inbox-test.com/inbox"),
        client.check(URI.create("http://Mastodon.inbox-test.com/inbox#frag")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://127.0.0.1/inbox",
        "https://10.0.0.1/inbox",
        "https://192.168.1.10/inbox",
        "https://169.254.169.254/latest/meta-data/",
        "https://[::1]/inbox",
        "https://[fe80::1]/inbox",
        "https://2130706433/inbox",
        "https://localhost/inbox",
        "https://inbox.local/inbox",
        "https://intranet/inbox",
        "https://trusted.inbox-test.com@evil.inbox-test.com/inbox",
        "https://mastodon.inbox-test.com:8443/inbox"
      })
  void refusesAddressesThatAreNotPublicNames(final String inbox) {
    final InboxClient client = strictClient(Map.of("*", "93.184.216.34"));

    assertThrows(InboxRefusedException.class, () -> client.post(URI.create(inbox), BODY, Map.of()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"127.0.0.1", "10.1.2.3", "172.16.0.5", "169.254.169.254", "::1", "fe80::1"})
  void refusesNamesThatResolveToPrivateLoopbackOrLinkLocalAddresses(final String address) {
    final InboxClient client = strictClient(Map.of("*", address));

    final InboxRefusedException refused =
        assertThrows(
            InboxRefusedException.class,
            () -> client.post(URI.create("https://sneaky.inbox-test.com/inbox"), BODY, Map.of()));
    assertTrue(refused.getMessage().contains("public address"), refused.getMessage());
  }

  @Test
  void aNameThatDoesNotResolve_isWorthRetrying() {
    final InboxClient client = strictClient(Map.of());

    final IOException failure =
        assertThrows(
            IOException.class,
            () -> client.post(URI.create("https://gone.example.com/inbox"), BODY, Map.of()));
    assertTrue(failure instanceof UnknownHostException, failure.toString());
  }

  // ---- talking to a server ------------------------------------------------------------------

  @Test
  void postsTheActivityAsActivityJsonWithTheSignature() throws Exception {
    final InboxClient client = localClient(1000);

    final int status =
        client.post(
            local("/inbox"),
            BODY,
            Map.of(
                "Signature", "keyId=\"k\"",
                "Content-Length", "1",
                "Content-Type", "text/plain"));

    assertEquals(202, status);
    assertEquals("POST", received.get("Method"));
    assertEquals("application/activity+json", received.get("Content-Type"));
    assertEquals("keyId=\"k\"", received.get("Signature"));
    assertTrue(received.get("Accept").startsWith("application/activity+json"));
    assertArrayEquals(BODY, bodies.get(0));
  }

  @Test
  void doesNotFollowRedirects() throws Exception {
    final InboxClient client = localClient(1000);

    assertEquals(302, client.post(local("/redirect"), BODY, Map.of()));
    assertEquals(1, hits("/redirect"));
    assertEquals(0, hits("/inbox"));
  }

  @Test
  void returnsErrorStatuses() throws Exception {
    final InboxClient client = localClient(1000);

    assertEquals(503, client.post(local("/busy"), BODY, Map.of()));
    assertEquals(410, client.post(local("/gone"), BODY, Map.of()));
  }

  @Test
  void stopsReadingAHugeResponse() throws Exception {
    final InboxClient client = localClient(5000);

    final long start = System.currentTimeMillis();
    assertEquals(200, client.post(local("/huge"), BODY, Map.of()));
    assertTrue(System.currentTimeMillis() - start < 5000);
  }

  @Test
  void aSlowServer_timesOutAndIsWorthRetrying() throws Exception {
    final InboxClient client = localClient(300);

    final IOException failure =
        assertThrows(IOException.class, () -> client.post(local("/slow"), BODY, Map.of()));
    assertTrue(!(failure instanceof UnknownHostException), failure.toString());
  }

  @Test
  void theWholeRequestHasADeadline() throws Exception {
    final InboxClient client = localClient(10_000);
    client.setMaxRequestMs(300);

    final long start = System.currentTimeMillis();
    assertThrows(IOException.class, () -> client.post(local("/slow"), BODY, Map.of()));
    assertTrue(System.currentTimeMillis() - start < 2500);
  }
}
