package com.justjournal.rss;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.User;
import com.justjournal.services.MarkdownService;
import java.util.Date;
import java.util.List;
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
}
