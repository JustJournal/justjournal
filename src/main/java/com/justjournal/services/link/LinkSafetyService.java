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
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The one place that decides whether a link may be saved or followed.
 *
 * <p>A link is checked in order of cost: its text ({@link UrlPolicy}), then DNS ({@link
 * SafeDnsResolver}, every address must be public), then Spamhaus DBL and Google Safe Browsing,
 * which run at the same time. A service that is turned off is skipped. A service that is on but
 * gives no answer lets the link through, unless {@code link-safety.fail-closed} is set.
 */
@Slf4j
@Service
public class LinkSafetyService {

  /** Upper bound on waiting for the reputation services; each also has its own timeout. */
  static final long REPUTATION_WAIT_MS = 10_000;

  private static final int THREADS = 4;
  private static final int QUEUE = 32;

  private final UrlPolicy urlPolicy;
  private final SafeDnsResolver resolver;
  private final SpamhausDblClient dbl;
  private final SafeBrowsingClient safeBrowsing;
  // when the pool is busy the caller runs the check itself, which is slower but never refuses
  private final ExecutorService executor =
      LinkThreads.bounded(
          "link-reputation",
          THREADS,
          QUEUE,
          new ThreadPoolExecutor.CallerRunsPolicy());

  /** Refuse links the reputation services couldn't vouch for. */
  @Setter
  @Value("${link-safety.fail-closed:false}")
  private boolean failClosed;

  public LinkSafetyService(
      final UrlPolicy urlPolicy,
      final SafeDnsResolver resolver,
      final SpamhausDblClient dbl,
      final SafeBrowsingClient safeBrowsing) {
    this.urlPolicy = urlPolicy;
    this.resolver = resolver;
    this.dbl = dbl;
    this.safeBrowsing = safeBrowsing;
  }

  /**
   * Check a link a writer wants to save.
   *
   * @param raw the link as typed
   * @return the canonical link, which is what should be stored
   * @throws LinkRejectedException with a message for the writer, if any check fails
   */
  public NormalizedUrl checkForSave(final String raw) throws LinkRejectedException {
    final NormalizedUrl url = urlPolicy.normalize(raw);
    try {
      resolver.resolve(url.asciiHost());
    } catch (final NonPublicAddressException e) {
      throw LinkRejectedException.of(LinkRejectedException.Reason.PRIVATE_ADDRESS, url.asciiHost());
    } catch (final UnknownHostException e) {
      throw LinkRejectedException.of(LinkRejectedException.Reason.DNS, url.asciiHost());
    }
    checkReputation(url);
    return url;
  }

  /**
   * Check a redirect target while fetching. DNS is not checked here: the fetcher's connections go
   * through {@link SafeDnsResolver}, which checks every address it connects to.
   *
   * @param next where the redirect points, already resolved against the current address
   * @return the canonical form of {@code next}
   * @throws LinkRejectedException if the target fails the policy or a reputation check
   */
  public NormalizedUrl checkHop(final URI next) throws LinkRejectedException {
    final NormalizedUrl url = urlPolicy.normalize(next);
    checkReputation(url);
    return url;
  }

  private void checkReputation(final NormalizedUrl url) throws LinkRejectedException {
    final CompletableFuture<Reputation> dblVerdict =
        dbl.enabled() ? ask(() -> dbl.check(url.asciiHost())) : null;
    final CompletableFuture<Reputation> safeBrowsingVerdict =
        safeBrowsing.enabled() ? ask(() -> safeBrowsing.check(url.fetchUri().toString())) : null;

    final long deadline = System.currentTimeMillis() + REPUTATION_WAIT_MS;
    final Reputation fromDbl = await(dblVerdict, deadline, "Spamhaus DBL");
    final Reputation fromSafeBrowsing = await(safeBrowsingVerdict, deadline, "Safe Browsing");

    if (fromDbl == Reputation.LISTED)
      throw LinkRejectedException.of(LinkRejectedException.Reason.DBL, url.asciiHost());
    if (fromSafeBrowsing == Reputation.LISTED)
      throw LinkRejectedException.of(LinkRejectedException.Reason.SAFE_BROWSING, url.asciiHost());
    if (failClosed) {
      if (fromDbl == Reputation.UNKNOWN)
        throw LinkRejectedException.of(LinkRejectedException.Reason.DBL, url.asciiHost());
      if (fromSafeBrowsing == Reputation.UNKNOWN)
        throw LinkRejectedException.of(LinkRejectedException.Reason.SAFE_BROWSING, url.asciiHost());
    }
  }

  private CompletableFuture<Reputation> ask(final Supplier<Reputation> check) {
    return CompletableFuture.supplyAsync(check, executor);
  }

  /** The verdict, or null for a service that is off. */
  private static Reputation await(
      final CompletableFuture<Reputation> verdict, final long deadline, final String service) {
    if (verdict == null) return null;
    try {
      final Reputation result =
          verdict.get(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
      return result == null ? Reputation.UNKNOWN : result;
    } catch (final TimeoutException e) {
      verdict.cancel(true);
      log.warn("{} gave no answer in {}ms", service, REPUTATION_WAIT_MS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final ExecutionException e) {
      log.warn("{} check failed: {}", service, e.getCause().getClass().getSimpleName());
    }
    return Reputation.UNKNOWN;
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}
