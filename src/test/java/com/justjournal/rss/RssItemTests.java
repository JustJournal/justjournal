package com.justjournal.rss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RssItemTests {

  @Test
  void setTitle_long_truncated() {
    final RssItem item = new RssItem();
    item.setTitle("x".repeat(150));
    assertEquals(99, item.getTitle().length());
  }

  @Test
  void setTitle_emojiAtCut_notSplit() {
    final RssItem item = new RssItem();
    // the emoji's two UTF-16 chars sit at index 98 and 99, across the cut
    item.setTitle("x".repeat(98) + "😀" + "y".repeat(10));

    assertEquals("x".repeat(98), item.getTitle());
    assertFalse(Character.isHighSurrogate(item.getTitle().charAt(item.getTitle().length() - 1)));
  }

  @Test
  void setDescription_emojiBeforeCut_kept() {
    final RssItem item = new RssItem();
    item.setDescription("x".repeat(494) + "😀" + "y".repeat(10));

    assertTrue(item.getDescription().endsWith("😀"), item.getDescription());
    assertEquals(496, item.getDescription().length());
  }
}
