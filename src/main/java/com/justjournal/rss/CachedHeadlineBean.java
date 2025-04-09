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


import com.justjournal.model.DateTime;
import com.justjournal.model.DateTimeBean;
import com.justjournal.model.RssCache;
import com.justjournal.repository.RssCacheRepository;
import com.justjournal.utility.StringUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Optional;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;

/**
 * Stores RSS content collected from the internet into a datastore, retrieves stored versions, and
 * spits out HTML to render them.
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

  private final RssCacheRepository rssCacheRepository;

  @Autowired
  public CachedHeadlineBean(final RssCacheRepository rssCacheRepository) {
    this.rssCacheRepository = rssCacheRepository;
  }

  @Override
  protected Optional<HeadlineContext> getRssDocument(@NonNull final String uri) throws Exception {
    log.info("Starting getRssDocument()");
    final HeadlineContext hc = new HeadlineContext();
    final StringBuilder sbx = new StringBuilder();

    RssCache rss = fetchOrCreateRssCache(uri);

    if (shouldUpdateRssContent(rss)) {
      updateRssContent(rss, uri, sbx);
    }

    return createHeadlineContext(rss, hc);
  }

  private RssCache fetchOrCreateRssCache(@NotNull String uri) {
    log.info("looking up rss cache repo uri");
    RssCache rss = rssCacheRepository.findByUri(uri);
    log.info("rss cache lookup done");

    if (rss == null || rss.getUri() == null || rss.getUri().length() <= 10) {
        log.info("Fetch uri: {}", uri);
      rss = new RssCache();
      rss.setUri(uri);
      rss.setInterval(24);
      rss.setActive(true);
      rss.setErrorCount(0);
    }

    return rss;
  }

  private boolean shouldUpdateRssContent(RssCache rss) {
    if (Boolean.FALSE.equals(rss.getActive())) return false;

    if (rss.getLastUpdated() == null) return true;

    final DateTime dt = new DateTimeBean(rss.getLastUpdated());
    final java.util.GregorianCalendar calendarg = new java.util.GregorianCalendar();
    return dt.getDay() != calendarg.get(java.util.Calendar.DATE) || rss.getContent() == null || rss.getContent().isEmpty();
  }

  private boolean isTooManyErrors(RssCache rss) {
    return rss.getErrorCount() >= 3;
  }


  private void updateRssContent(RssCache rss, @NotNull String uri, StringBuilder sbx) throws IOException {
    log.info("Updating RSS content for: {}", uri);
    rss.setLastUpdated(Calendar.getInstance().getTime());

    int statusCode = fetchContentFromUrl(uri, sbx);

    if (statusCode != 200 || rss.getContent().startsWith("<html") || rss.getContent().startsWith("<!DOCTYPE HTML")) {
      rss.setContent("");
      if (isTooManyErrors(rss)) {
        rss.setActive(false);
      } else {
        rss.setErrorCount(rss.getErrorCount() + 1);
      }
    }

    rss.setContent(sbx.toString().trim());

    // sun can't make their own rss feeds complaint
    if (rss.getContent().startsWith("<rss"))
      rss.setContent("<?xml version=\"1.0\"?>\n" + rss.getContent());

    rssCacheRepository.saveAndFlush(rss);
  }

  private int fetchContentFromUrl(@NotNull String uri, StringBuilder sbx) throws IOException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      final HttpGet httpGet = new HttpGet(uri);
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
          log.error("Error fetching content from URL: {} status: {}", uri, statusCode);
          return statusCode;
        }
        final HttpEntity entity = response.getEntity();
        try (BufferedReader buff = new BufferedReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
          String input;
          while ((input = buff.readLine()) != null) {
            sbx.append(StringUtil.replace(input, '\'', "\\\'"));
          }
        }
      }
    } catch (final IOException e) {
        log.error("Error fetching content from URL: {}", uri, e);
      throw e;
    }

    return 200;
  }

  private Optional<HeadlineContext> createHeadlineContext(RssCache rss, HeadlineContext hc) throws Exception {
      log.info("getRssDocument() load into a parser: {}", rss.getUri());

      try {
        final StringReader sr = new StringReader(rss.getContent());
        final org.xml.sax.InputSource saxy = new org.xml.sax.InputSource(sr);
        hc.factory.setValidating(false);
        hc.factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        hc.builder = hc.factory.newDocumentBuilder();
        hc.document = hc.builder.parse(saxy);

        log.debug("Hit end of getRssDocument() for {}", rss.getUri());
        return Optional.of(hc);
      } catch (final SAXParseException saxParseException) {
        log.error("Error parsing RSS content: {}", rss.getUri(), saxParseException);
        return Optional.empty();
      }
  }

}
