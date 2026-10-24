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
package com.justjournal.rss;

import static com.justjournal.core.Constants.endl;

import com.justjournal.model.RssCache;
import com.justjournal.repository.RssCacheRepository;
import java.io.StringReader;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Renders RSS content stored in the datastore as HTML.
 *
 * <p>This runs while a page request holds a database connection, so it never goes to the network.
 * {@link com.justjournal.core.RssCacheRefresh} downloads feeds in the background; a feed seen here
 * for the first time gets an empty cache record that the next refresh fills in.
 *
 * @author Lucas Holt
 * @version $Id: CachedHeadlineBean.java,v 1.7 2011/05/29 22:32:59 laffer1 Exp $
 *     <p>Previously version was incremented manually. 1.4 Attempt to catch case were RSS record
 *     content is empty in database. 1.3 Added sun fix to correct sun.com rss feeds (rss .92 issue?)
 *     xml declaration was missing in feed and whitespace at front. 1.2 Fixed bug where records
 *     might not get updated. 1.1 optimized code 1.0 Initial release
 */
@Slf4j
@Component
public class CachedHeadlineBean extends HeadlineBean {

  static final Date NEVER_UPDATED =
      Date.from(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant());

  private final RssCacheRepository rssCacheRepository;

  public CachedHeadlineBean(
      final FeedFetcher feedFetcher, final RssCacheRepository rssCacheRepository) {
    super(feedFetcher);
    this.rssCacheRepository = rssCacheRepository;
  }

  @Override
  protected Optional<HeadlineContext> getRssDocument(@NonNull final String uri) throws Exception {
    final RssCache rss = rssCacheRepository.findFirstByUriOrderByIdAsc(uri);

    if (rss == null) {
      queueForRefresh(uri);
      return Optional.empty();
    }

    if (rss.getContent() == null || rss.getContent().isBlank()) {
      return Optional.empty();
    }

    return createHeadlineContext(rss, new HeadlineContext());
  }

  /** Only a syntax check here; the background refresh finds out whether the host exists. */
  @Override
  protected boolean isValidFeedAddress(@NonNull final String url) {
    try {
      final URI parsed = URI.create(url.trim());
      final String scheme = parsed.getScheme();
      return parsed.getHost() != null
          && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    } catch (final IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  protected String unavailableMessage(@NonNull final String url) {
    return "<p>This feed hasn't been downloaded yet, or is currently unavailable. Check back later.</p>"
        + endl;
  }

  /**
   * Create an empty record dated long ago so the next background refresh picks the feed up.
   * (lastupdated is NOT NULL in the schema, so it can't be left empty.)
   */
  private void queueForRefresh(final String uri) {
    final RssCache rss = new RssCache();
    rss.setUri(uri);
    rss.setLastUpdated(NEVER_UPDATED);
    rss.setInterval(24);
    rss.setActive(true);
    rss.setErrorCount(0);
    rss.setContent("");
    rssCacheRepository.save(rss);
    log.info("Queued new RSS feed for background refresh: {}", uri);
  }

  private Optional<HeadlineContext> createHeadlineContext(RssCache rss, HeadlineContext hc)
      throws Exception {
    try {
      hc.factory.setValidating(false);
      hc.factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      hc.builder = hc.factory.newDocumentBuilder();
      hc.document = hc.builder.parse(new InputSource(new StringReader(rss.getContent())));
      return Optional.of(hc);
    } catch (final SAXParseException saxParseException) {
      log.error("Error parsing RSS content: {}", rss.getUri(), saxParseException);
      return Optional.empty();
    }
  }
}
