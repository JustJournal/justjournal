package com.justjournal.rss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.FormatType;
import com.justjournal.model.User;
import com.justjournal.services.MarkdownService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RssTests {

  @Test
  void itemLinkPointsAtEntry() {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");

    final User user = new User();
    user.setUsername("technews");

    final Entry entry = new Entry();
    entry.setId(34230);
    entry.setUser(user);
    entry.setSubject("Subject");
    entry.setBody("Body");
    entry.setFormat(FormatType.TEXT);
    entry.setDate(new Date());

    final Rss rss = new Rss(mock(JdbcTemplate.class), settings, mock(MarkdownService.class));
    rss.populate(List.of(entry));
    final String xml = rss.toXml();

    assertTrue(
        xml.contains("<link>https://www.justjournal.com/users/technews/entry/34230</link>"), xml);
    assertTrue(
        xml.contains(
            "<guid isPermaLink=\"true\">https://www.justjournal.com/users/technews/entry/34230</guid>"),
        xml);
  }

  @Test
  void pictureItemLinkPointsAtPicturePage() {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");
    final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForList(anyString()))
        .thenReturn(
            List.of(
                Map.of(
                    "id", 42,
                    "title", "Sunset",
                    "modified", "2026-10-28 12:00:00",
                    "mimetype", "image/jpeg",
                    "imglen", 1234L)));

    final Rss rss = new Rss(jdbcTemplate, settings, mock(MarkdownService.class));
    rss.populateImageList(7, "technews");
    final String xml = rss.toXml();

    assertTrue(
        xml.contains("<link>https://www.justjournal.com/users/technews/pictures/42</link>"), xml);
  }

  private static Rss feed(final Entry entry, final FeedAttachments attachments) {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");
    final Rss rss = new Rss(mock(JdbcTemplate.class), settings, mock(MarkdownService.class));
    rss.populate(List.of(entry), attachments == null ? Map.of() : Map.of(entry.getId(), attachments));
    return rss;
  }

  private static Entry entry() {
    final User user = new User();
    user.setUsername("technews");
    final Entry entry = new Entry();
    entry.setId(7);
    entry.setUser(user);
    entry.setSubject("Subject");
    entry.setBody("Body");
    entry.setFormat(FormatType.TEXT);
    entry.setDate(new Date());
    return entry;
  }

  private static void assertWellFormed(final String xml) throws Exception {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void attachedPictureBecomesTheEnclosureAndMediaContent() throws Exception {
    final FeedAttachments attached =
        new FeedAttachments(
            "https://www.justjournal.com/EntryImage/3",
            "https://www.justjournal.com/EntryImage/3/display",
            "image/png",
            12345,
            800,
            600,
            "a cat & a dog",
            "https://example.com/a?b=1&c=2",
            "Example <page>",
            "about it",
            "https://www.justjournal.com/EntryLinkImage/9",
            600,
            315);

    final String xml = feed(entry(), attached).toXml();

    assertWellFormed(xml);
    assertTrue(xml.contains("xmlns:media=\"http://search.yahoo.com/mrss/\""), xml);
    assertTrue(
        xml.contains(
            "<enclosure url=\"https://www.justjournal.com/EntryImage/3\" length=\"12345\" type=\"image/png\" />"),
        xml);
    assertTrue(
        xml.contains(
            "<media:content url=\"https://www.justjournal.com/EntryImage/3\" type=\"image/png\" medium=\"image\" fileSize=\"12345\">"),
        xml);
    assertTrue(xml.contains("<media:description>a cat &amp; a dog</media:description>"), xml);
    assertTrue(
        xml.contains(
            "<media:thumbnail url=\"https://www.justjournal.com/EntryImage/3/display\" width=\"800\" height=\"600\" />"),
        xml);
    // the entry's own picture wins over the link preview
    assertFalse(xml.contains("EntryLinkImage"), xml);
    assertTrue(
        xml.contains(
            "<atom:link rel=\"related\" type=\"text/html\" href=\"https://example.com/a?b=1&amp;c=2\" title=\"Example &lt;page&gt;\" />"),
        xml);
    assertTrue(xml.contains("Link: Example &lt;page&gt; https://example.com/a?b=1&amp;c=2"), xml);
  }

  @Test
  void linkPreviewIsTheThumbnailWithoutAPicture() throws Exception {
    final FeedAttachments attached =
        new FeedAttachments(
            null, null, null, 0, 0, 0, null,
            "https://example.com/",
            "Example",
            "",
            "https://www.justjournal.com/EntryLinkImage/9",
            600,
            315);

    final String xml = feed(entry(), attached).toXml();

    assertWellFormed(xml);
    assertFalse(xml.contains("<enclosure"), xml);
    assertFalse(xml.contains("<media:content"), xml);
    assertTrue(
        xml.contains(
            "<media:thumbnail url=\"https://www.justjournal.com/EntryLinkImage/9\" width=\"600\" height=\"315\" />"),
        xml);
  }

  @Test
  void entriesWithoutAttachmentsGetNoMediaElements() throws Exception {
    final String xml = feed(entry(), null).toXml();

    assertWellFormed(xml);
    assertFalse(xml.contains("<enclosure"), xml);
    assertFalse(xml.contains("<media:"), xml);
    assertFalse(xml.contains("rel=\"related\""), xml);
    assertEquals(1, xml.split("<item>", -1).length - 1);
  }
}
