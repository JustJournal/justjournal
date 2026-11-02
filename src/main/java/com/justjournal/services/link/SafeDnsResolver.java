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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.DnsResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves host names for anything the server fetches on a writer's behalf, and refuses names that
 * lead anywhere but the public internet.
 *
 * <p>A name fails if <em>any</em> of its addresses is not public: HttpClient may try each address
 * in turn, so one private address among public ones is enough to reach the internal network. Used
 * as HttpClient's {@link DnsResolver}, the check happens on every connection, so a redirect or a
 * DNS answer that changes between the check at save time and the fetch (DNS rebinding) can't slip
 * through.
 *
 * <p>The JVM's own lookup can't be given a timeout, so it runs on a small pool of daemon threads
 * and the caller stops waiting after {@code link-safety.dns-timeout-ms}.
 */
@Slf4j
@Component
public class SafeDnsResolver implements DnsResolver {

  private static final int THREADS = 4;
  private static final int QUEUE = 64;

  private final long timeoutMs;
  private final Predicate<InetAddress> allowed;
  private final ExecutorService executor;

  @Autowired // the other constructor is for tests
  public SafeDnsResolver(@Value("${link-safety.dns-timeout-ms:3000}") final long timeoutMs) {
    this(timeoutMs, PublicAddresses::isPublic);
  }

  /**
   * @param timeoutMs how long to wait for an answer
   * @param allowed which addresses a name may resolve to
   */
  public SafeDnsResolver(final long timeoutMs, final Predicate<InetAddress> allowed) {
    this.timeoutMs = timeoutMs;
    this.allowed = allowed;
    this.executor = LinkThreads.bounded("link-dns", THREADS, QUEUE);
  }

  /**
   * Resolve a host name.
   *
   * @param host host name
   * @return every address of the host, all of them allowed
   * @throws NonPublicAddressException if any address is not allowed
   * @throws UnknownHostException if the name doesn't exist, has no addresses, or the lookup timed
   *     out
   */
  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    final Future<InetAddress[]> future;
    try {
      future = executor.submit(() -> lookup(host));
    } catch (final RejectedExecutionException e) {
      throw new UnknownHostException(host + ": too many DNS lookups in progress");
    }

    final InetAddress[] addresses;
    try {
      addresses = future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      future.cancel(true);
      throw new UnknownHostException(host + ": DNS lookup timed out");
    } catch (final InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new UnknownHostException(host + ": DNS lookup interrupted");
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof UnknownHostException unknown) throw unknown;
      final UnknownHostException failure = new UnknownHostException(host + ": DNS lookup failed");
      failure.initCause(e.getCause());
      throw failure;
    }

    if (addresses == null || addresses.length == 0)
      throw new UnknownHostException(host + ": no addresses");
    for (final InetAddress address : addresses) {
      if (!allowed.test(address)) {
        log.debug("Refusing {}: resolves to {}", host, address.getHostAddress());
        throw new NonPublicAddressException(host);
      }
    }
    return addresses;
  }

  /** The actual lookup, run on the resolver's own threads; overridable for tests. */
  protected InetAddress[] lookup(final String host) throws UnknownHostException {
    return InetAddress.getAllByName(host);
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}
