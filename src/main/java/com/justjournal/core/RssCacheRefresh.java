/*
 * Copyright (c) 2003-2021 Lucas Holt
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
package com.justjournal.core;

import com.justjournal.model.RssCache;
import com.justjournal.repository.RssCacheRepository;
import com.justjournal.rss.FeedFetcher;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Update the RSS cache.
 *
 * <p>This is the only place feeds are downloaded; page requests read the cache only. Feeds are
 * fetched one at a time on the scheduler thread, and no database connection is held while waiting
 * on a remote server.
 *
 * @author Lucas Holt
 */
@Slf4j
@Component
@Profile("!test")
public class RssCacheRefresh {

  static final int MAX_ERROR_FETCH = 5;

  private final RssCacheRepository rssCacheDao;

  private final FeedFetcher feedFetcher;

  public RssCacheRefresh(final RssCacheRepository rssCacheDao, final FeedFetcher feedFetcher) {
    this.rssCacheDao = rssCacheDao;
    this.feedFetcher = feedFetcher;
  }

  @Scheduled(fixedDelay = 1000 * 60 * 30, initialDelay = 60000)
  public void run() {
    log.info("RssCache: Init");

    try {
      final Calendar yesterday = Calendar.getInstance();
      yesterday.add(Calendar.DATE, -1);

      final List<RssCache> items = rssCacheDao.findDueForRefresh(yesterday.getTime());
      log.info("RssCache: {} feeds due for refresh", items.size());

      for (final RssCache cache : items) {
        try {
          refresh(cache);
        } catch (final RuntimeException e) {
          log.error("RssCache: could not update {}", cache.getUri(), e);
        }
      }
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }

    log.info("RssCache: Quit");
  }

  /**
   * Download one feed and store the result. Failures are counted, and a feed that keeps failing
   * is switched off.
   *
   * @param rss cache record to refresh
   * @return the saved record, or null if it has no usable address
   */
  RssCache refresh(final RssCache rss) {
    if (rss == null || rss.getUri() == null || rss.getUri().length() < 11) return null;

    final String previousContent = rss.getContent();
    final int previousErrors = rss.getErrorCount();
    try {
      final FeedFetcher.Result result = feedFetcher.fetch(rss.getUri());
      final int code = result.statusCode();

      if (result.isOk()) {
        final String content = cleanContent(result.body());
        if (content.isEmpty()) {
          return failed(rss, "returned an HTML page or no content");
        }
        rss.setLastUpdated(Calendar.getInstance().getTime());
        rss.setContent(content);
        rss.setErrorCount(0);
        return rssCacheDao.saveAndFlush(rss);
      }

      if (code == 404 || code == 410) {
        log.warn("URL {} is returning a {}. Removing from list", rss.getUri(), code);
        rss.setLastUpdated(Calendar.getInstance().getTime());
        rss.setActive(false);
        return rssCacheDao.saveAndFlush(rss);
      }

      return failed(rss, "returned status code " + code);
    } catch (final IOException | RuntimeException e) {
      // if saving the new content is what failed, recording the failure must not try it again
      rss.setContent(previousContent);
      rss.setErrorCount(previousErrors);
      return failed(rss, e.toString());
    }
  }

  /** Record a failed fetch; it is retried after a day, and switched off after repeated failures. */
  private RssCache failed(final RssCache rss, final String reason) {
    log.warn("RssCache: {} {}", rss.getUri(), reason);
    rss.setLastUpdated(Calendar.getInstance().getTime());
    rss.setErrorCount(rss.getErrorCount() + 1);
    if (rss.getErrorCount() > MAX_ERROR_FETCH) {
      log.warn("RssCache: disabling {} after {} failures", rss.getUri(), rss.getErrorCount());
      rss.setActive(false);
    }
    return rssCacheDao.saveAndFlush(rss);
  }

  private String cleanContent(final String content) {
    if (content == null) return "";

    final String trimmed = content.trim();

    if (trimmed.startsWith("<rss")) return ("<?xml version=\"1.0\"?>\n" + trimmed);

    if (trimmed.startsWith("<html") || trimmed.regionMatches(true, 0, "<!DOCTYPE HTML", 0, 14))
      return ""; // it's an html page.. bad

    return trimmed;
  }
}
