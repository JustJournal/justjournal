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
package com.justjournal.services;


import com.justjournal.core.Settings;
import com.justjournal.model.Journal;
import com.justjournal.model.Trackback;
import com.justjournal.model.api.TrackbackTo;
import com.justjournal.repository.TrackbackRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Encoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.util.StringUtils;

import static com.justjournal.core.Constants.PATH_ENTRY;
import static com.justjournal.core.Constants.PATH_USERS;

/** @author Lucas Holt */
@Slf4j
@Service
public class TrackbackService {
  private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
  private static final String RESPONSE = "<response>";
  private static final String END_RESPONSE = "</response>";
  private static final String ERROR = "<error>";
  private static final String END_ERROR = "</error>";
  private static final String MESSAGE = "<message>";
  private static final String END_MESSAGE = "</message>";

  private final TrackbackRepository trackbackRepository;
  private final RestTemplate restTemplate;
  private final Encoder encoder;
  private Settings settings;

  @Autowired
  public TrackbackService(TrackbackRepository trackbackRepository, RestTemplate restTemplate, Encoder encoder, Settings settings) {
    this.trackbackRepository = trackbackRepository;
    this.restTemplate = restTemplate;
    this.encoder = encoder;
    this.settings = settings;
  }

  public boolean send(
      String pingUrl, String blogName, String permalink, String title, String excerpt) {
    try {
      final String cleanTitle = encoder.encodeForURL(title);
      final String cleanPermanentBlogEntryUrl = encoder.encodeForURL(permalink);
      final String cleanBlogName = encoder.encodeForURL(blogName);
      final String cleanExcerpt = encoder.encodeForURL(excerpt);

      final URI uri =
          new URI(
              pingUrl
                  + "?title="
                  + cleanTitle
                  + "&url="
                  + cleanPermanentBlogEntryUrl
                  + "&blog_name="
                  + cleanBlogName
                  + "&excerpt="
                  + cleanExcerpt);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      headers.setAccept(Collections.singletonList(MediaType.TEXT_XML));
      headers.setAcceptCharset(Collections.singletonList(StandardCharsets.UTF_8));
      HttpEntity<String> entity = new HttpEntity<>(null, headers);
      ResponseEntity<String> result = restTemplate.postForEntity(uri, entity, String.class);

      return result.getBody() != null && result.getBody().contains(ERROR + "0" + END_ERROR);
    } catch (final Exception me) {
      log.error("Failed to perform trackback ping", me);
      return false;
    }
  }

  public void sendForBlog(String trackbackurl, int entryId, String username, String journalName, String subject, String body) {
    if (StringUtils.isEmptyOrWhitespace(trackbackurl))
      return;

    try {
      Optional<String> html = getHtmlDocument(trackbackurl);
      if (html.isPresent()) {
        Optional<String> url = parseTrackbackUrl(html.get());
        if (url.isPresent()) {
          String permalink =
                  settings.getBaseUri() + PATH_USERS + username + PATH_ENTRY + entryId;

            send(
                    url.get(),
                    journalName,
                    permalink,
                    subject,
                    body);
            log.info("Performed trackback call on {}", url.get());
        }
      }
    } catch (final Exception e) {
      log.error("Could not save trackback on entry {}", entryId, e);
    }
  }

  public Optional<String> parseTrackbackUrl(String input) {
    Optional<String> result = parseTrackbackUrlWithRdf(input);
    if (result.isPresent()) return result;

    return parseTrackbackUrlWithLinkTag(input);
  }

  /**
   * Parse the RDF version
   *
   * @param input
   * @return
   */
  public Optional<String> parseTrackbackUrlWithRdf(String input) {
    final String pattern = "trackback:ping=\"(.*?)\"";
    final Pattern p = Pattern.compile(pattern);
    final Matcher m = p.matcher(input);
    if (m.find()) {
      return Optional.ofNullable(m.group(1));
    }
    return Optional.empty();
  }

