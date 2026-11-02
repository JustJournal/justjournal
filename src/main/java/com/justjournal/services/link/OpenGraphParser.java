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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads the Open Graph tags ({@code og:title}, {@code og:description}, {@code og:site_name},
 * {@code og:image}) from a page that {@link SafeHttpFetcher} downloaded, falling back to the
 * page's {@code <title>} and description meta tag.
 *
 * <p>Only bytes we fetched ourselves are parsed; jsoup never makes a request of its own. The
 * results are plain text with white space collapsed and control, bidi and private use characters
 * removed, cut to fit the database columns.
 */
@Slf4j
@Component
public class OpenGraphParser {

  /** Matches the entry_link.title column size. */
  public static final int MAX_TITLE_LENGTH = 300;
  /** Matches the entry_link.description column size. */
  public static final int MAX_DESCRIPTION_LENGTH = 1000;
  /** Matches the entry_link.site_name column size. */
  public static final int MAX_SITE_NAME_LENGTH = 200;

  private static final int MAX_IMAGE_URL_LENGTH = 2048;

  /** Image tags, best first. */
  private static final List<String> IMAGE_TAGS =
      List.of("og:image:secure_url", "og:image", "og:image:url", "twitter:image");

  private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+");

  /**
   * Characters that have no business in a title: controls, lone surrogates, private use, and the
   * bidi controls that can make text read differently from what it is. Unlike {@code
   * StringUtil.stripNonPrintableCharacters}, this keeps letters outside ASCII.
   */
  private static final Pattern UNSAFE =
      Pattern.compile("[\\p{Cc}\\p{Cs}\\p{Co}\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");

  /**
   * Parse a page.
   *
   * @param html the page as downloaded
   * @param hint charset from the Content-Type header, or null to go by the page itself
   * @param baseUri where the page was fetched from, for resolving a relative image address
   * @return what the page says; fields it doesn't have are null
   */
  public LinkPreviewData parse(final byte[] html, final Charset hint, final URI baseUri) {
    if (html == null || html.length == 0) return LinkPreviewData.EMPTY;

    final Document doc;
    try {
      doc =
          Jsoup.parse(
              new ByteArrayInputStream(html),
              hint == null ? null : hint.name(),
              baseUri == null ? "" : baseUri.toString());
    } catch (final IOException | RuntimeException e) {
      log.debug("Could not parse page for preview: {}", e.getClass().getSimpleName());
      return LinkPreviewData.EMPTY;
    }

    final Map<String, Element> meta = new HashMap<>();
    for (final Element element : doc.select("meta[content]")) {
      if (element.attr("content").isBlank()) continue;
      for (final String attribute : List.of("property", "name")) {
        final String key = element.attr(attribute).strip().toLowerCase(Locale.ROOT);
        if (!key.isEmpty()) meta.putIfAbsent(key, element);
      }
    }

    String title = clean(content(meta, "og:title"), MAX_TITLE_LENGTH);
    if (title == null) title = clean(doc.title(), MAX_TITLE_LENGTH);
    String description = clean(content(meta, "og:description"), MAX_DESCRIPTION_LENGTH);
    if (description == null) description = clean(content(meta, "description"), MAX_DESCRIPTION_LENGTH);
    final String siteName = clean(content(meta, "og:site_name"), MAX_SITE_NAME_LENGTH);

    return new LinkPreviewData(title, description, siteName, image(meta));
  }

  private static String content(final Map<String, Element> meta, final String key) {
    final Element element = meta.get(key);
    return element == null ? null : element.attr("content");
  }

  /** The first image tag that holds an absolute http(s) address, once resolved. */
  private static URI image(final Map<String, Element> meta) {
    for (final String tag : IMAGE_TAGS) {
      final Element element = meta.get(tag);
      if (element == null) continue;
      final String address = element.absUrl("content");
      if (address.isEmpty() || address.length() > MAX_IMAGE_URL_LENGTH) continue;
      try {
        final URI uri = new URI(address);
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) return uri;
      } catch (final URISyntaxException e) {
        // try the next tag
      }
    }
    return null;
  }

  /** Plain single line text, or null if nothing is left. */
  static String clean(final String text, final int maxLength) {
    if (text == null) return null;
    String result = SPACE.matcher(text).replaceAll(" ");
    result = UNSAFE.matcher(result).replaceAll("");
    result = SPACE.matcher(result).replaceAll(" ").strip();
    if (result.isEmpty()) return null;
    if (result.length() > maxLength) {
      int end = maxLength;
      if (Character.isHighSurrogate(result.charAt(end - 1))) end--;
      result = result.substring(0, end).strip();
    }
    return result;
  }
}
