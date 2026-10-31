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
package com.justjournal.atom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.User;
import com.justjournal.services.MarkdownService;
import com.justjournal.utility.DateConvert;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Atom feed is built from a user's entries and rendered as XML by hand. */
class AtomFeedTests {

  private MarkdownService markdownService;
  private Settings settings;
  private AtomFeed feed;
  private User author;

  @BeforeEach
  void setUp() {
    markdownService = mock(MarkdownService.class);
    settings = mock(Settings.class);
    when(settings.getBlogBaseUrl(anyString()))
        .thenAnswer(inv -> "https://www.justjournal.com/users/" + inv.getArgument(0));
    feed = new AtomFeed(markdownService, settings);
    feed.setUserName("ada");
    feed.setTitle("Ada & Co");
    feed.setAuthorName("Ada");
    feed.setAlternateLink("https://www.justjournal.com/users/ada");
    feed.setSelfLink("https://www.justjournal.com/users/ada/atom");

    author = new User();
    author.setId(1);
    author.setUsername("ada");
  }

  private Entry entry(final int id, final FormatType format, final String body) {
    final Entry entry = new Entry();
    entry.setId(id);
    entry.setUser(author);
    entry.setSubject("Post " + id);
    entry.setBody(body);
    entry.setFormat(format);
    entry.setDate(new Date(1_700_000_000_000L + id * 60_000L));
    return entry;
  }

  @Test
  void populate_turnsEntriesIntoAtomEntries() {
    final Entry text = entry(1, FormatType.TEXT, "plain body");
    final Entry markdown = entry(2, FormatType.MARKDOWN, "*md*");
    when(markdownService.convertToText("*md*")).thenReturn("md");

    feed.populate(List.of(text, markdown));
    final String xml = feed.toXml();

    assertTrue(xml.contains("<id>urn:jj:justjournal.com:atom1:ada:1</id>"), xml);
    assertTrue(xml.contains("<id>urn:jj:justjournal.com:atom1:ada:2</id>"), xml);
    assertTrue(xml.contains("<title>Post 1</title>"), xml);
    assertTrue(
        xml.contains("href=\"https://www.justjournal.com/users/ada/entry/1\"/>"), xml);
    assertTrue(xml.contains("<published>" + DateConvert.encode3339(text.getDate()) + "</published>"), xml);
    assertTrue(xml.contains("<updated>" + DateConvert.encode3339(text.getDate()) + "</updated>"), xml);
    assertTrue(xml.contains("<content type=\"html\">plain body</content>"), xml);
    assertTrue(xml.contains("<content type=\"html\">md</content>"), xml);
    assertFalse(xml.contains("<summary>"), xml);
  }

  @Test
  void populate_keepsAtMostFifteenEntries() {
    final List<Entry> entries = new ArrayList<>();
    for (int i = 1; i <= 20; i++) entries.add(entry(i, FormatType.TEXT, "b"));

    feed.populate(entries);
    final String xml = feed.toXml();

    assertEquals(15, xml.split("<entry>", -1).length - 1);
    assertTrue(xml.contains("atom1:ada:15</id>"));
    assertFalse(xml.contains("atom1:ada:16</id>"));
  }

  @Test
  void populate_swallowsBrokenEntries() {
    final Entry broken = entry(1, FormatType.TEXT, "b");
    broken.setUser(null);

    feed.populate(List.of(broken));

    assertEquals(0, feed.toXml().split("<entry>", -1).length - 1);
  }

  @Test
  void toXml_writesTheFeedHeaderAndEscapesText() {
    final String xml = feed.toXml();

    assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"), xml);
    assertTrue(xml.contains("<feed xmlns=\"http://www.w3.org/2005/Atom\""), xml);
    assertTrue(xml.contains("<id>urn:jj:justjournal.com:atom1:ada</id>"), xml);
    assertTrue(xml.contains("<title>Ada &amp; Co</title>"), xml);
    assertTrue(xml.contains("<name>Ada</name>"), xml);
    assertTrue(
        xml.contains(
            "<link rel=\"alternate\" type=\"text/html\" href=\"https://www.justjournal.com/users/ada\"/>"),
        xml);
    assertTrue(
        xml.contains("<link rel=\"self\" href=\"https://www.justjournal.com/users/ada/atom\"/>"), xml);
    assertTrue(xml.contains("<generator uri=\"https://github.com/laffer1/justjournal\""), xml);
    assertTrue(xml.contains("<updated>20"), xml);
    assertTrue(xml.endsWith("\t</feed>\n"), xml);
  }

  @Test
  void toXml_includesASummaryWhenOneIsSet() {
    final AtomEntry item = new AtomEntry();
    item.setId("urn:x:1");
    item.setTitle("t");
    item.setLink("https://example.com/1");
    item.setPublished("2024-01-01T00:00:00.000Z");
    item.setUpdated("2024-01-01T00:00:00.000Z");
    item.setSummary("short");
    feed.add(item);

    final String xml = feed.toXml();

    assertTrue(xml.contains("<summary>short</summary>"), xml);
    assertFalse(xml.contains("<content"), xml);
  }

  @Test
  void feedProperties_roundTrip() {
    feed.setId("feed-id");
    feed.setUpdated("now");

    assertEquals("feed-id", feed.getId());
    assertEquals("now", feed.getUpdated());
    assertEquals("ada", feed.getUserName());
    assertEquals("Ada & Co", feed.getTitle());
    assertEquals("Ada", feed.getAuthorName());
    assertEquals("https://www.justjournal.com/users/ada", feed.getAlternateLink());
    assertEquals("https://www.justjournal.com/users/ada/atom", feed.getSelfLink());
  }

  @Test
  void atomEntry_equalityUsesIdTitleLinkUpdatedAndContent() {
    final AtomEntry a = new AtomEntry();
    a.setId("1");
    a.setTitle("t");
    a.setLink("l");
    a.setUpdated("u");
    a.setContent("c");
    a.setPublished("p1");
    a.setSummary("s1");
    final AtomEntry b = new AtomEntry();
    b.setId("1");
    b.setTitle("t");
    b.setLink("l");
    b.setUpdated("u");
    b.setContent("c");
    b.setPublished("p2");
    b.setSummary("s2");

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a, a);
    assertNotEquals(a, null);
    assertNotEquals(a, "1");

    b.setContent("other");
    assertNotEquals(a, b);
    b.setContent("c");
    b.setUpdated("later");
    assertNotEquals(a, b);
    b.setUpdated("u");
    b.setTitle(null);
    assertNotEquals(a, b);
    b.setTitle("t");
    b.setLink(null);
    assertNotEquals(a, b);
    b.setLink("l");
    b.setId("2");
    assertNotEquals(a, b);

    final AtomEntry bare = new AtomEntry();
    bare.setId("1");
    final AtomEntry bare2 = new AtomEntry();
    bare2.setId("1");
    assertEquals(bare, bare2);
    assertEquals(bare.hashCode(), bare2.hashCode());
    assertEquals("p1", a.getPublished());
    assertEquals("s1", a.getSummary());
  }
}