  /** The most of a fetched page that is scanned for a trackback URL. */
  static final int MAX_HTML_LENGTH = 512 * 1024;

  /** Longest link element that is looked at; anything longer is skipped. */
  private static final int MAX_TAG_LENGTH = 2048;

  private static final Pattern REL_TRACKBACK =
      Pattern.compile("\\brel=[\"']trackback[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern HREF =
      Pattern.compile("\\bhref=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  /**
   * Find the trackback URL advertised by a link element that declares rel="trackback", with the
   * attributes in any order, such as:
   * <link rel="trackback" type="application/x-www-form-urlencoded" href="http://example.org/tb" />
   * Other link elements (stylesheets, feeds) are ignored. Each tag is cut out with a bounded scan
   * and only that slice is matched, so a large or malformed page costs linear time.
   *
   * @param input html document
   * @return the href of the first trackback link, if any
   */
  public Optional<String> parseTrackbackUrlWithLinkTag(String input) {
    final String lower = input.toLowerCase(Locale.ROOT);
    int at = lower.indexOf("<link");
    while (at >= 0) {
      final int afterName = at + "<link".length();
      final int limit = Math.min(input.length(), at + MAX_TAG_LENGTH);
      if (afterName < input.length() && !Character.isLetterOrDigit(input.charAt(afterName))) {
        int end = -1;
        for (int i = afterName; i < limit; i++) {
          if (input.charAt(i) == '>') {
            end = i;
            break;
          }
        }
        if (end > 0) {
          final String tag = input.substring(at, end);
          if (REL_TRACKBACK.matcher(tag).find()) {
            final Matcher href = HREF.matcher(tag);
            if (href.find()) {
              return Optional.of(href.group(1));
            }
          }
        }
      }
      at = lower.indexOf("<link", afterName);
    }
    return Optional.empty();
  }

  public Optional<String> getHtmlDocument(String url) {
    ResponseEntity<String> result = restTemplate.getForEntity(url, String.class);

    if (result.getStatusCode() == HttpStatus.OK) {
      final String body = result.getBody();
      if (body != null && body.length() > MAX_HTML_LENGTH) {
        return Optional.of(body.substring(0, MAX_HTML_LENGTH));
      }
      return Optional.ofNullable(body);
    }

    return Optional.empty();
  }

  public TrackbackTo save(Trackback tb) {
    if (trackbackRepository.existsByEntryIdAndUrl(tb.getEntryId(), tb.getUrl())) {
      return null;
    }

    if (tb.getEntryId() < 1) {
      throw new IllegalArgumentException("EntryId must be set before saving a Trackback");
    }

    final Date now = new Date(System.currentTimeMillis());
    tb.setDate(now);
    return trackbackRepository.save(tb).toTrackbackTo();
  }

  protected List<Trackback> findByEntry(int entryId) {
    return trackbackRepository.findByEntryIdOrderByDate(entryId);
  }

  public List<TrackbackTo> getByEntry(int entryId) {
    return findByEntry(entryId).stream().map(Trackback::toTrackbackTo).toList();
  }

  public Optional<TrackbackTo> getById(int trackbackId) {
    return trackbackRepository.findById(trackbackId).map(Trackback::toTrackbackTo);
  }

  public void deleteById(int trackbackId) {
    trackbackRepository.deleteById(trackbackId);
  }

  /**
   * A trackback response compatible with the trackback protocol.
   *
   * @param errorCode
   * @param message
   * @return
   */
  public String generateResponse(final int errorCode, final String message) {
    final StringBuilder sb = new StringBuilder();
    sb.append(XML_HEADER);
    sb.append(RESPONSE);
    sb.append(ERROR);
    sb.append(errorCode);
    sb.append(END_ERROR);
    if (!StringUtils.isEmpty(message)) {
      sb.append(MESSAGE);
      sb.append(message);
      sb.append(END_MESSAGE);
    }
    sb.append(END_RESPONSE);
    return sb.toString();
  }
}
