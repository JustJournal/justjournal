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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OpenGraphParserTests {

  private static final URI BASE = URI.create("https://example.com/posts/1");

  private final OpenGraphParser parser = new OpenGraphParser();

  private LinkPreviewData parse(final String html) {
    return parser.parse(html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, BASE);
  }

  private static String page(final String head) {
    return "<!doctype html><html><head>" + head + "</head><body><p>Body</p></body></html>";
  }

  @Test
  void readsOpenGraphTags() {
    final LinkPreviewData data =
        parse(
            page(
                "<title>Fallback</title>"
                    + "<meta property=\"og:title\" content=\"The Title\">"
                    + "<meta property=\"og:description\" content=\"The description.\">"
                    + "<meta property=\"og:site_name\" content=\"Example Site\">"
                    + "<meta property=\"og:image\" content=\"https://cdn.example.com/a.jpg\">"
                    + "<meta name=\"description\" content=\"Other description\">"));
    assertEquals("The Title", data.title());
    assertEquals("The description.", data.description());
    assertEquals("Example Site", data.siteName());
    assertEquals(URI.create("https://cdn.example.com/a.jpg"), data.image());
  }

  @Test
  void acceptsNameAttributeAndAnyCase() {
    final LinkPreviewData data =
        parse(
            page(
                "<META NAME=\"OG:Title\" CONTENT=\"By name\">"
                    + "<meta name=\"og:site_name\" content=\"Site\">"));
    assertEquals("By name", data.title());
    assertEquals("Site", data.siteName());
  }

  @Test
  void fallsBackToTitleAndDescriptionMeta() {
    final LinkPreviewData data =
        parse(
            page(
                "<title>  Plain   title </title>"
                    + "<meta name=\"description\" content=\"Plain description\">"));
    assertEquals("Plain title", data.title());
    assertEquals("Plain description", data.description());
    assertNull(data.siteName());
    assertNull(data.image());
  }

  @Test
  void blankOpenGraphValuesFallBack() {
    final LinkPreviewData data =
        parse(
            page(
                "<title>Real</title><meta property=\"og:title\" content=\"   \">"
                    + "<meta property=\"og:description\" content=\"\">"
                    + "<meta name=\"description\" content=\"Real description\">"));
    assertEquals("Real", data.title());
    assertEquals("Real description", data.description());
  }

  @Test
  void firstTagWins() {
    final LinkPreviewData data =
        parse(
            page(
                "<meta property=\"og:title\" content=\"First\">"
                    + "<meta property=\"og:title\" content=\"Second\">"));
    assertEquals("First", data.title());
  }

  @Test
  void nothingToFind() {
    final LinkPreviewData data = parse("<html><body>hello</body></html>");
    assertNull(data.title());
    assertNull(data.description());
    assertNull(data.siteName());
    assertNull(data.image());
    assertEquals(LinkPreviewData.EMPTY, parser.parse(new byte[0], null, BASE));
    assertEquals(LinkPreviewData.EMPTY, parser.parse(null, null, BASE));
  }

  @Test
  void relativeImagesResolveAgainstThePage() {
    assertEquals(
        URI.create("https://example.com/img/a.png"),
        parse(page("<meta property=\"og:image\" content=\"/img/a.png\">")).image());
    assertEquals(
        URI.create("https://example.com/posts/b.png"),
        parse(page("<meta property=\"og:image\" content=\"b.png\">")).image());
    assertEquals(
        URI.create("https://cdn.example.net/c.png"),
        parse(page("<meta property=\"og:image\" content=\"//cdn.example.net/c.png\">")).image());
  }

  @Test
  void baseElementIsHonoured() {
    assertEquals(
        URI.create("https://static.example.org/x/a.png"),
        parse(
                page(
                    "<base href=\"https://static.example.org/x/\">"
                        + "<meta property=\"og:image\" content=\"a.png\">"))
            .image());
  }

  @Test
  void imageTagPreference() {
    assertEquals(
        URI.create("https://example.com/secure.png"),
        parse(
                page(
                    "<meta property=\"og:image\" content=\"https://example.com/plain.png\">"
                        + "<meta property=\"og:image:secure_url\" content=\"https://example.com/secure.png\">"))
            .image());
    assertEquals(
        URI.create("https://example.com/url.png"),
        parse(page("<meta property=\"og:image:url\" content=\"https://example.com/url.png\">"))
            .image());
    assertEquals(
        URI.create("https://example.com/tw.png"),
        parse(page("<meta name=\"twitter:image\" content=\"https://example.com/tw.png\">")).image());
  }

  @Test
  void nonHttpImagesRejected() {
    assertNull(parse(page("<meta property=\"og:image\" content=\"javascript:alert(1)\">")).image());
    assertNull(
        parse(page("<meta property=\"og:image\" content=\"JavaScript:alert(1)\">")).image());
    assertNull(
        parse(page("<meta property=\"og:image\" content=\"data:image/png;base64,AAAA\">")).image());
    assertNull(parse(page("<meta property=\"og:image\" content=\"file:///etc/passwd\">")).image());
    assertNull(parse(page("<meta property=\"og:image\" content=\"ftp://example.com/a.png\">")).image());
  }

  @Test
  void rejectedImageFallsThroughToTheNextTag() {
    assertEquals(
        URI.create("https://example.com/ok.png"),
        parse(
                page(
                    "<meta property=\"og:image:secure_url\" content=\"javascript:alert(1)\">"
                        + "<meta property=\"og:image\" content=\"https://example.com/ok.png\">"))
            .image());
  }

  @Test
  void textIsCutToColumnSizes() {
    final LinkPreviewData data =
        parse(
            page(
                "<meta property=\"og:title\" content=\""
                    + "t".repeat(400)
                    + "\"><meta property=\"og:description\" content=\""
                    + "d".repeat(1500)
                    + "\"><meta property=\"og:site_name\" content=\""
                    + "s".repeat(250)
                    + "\">"));
    assertEquals(OpenGraphParser.MAX_TITLE_LENGTH, data.title().length());
    assertEquals(OpenGraphParser.MAX_DESCRIPTION_LENGTH, data.description().length());
    assertEquals(OpenGraphParser.MAX_SITE_NAME_LENGTH, data.siteName().length());
  }

  @Test
  void truncationNeverSplitsASurrogatePair() {
    final String title = "a".repeat(OpenGraphParser.MAX_TITLE_LENGTH - 1) + "😀 more";
    final LinkPreviewData data =
        parse(page("<meta property=\"og:title\" content=\"" + title + "\">"));
    assertEquals("a".repeat(OpenGraphParser.MAX_TITLE_LENGTH - 1), data.title());
  }

  @Test
  void whitespaceCollapsedAndUnsafeCharactersRemoved() {
    final LinkPreviewData data =
        parse(
            page(
                "<meta property=\"og:title\" content=\"Hello\n\t  &#8238;World&#7;&#x200F;\">"
                    + "<meta property=\"og:description\" content=\"&#xE000;&#x2066;\">"));
    assertEquals("Hello World", data.title());
    assertNull(data.description());
  }

  @Test
  void keepsTextOutsideAscii() {
    final LinkPreviewData data =
        parse(page("<meta property=\"og:title\" content=\"Café 日本 😀\">"));
    assertEquals("Café 日本 😀", data.title());
  }

  @Test
  void entitiesAreDecodedAndMarkupStaysText() {
    final LinkPreviewData data =
        parse(
            page(
                "<title>&lt;script&gt;alert(1)&lt;/script&gt;</title>"
                    + "<meta property=\"og:description\" content=\"Tom &amp; Jerry &quot;quoted&quot;\">"));
    assertEquals("<script>alert(1)</script>", data.title());
    assertEquals("Tom & Jerry \"quoted\"", data.description());
  }

  @Test
  void decodesWithTheHeaderCharset() {
    final byte[] html =
        page("<meta property=\"og:title\" content=\"Café\">").getBytes(StandardCharsets.ISO_8859_1);
    assertEquals("Café", parser.parse(html, StandardCharsets.ISO_8859_1, BASE).title());
  }

  @Test
  void decodesWithTheMetaCharsetWhenTheHeaderHasNone() {
    final byte[] html =
        page("<meta charset=\"windows-1252\"><meta property=\"og:title\" content=\"Café €\">")
            .getBytes(Charset.forName("windows-1252"));
    assertEquals("Café €", parser.parse(html, null, BASE).title());
  }

  @Test
  void defaultsToUtf8() {
    final byte[] html =
        page("<meta property=\"og:title\" content=\"Café\">").getBytes(StandardCharsets.UTF_8);
    assertEquals("Café", parser.parse(html, null, BASE).title());
  }

  @Test
  void malformedHtmlDoesNotThrow() {
    final LinkPreviewData data =
        parse("<html><head><title>Broken<meta property=og:title content='Unquoted'<<<>>></");
    // what jsoup makes of it doesn't matter, as long as there is an answer
    assertNotNull(data.title());

    final LinkPreviewData unclosed =
        parse("<meta property=\"og:title\" content=\"Unclosed <p><div><table><tr><td>");
    assertNull(unclosed.image());
  }

  @Test
  void randomBytesDoNotThrow() {
    final byte[] junk = new byte[64 * 1024];
    new Random(42).nextBytes(junk);
    parser.parse(junk, null, BASE);
    parser.parse(junk, StandardCharsets.UTF_8, BASE);
  }

  @Test
  void missingBaseUriLeavesRelativeImagesOut() {
    final byte[] html =
        page("<meta property=\"og:image\" content=\"/a.png\">").getBytes(StandardCharsets.UTF_8);
    assertNull(parser.parse(html, null, null).image());
  }

  @Test
  void clean_handlesNullAndBlank() {
    assertNull(OpenGraphParser.clean(null, 10));
    assertNull(OpenGraphParser.clean(" \n\t  ", 10));
    assertEquals("abc", OpenGraphParser.clean("abcdef", 3));
    assertEquals("ab", OpenGraphParser.clean("ab cd", 3));
  }
}
