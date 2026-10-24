package com.justjournal.rss;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.justjournal.model.RssCache;
import com.justjournal.repository.RssCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CachedHeadlineBeanTests {

  private static final String URI = "https://example.com/feed.xml";

  @Mock private FeedFetcher feedFetcher;

  @Mock private RssCacheRepository rssCacheRepository;

  @InjectMocks private CachedHeadlineBean cachedHeadlineBean;

  @Test
  void parse_rendersCachedFeed_withoutGoingToTheNetwork() {
    final RssCache cache = new RssCache();
    cache.setUri(URI);
    cache.setContent(
        "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Example Feed</title>"
            + "<link>https://example.com/</link><description>desc</description>"
            + "<item><title>First post</title><link>https://example.com/1</link>"
            + "<description>hello</description><guid>https://example.com/1</guid></item>"
            + "</channel></rss>");
    when(rssCacheRepository.findFirstByUriOrderByIdAsc(URI)).thenReturn(cache);

    final String html = cachedHeadlineBean.parse(URI);

    assertTrue(html.contains("Example Feed"), html);
    assertTrue(html.contains("First post"), html);
    verifyNoInteractions(feedFetcher);
    verify(rssCacheRepository, never()).save(any());
  }

  @Test
  void parse_queuesUnknownFeedForBackgroundRefresh() {
    when(rssCacheRepository.findFirstByUriOrderByIdAsc(URI)).thenReturn(null);

    final String html = cachedHeadlineBean.parse(URI);

    assertTrue(html.contains("hasn't been downloaded yet"), html);
    final ArgumentCaptor<RssCache> saved = ArgumentCaptor.forClass(RssCache.class);
    verify(rssCacheRepository).save(saved.capture());
    assertEquals(URI, saved.getValue().getUri());
    assertEquals(CachedHeadlineBean.NEVER_UPDATED, saved.getValue().getLastUpdated());
    assertTrue(saved.getValue().getActive());
    assertEquals("", saved.getValue().getContent());
    verifyNoInteractions(feedFetcher);
  }

  @Test
  void parse_showsUnavailable_whenCachedContentIsEmpty() {
    final RssCache cache = new RssCache();
    cache.setUri(URI);
    cache.setContent("");
    when(rssCacheRepository.findFirstByUriOrderByIdAsc(URI)).thenReturn(cache);

    assertTrue(cachedHeadlineBean.parse(URI).contains("currently unavailable"));
    verifyNoInteractions(feedFetcher);
  }

  @Test
  void parse_rejectsNonHttpAddress_withoutDnsLookup() {
    assertTrue(cachedHeadlineBean.parse("file:///etc/passwd").contains("address is invalid"));
    verifyNoInteractions(feedFetcher, rssCacheRepository);
  }

  private void cached(final String content) {
    final RssCache cache = new RssCache();
    cache.setUri(URI);
    cache.setContent(content);
    when(rssCacheRepository.findFirstByUriOrderByIdAsc(URI)).thenReturn(cache);
  }

  @Test
  void parse_rendersItemsWithoutGuid() {
    cached(
        "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>No Guids</title>"
            + "<link>https://example.com/</link>"
            + "<item><title>One</title><link>https://example.com/1</link>"
            + "<description>first</description></item>"
            + "<item><title>Two</title><link>https://example.com/2</link>"
            + "<description>second</description><guid/></item></channel></rss>");

    final String html = cachedHeadlineBean.parse(URI);

    assertFalse(html.contains("could not process request"), html);
    assertTrue(html.contains("One") && html.contains("Two"), html);
    final String id1 = "0_" + Integer.toHexString("https://example.com/1".hashCode());
    final String id2 = "1_" + Integer.toHexString("https://example.com/2".hashCode());
    assertTrue(html.contains("id=\"heading" + id1 + "\""), html);
    assertTrue(html.contains("id=\"collapse" + id2 + "\""), html);
  }

  @Test
  void parse_rendersItemWithoutDescription() {
    cached(
        "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>T</title>"
            + "<item><title>Bare</title><link>https://example.com/b</link></item>"
            + "</channel></rss>");

    final String html = cachedHeadlineBean.parse(URI);

    assertFalse(html.contains("could not process request"), html);
    assertTrue(html.contains("Bare"), html);
  }

  @Test
  void parse_escapesFeedUrlInErrorMessages() {
    // passes the URI syntax check, but would break out of an HTML attribute if left raw
    final String odd = "https://example.com/rss?a=1&b='onmouseover=alert(1)";
    final RssCache cache = new RssCache();
    cache.setUri(odd);
    cache.setContent("<rss></rss>"); // no <channel> -> render error path
    when(rssCacheRepository.findFirstByUriOrderByIdAsc(odd)).thenReturn(cache);

    final String html = cachedHeadlineBean.parse(odd);

    assertTrue(html.contains("could not process request"), html);
    assertFalse(html.contains("'onmouseover"), html);
    assertTrue(html.contains("a=1&amp;b=&#39;onmouseover"), html);
  }

  @Test
  void unavailableMessage_fromBaseBeanEscapesUrl() {
    final String html = new HeadlineBean(feedFetcher).unavailableMessage("https://e.com/<b>");
    assertTrue(html.contains("https://e.com/&lt;b&gt;"), html);
  }

  @Test
  void parse_neutralizesHostileFeedContent() {
    cached(
        "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>"
            + "<title><![CDATA[<script>alert('title')</script>]]></title>"
            + "<link>javascript:alert('channel')</link>"
            + "<description><![CDATA[\" onmouseover=\"alert(1)]]></description>"
            + "<generator><![CDATA[--><script>alert('gen')</script>]]></generator>"
            + "<lastBuildDate><![CDATA[<img src=x onerror=alert(1)>]]></lastBuildDate>"
            + "<image><url>javascript:alert('img')</url><width>100px</width>"
            + "<title>logo</title><link>https://example.com/</link></image>"
            + "<item><title>Hostile</title><link>javascript:alert('item')</link>"
            + "<guid>g1</guid><pubDate><![CDATA[<script>alert('date')</script>]]></pubDate>"
            + "<description><![CDATA[<p>Hello <b>bold</b> <a href=\"https://example.com/ok\">ok</a>"
            + " <a href=\"javascript:alert('desc')\">bad</a></p>"
            + "<script>alert('desc')</script><img src=x onerror=alert(1)>"
            + "<iframe src=\"https://evil.example/\"></iframe>"
            + "<div onclick=\"alert(1)\">click</div>]]></description></item>"
            + "</channel></rss>");

    final String html = cachedHeadlineBean.parse(URI);
    final String lower = html.toLowerCase();

    assertFalse(html.contains("could not process request"), html);
    // nothing executable survives as markup (escaped text like "&lt;script&gt;" is fine)
    assertFalse(lower.contains("<script"), html);
    assertFalse(lower.contains("href=\"javascript:"), html);
    assertFalse(lower.contains("src=\"javascript:"), html);
    assertFalse(lower.contains("<img src=x"), html);
    assertFalse(lower.contains(" onclick="), html);
    assertFalse(lower.contains("<iframe"), html);
    assertFalse(html.contains("title=\"\" onmouseover"), html);
    assertFalse(html.contains("--><"), html);
    assertFalse(lower.contains("<!doctype"), html);
    assertFalse(lower.contains("dtd\">"), html);

    // safe formatting, safe links and the item itself survive
    assertTrue(html.contains("<b>bold</b>"), html);
    assertTrue(html.contains("href=\"https://example.com/ok\""), html);
    assertTrue(html.contains("Hostile"), html);
    assertTrue(html.contains("&lt;script&gt;alert(&#39;title&#39;)"), html);
  }

  @Test
  void safeUrl_allowsOnlyHttpAndHttps() {
    assertEquals("https://example.com/a?b=1&amp;c=2", HeadlineBean.safeUrl(" https://example.com/a?b=1&c=2 "));
    assertEquals("HTTP://EXAMPLE.COM/", HeadlineBean.safeUrl("HTTP://EXAMPLE.COM/"));
    assertNull(HeadlineBean.safeUrl("javascript:alert(1)"));
    assertNull(HeadlineBean.safeUrl("JaVaScRiPt:alert(1)"));
    assertNull(HeadlineBean.safeUrl("data:text/html,<script>"));
    assertNull(HeadlineBean.safeUrl("//evil.example/"));
    assertNull(HeadlineBean.safeUrl(null));
  }

  @Test
  void parse_keepsHttpImages_andDropsUnsafeOnes() {
    cached(
        "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Pics</title>"
            + "<item><title>Photos</title><link>https://example.com/p</link><guid>p1</guid>"
            + "<description><![CDATA["
            + "<img src=\"https://example.com/ok.png\" alt=\"ok pic\" width=\"120\" height=\"80\""
            + " onerror=\"alert(1)\" />"
            + "<img src=\"http://example.com/plain.jpg\" />"
            + "<img src=\"/api/logout\" />"
            + "<img src=\"//evil.example/x.png\" />"
            + "<img src=\"javascript:alert(1)\" />"
            + "<img src=\"data:image/svg+xml;base64,PHN2Zz4=\" />"
            + "]]></description></item></channel></rss>");

    final String html = cachedHeadlineBean.parse(URI);
    final String lower = html.toLowerCase();

    assertTrue(html.contains("src=\"https://example.com/ok.png\""), html);
    assertTrue(html.contains("alt=\"ok pic\""), html);
    assertTrue(html.contains("width=\"120\""), html);
    assertTrue(html.contains("src=\"http://example.com/plain.jpg\""), html);
    assertFalse(lower.contains("onerror"), html);
    assertFalse(html.contains("/api/logout"), html);
    assertFalse(html.contains("evil.example"), html);
    assertFalse(lower.contains("javascript:"), html);
    assertFalse(lower.contains("data:image"), html);
  }
}
