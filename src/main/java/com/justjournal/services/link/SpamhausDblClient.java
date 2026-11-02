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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.InternetDomainName;
import jakarta.annotation.PreDestroy;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Looks domains up in the Spamhaus Domain Block List, a DNS zone of domains used for spam,
 * phishing, malware and botnet control.
 *
 * <p>Both the registered domain ({@code example.co.uk} for {@code www.example.co.uk}) and the full
 * host are queried, since DBL lists some subdomains on their own. A name that doesn't exist in the
 * zone is clean; an answer in 127.0.1.0/24 means listed. Spamhaus answers 127.255.255.x (and
 * 127.0.1.255) when it won't answer the question, most often because the query came through a
 * public resolver such as 8.8.8.8; that and anything else unexpected is treated as unknown and
 * logged, at most once a minute. Production needs its own recursive resolver, or a DQS zone set
 * in {@code link-safety.dbl.zone}.
 *
 * <p>Off unless {@code link-safety.dbl.enabled} is set, so tests and development stay offline.
 */
@Slf4j
@Component
public class SpamhausDblClient {

  private static final long WARNING_INTERVAL_MS = 60_000;
  private static final int THREADS = 4;
  private static final int QUEUE = 64;

  @Setter
  @Value("${link-safety.dbl.enabled:false}")
  private boolean enabled;

  @Setter
  @Value("${link-safety.dbl.zone:dbl.spamhaus.org}")
  private String zone;

  private final long timeoutMs;
  private final Cache<String, Reputation> cache;
  private final ExecutorService executor;
  private final AtomicLong lastWarning = new AtomicLong();

  public SpamhausDblClient(
      @Value("${link-safety.dbl.timeout-ms:2000}") final long timeoutMs,
      @Value("${link-safety.cache-minutes:30}") final long cacheMinutes) {
    this.timeoutMs = timeoutMs;
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(cacheMinutes, TimeUnit.MINUTES)
            .build();
    this.executor = LinkThreads.bounded("link-dbl", THREADS, QUEUE);
  }

  /** Whether lookups are made at all; when off, every check is {@link Reputation#UNKNOWN}. */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Look a host up.
   *
   * @param asciiHost host name, as {@link NormalizedUrl#asciiHost()} has it
   * @return LISTED if the domain or the host is listed, CLEAN if neither is, UNKNOWN if DBL didn't
   *     give a usable answer for one of them in time or is turned off
   */
  public Reputation check(final String asciiHost) {
    if (!enabled || asciiHost == null || asciiHost.isBlank()) return Reputation.UNKNOWN;

    final long deadline = System.currentTimeMillis() + timeoutMs;
    final List<Reputation> verdicts = new ArrayList<>();
    final List<Future<Reputation>> pending = new ArrayList<>();
    for (final String name : namesFor(asciiHost)) {
      final Reputation cached = cache.getIfPresent(name);
      if (cached != null) {
        verdicts.add(cached);
        continue;
      }
      try {
        pending.add(executor.submit(() -> query(name)));
      } catch (final RejectedExecutionException e) {
        verdicts.add(Reputation.UNKNOWN);
      }
    }

    for (final Future<Reputation> future : pending) {
      try {
        final long remaining = Math.max(0, deadline - System.currentTimeMillis());
        verdicts.add(future.get(remaining, TimeUnit.MILLISECONDS));
      } catch (final TimeoutException e) {
        future.cancel(true);
        verdicts.add(Reputation.UNKNOWN);
      } catch (final InterruptedException e) {
        future.cancel(true);
        Thread.currentThread().interrupt();
        verdicts.add(Reputation.UNKNOWN);
      } catch (final ExecutionException e) {
        log.warn("Spamhaus DBL lookup failed: {}", e.getCause().getClass().getSimpleName());
        verdicts.add(Reputation.UNKNOWN);
      }
    }

    if (verdicts.contains(Reputation.LISTED)) return Reputation.LISTED;
    if (verdicts.contains(Reputation.UNKNOWN)) return Reputation.UNKNOWN;
    return Reputation.CLEAN;
  }

  /** The registered domain, then the full host if it is different. */
  static List<String> namesFor(final String host) {
    String domain = host;
    try {
      final InternetDomainName name = InternetDomainName.from(host);
      if (name.isUnderPublicSuffix()) domain = name.topPrivateDomain().toString();
    } catch (final IllegalArgumentException | IllegalStateException e) {
      // not a name Guava understands; query the host as it is
    }
    return domain.equals(host) ? List.of(host) : List.of(domain, host);
  }

  private Reputation query(final String name) {
    final InetAddress[] answers;
    try {
      answers = lookup(name + "." + zoneName());
    } catch (final UnknownHostException e) {
      cache.put(name, Reputation.CLEAN); // NXDOMAIN: not listed
      return Reputation.CLEAN;
    }

    if (answers != null) {
      for (final InetAddress answer : answers) {
        if (isListing(answer)) {
          cache.put(name, Reputation.LISTED);
          return Reputation.LISTED;
        }
      }
    }
    warnUnexpected(answers);
    return Reputation.UNKNOWN;
  }

  /** 127.0.1.0/24, except 127.0.1.255, which means "IP queries are not allowed". */
  private static boolean isListing(final InetAddress answer) {
    if (!(answer instanceof Inet4Address)) return false;
    final byte[] b = answer.getAddress();
    return b[0] == 127 && b[1] == 0 && b[2] == 1 && (b[3] & 0xff) != 255;
  }

  private void warnUnexpected(final InetAddress[] answers) {
    final long now = System.currentTimeMillis();
    final long last = lastWarning.get();
    if (now - last < WARNING_INTERVAL_MS || !lastWarning.compareAndSet(last, now)) return;
    final String answer =
        answers == null || answers.length == 0 ? "nothing" : answers[0].getHostAddress();
    log.warn(
        "Spamhaus DBL answered {}, which is an error code or not a DBL answer; treating links as"
            + " unknown. Queries through public resolvers are refused; use a local resolver or a"
            + " DQS zone.",
        answer);
  }

  private String zoneName() {
    String name = zone == null ? "" : zone.strip();
    while (name.startsWith(".")) name = name.substring(1);
    while (name.endsWith(".")) name = name.substring(0, name.length() - 1);
    return name;
  }

  /**
   * The DNS query, run on the client's own threads; overridable for tests. The trailing dot makes
   * the name absolute, so the resolver's search domains are never appended.
   *
   * @param queryName the name to look up, such as {@code example.com.dbl.spamhaus.org}
   * @return the addresses in the answer
   * @throws UnknownHostException if the name doesn't exist
   */
  protected InetAddress[] lookup(final String queryName) throws UnknownHostException {
    return InetAddress.getAllByName(queryName + ".");
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}
