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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HeadlineBean fetches an RSS feed and renders it as a bootstrap accordion. */
class HeadlineBeanTests {

  /** localhost resolves without a DNS lookup, so the address check passes offline. */
  private static final String URL = "http://localhost/feed.xml";

  private FeedFetcher feedFetcher;
  private HeadlineBean bean;

  @BeforeEach
  void setUp() {
    feedFetcher = mock(FeedFetcher.class);
    bean = new HeadlineBean(feedFetcher);
  }

  private void feed(final String body) throws IOException {
    when(feedFetcher.fetch(URL)).thenReturn(new FeedFetcher.Result(200, body));
  }

  private static String rss(final String channelExtras, final String items) {
    return "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Tom &amp; Jerry</title>"
        + "<link>https://example.com/</link><description>A \"quoted\" desc</description>"
        + channelExtras
        + items
        + "</channel></rss>";
  }

  @Test
  void parse_rejectsAddressesWithoutAHost() {
    assertEquals("<p>RSS Feed address is invalid</p>\n", bean.parse("not a url at all"));
    assertEquals("<p>RSS Feed address is invalid</p>\n", bean.parse("http://"));
  }

  @Test
  void parse_reportsUnavailableFeeds() throws IOException {
    when(feedFetcher.fetch(URL)).thenReturn(new FeedFetcher.Result(404, null));
    assertEquals("<p>RSS Feed is unavailable for url: http://localhost/feed.xml</p>\n", bean.parse(URL));

    when(feedFetcher.fetch(URL)).thenThrow(new IOException("timeout"));
    assertEquals("<p>RSS Feed is unavailable for url: http://localhost/feed.xml</p>\n", bean.parse(URL));
  }

  @Test
  void parse_rendersTheChannelHeaderWithImageAndDates() throws IOException {
    feed(
        rss(
            "<lastBuildDate>Mon, 01 Jan 2024 00:00:00 GMT</lastBuildDate>"
                + "<generator>Gen -- v1</generator>"
                + "<image><url>https://example.com/logo.png</url><title>Logo</title>"
                + "<link>https://example.com/home</link><width>88</width><height>31</height></image>",
            ""));

    final String html = bean.parse(URL);

    assertTrue(html.contains("<!-- Generator: Gen - - v1 -->"), html);
    assertTrue(html.contains("width:88px; height:31px; position: relative;\">"), html);
    assertTrue(html.contains("<a href=\"https://example.com/home\"><img src=\"https://example.com/logo.png\" height=\"31\" width=\"88\" alt=\"Logo\" title=\"Logo\" /></a>"), html);
    assertTrue(html.contains("<h3 title=\"A &quot;quoted&quot; desc\">Tom &amp; Jerry</h3>"), html);
    assertTrue(html.contains("<p>Updated: Mon, 01 Jan 2024 00:00:00 GMT<br /><a href=\"https://example.com/\">[https://example.com/]</a></p>"), html);
    assertTrue(html.contains("<div class=\"panel-group\" id=\"accordion\""), html);
  }

  @Test
  void parse_imageWithoutLinkOrSize_isPlain() throws IOException {
    feed(rss("<image><url>https://example.com/logo.png</url><width>bad</width></image>", ""));

    final String html = bean.parse(URL);

    assertTrue(html.contains("<span style=\"padding: 5px; float:left; position: relative;\"><img src=\"https://example.com/logo.png\" alt=\"\" title=\"\" /></span>"), html);
    assertTrue(html.contains("<p><a href=\"https://example.com/\">"), html);
  }

  @Test
  void parse_rendersItems_firstOneExpanded() throws IOException {
    feed(
        rss(
            "",
            "<item><title>First</title><link>https://example.com/1</link>"
                + "<description>&lt;b&gt;bold&lt;/b&gt; &lt;script&gt;x()&lt;/script&gt;</description>"
                + "<pubDate>Mon, 01 Jan 2024 10:00:00 GMT</pubDate><guid>g1</guid></item>"
                + "<item><title>Second</title><link>https://example.com/2</link></item>"
                + "<item><title>No link</title></item>"
                + "<item><link>https://example.com/4</link></item>"));

    final String html = bean.parse(URL);

    final String firstId = "0_" + Integer.toHexString("g1".hashCode());
    assertTrue(html.contains("id=\"heading" + firstId + "\""), html);
    assertTrue(html.contains("aria-expanded=\"true\" aria-controls=\"collapse" + firstId + "\">First</a>"), html);
    assertTrue(html.contains("class=\"panel-collapse collapsein\""), html);
    assertTrue(html.contains("<b>bold</b>"), html);
    assertFalse(html.contains("<script>"), html);
    assertTrue(html.contains("<a href=\"https://example.com/1\">Read More</a>"), html);
    assertTrue(html.contains("<span class=\"RssItemPubDate\">Mon, 01 Jan 2024 10:00:00 GMT</span>"), html);
    final String secondId = "1_" + Integer.toHexString("https://example.com/2".hashCode());
    assertTrue(html.contains("aria-expanded=\"false\" aria-controls=\"collapse" + secondId + "\">Second</a>"), html);
    assertFalse(html.contains("No link"), html);
    assertFalse(html.contains("example.com/4"), html);
  }

  @Test
  void parse_showsAtMostSixteenItems() throws IOException {
    final StringBuilder items = new StringBuilder();
    for (int i = 1; i <= 20; i++) {
      items.append("<item><title>Item ").append(i).append("</title><link>https://example.com/").append(i).append("</link></item>");
    }
    feed(rss("", items.toString()));

    final String html = bean.parse(URL);

    assertTrue(html.contains(">Item 16</a>"), html);
    assertFalse(html.contains(">Item 17</a>"), html);
  }

  @Test
  void parse_reportsBrokenAndEmptyFeeds() throws IOException {
    feed("<rss><channel><title>oops</title>");
    assertTrue(bean.parse(URL).startsWith("<p>Bad Feed "), bean.parse(URL));

    feed("");
    assertEquals("<p>Feed is empty at http://localhost/feed.xml", bean.parse(URL));

    feed("<rss version=\"2.0\"></rss>");
    assertTrue(bean.parse(URL).startsWith("<p>Error, could not process request: "), bean.parse(URL));
  }

  @Test
  void parse_refusesDoctypes() throws IOException {
    feed("<!DOCTYPE rss [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><title>&x;</title></channel></rss>");

    assertTrue(bean.parse(URL).startsWith("<p>Bad Feed "), bean.parse(URL));
  }

  @Test
  void helpers_sanitizeHtmlAndUrls() {
    assertEquals("", HeadlineBean.sanitizeHtml(null));
    assertEquals("", HeadlineBean.sanitizeHtml("  "));
    assertEquals("<p>ok</p>", HeadlineBean.sanitizeHtml("<p onclick=\"x()\">ok</p>").trim());
    assertEquals(null, HeadlineBean.safeUrl(null));
    assertEquals(null, HeadlineBean.safeUrl("javascript:alert(1)"));
    assertEquals("https://example.com/?a=1&amp;b=2", HeadlineBean.safeUrl(" https://example.com/?a=1&b=2 "));
  }
}
