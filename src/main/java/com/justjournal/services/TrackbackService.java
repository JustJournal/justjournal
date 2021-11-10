/*
Copyright (c) 2003-2021, Lucas Holt
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

  Redistributions of source code must retain the above copyright notice, this list of
  conditions and the following disclaimer.

  Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or other
  materials provided with the distribution.

  Neither the name of the Just Journal nor the names of its contributors
  may be used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/
package com.justjournal.services;


import com.justjournal.model.Trackback;
import com.justjournal.model.api.TrackbackTo;
import com.justjournal.repository.TrackbackRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.esapi.ESAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.util.StringUtils;

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

  @Autowired private RestTemplate restTemplate;

  @Autowired
  public TrackbackService(final TrackbackRepository trackbackRepository) {
    this.trackbackRepository = trackbackRepository;
  }

  public boolean send(
      String pingUrl, String blogName, String permalink, String title, String excerpt) {
    try {
      final String cleanTitle = ESAPI.encoder().encodeForURL(title);
      final String cleanPermanentBlogEntryUrl = ESAPI.encoder().encodeForURL(permalink);
      final String cleanBlogName = ESAPI.encoder().encodeForURL(blogName);
      final String cleanExcerpt = ESAPI.encoder().encodeForURL(excerpt);

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
    final String pattern = "(((trackback:ping=[\\\"\\'])((https?)://(.*?)))[\\'\\\"\\s\\r\\n|,|$])";

    final Pattern p = Pattern.compile(pattern);
    final Matcher m = p.matcher(input);
    if (m.groupCount() < 4) return Optional.empty();
    return Optional.ofNullable(m.group(4));
  }

  /**
   * Works on one line but not mulitline like this: <link rel="trackback"
   * type="application/x-www-form-urlencoded" href="http://example.org/trackback-url" />
   *
   * @param input
   * @return
   */
  public Optional<String> parseTrackbackUrlWithLinkTag(String input) {
    final String pattern =
        "<\\s*link[\\s\\r"
            + "\\n"
            + "](rel=\"trackback\"|[\\s\\n"
            + "\\r"
            + "]?type=\"application\\/x-www-form-urlencoded\"|[^>](.*?)([\\s\\n"
            + "\\r"
            + "]?href=\"(.*?)\"))[\\s\\r"
            + "\\n"
            + "]*\\/?>";

    final Pattern p = Pattern.compile(pattern);
    final Matcher m = p.matcher(input);
    if (m.groupCount() < 4) return Optional.empty();
    return Optional.ofNullable(m.group(4));
  }

  public Optional<String> getHtmlDocument(String url) {
    ResponseEntity<String> result = restTemplate.getForEntity(url, String.class);

    if (result.getStatusCode() == HttpStatus.OK) {
      return Optional.ofNullable(result.getBody());
    }

    return Optional.empty();
  }

  public TrackbackTo save(Trackback tb) {
    if (trackbackRepository.existsByEntryIdAndUrl(tb.getEntryId(), tb.getUrl())) {
      return null;
    }

    final java.sql.Date now = new java.sql.Date(System.currentTimeMillis());
    tb.setDate(now);
    return trackbackRepository.save(tb).toTrackbackTo();
  }

  protected List<Trackback> findByEntry(int entryId) {
    return trackbackRepository.findByEntryIdOrderByDate(entryId);
  }

  public List<TrackbackTo> getByEntry(int entryId) {
    return findByEntry(entryId).stream().map(Trackback::toTrackbackTo).collect(Collectors.toList());
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
