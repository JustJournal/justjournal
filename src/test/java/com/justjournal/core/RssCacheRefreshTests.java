package com.justjournal.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.justjournal.model.RssCache;
import com.justjournal.repository.RssCacheRepository;
import com.justjournal.rss.FeedFetcher;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RssCacheRefreshTests {

  private static final String URI = "https://example.com/feed.xml";

  @Mock private RssCacheRepository rssCacheRepository;

  @Mock private FeedFetcher feedFetcher;

  @InjectMocks private RssCacheRefresh rssCacheRefresh;

  private RssCache cache;

  @BeforeEach
  void setUp() {
    cache = new RssCache();
    cache.setUri(URI);
    cache.setActive(true);
    cache.setContent("");
    cache.setLastUpdated(new Date(0));
  }

  @Test
  void run_fetchesEveryDueFeed() throws IOException {
    final RssCache second = new RssCache();
    second.setUri("https://example.org/rss");
    second.setActive(true);
    when(rssCacheRepository.findDueForRefresh(any())).thenReturn(List.of(cache, second));
    when(feedFetcher.fetch(anyString())).thenReturn(new FeedFetcher.Result(200, "<rss></rss>"));
    when(rssCacheRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    rssCacheRefresh.run();

    verify(feedFetcher).fetch(URI);
    verify(feedFetcher).fetch("https://example.org/rss");
    verify(rssCacheRepository, times(2)).saveAndFlush(any());
  }

  @Test
  void refresh_storesContentAndClearsErrors() throws IOException {
    cache.setErrorCount(3);
    when(feedFetcher.fetch(URI)).thenReturn(new FeedFetcher.Result(200, "  <rss><channel/></rss>"));
    when(rssCacheRepository.saveAndFlush(cache)).thenReturn(cache);

    rssCacheRefresh.refresh(cache);

    assertEquals("<?xml version=\"1.0\"?>\n<rss><channel/></rss>", cache.getContent());
    assertEquals(0, cache.getErrorCount());
    assertTrue(cache.getLastUpdated().after(new Date(0)));
  }

  @Test
  void refresh_disablesGoneFeed() throws IOException {
    when(feedFetcher.fetch(URI)).thenReturn(new FeedFetcher.Result(410, null));
    when(rssCacheRepository.saveAndFlush(cache)).thenReturn(cache);

    rssCacheRefresh.refresh(cache);

    assertFalse(cache.getActive());
  }

  @Test
  void refresh_countsTimeout_andWaitsADayBeforeRetrying() throws IOException {
    when(feedFetcher.fetch(URI)).thenThrow(new SocketTimeoutException("Read timed out"));
    when(rssCacheRepository.saveAndFlush(cache)).thenReturn(cache);

    rssCacheRefresh.refresh(cache);

    assertEquals(1, cache.getErrorCount());
    assertTrue(cache.getActive());
    assertTrue(cache.getLastUpdated().after(new Date(0)));
  }

  @Test
  void refresh_disablesFeedAfterRepeatedFailures() throws IOException {
    cache.setErrorCount(RssCacheRefresh.MAX_ERROR_FETCH);
    when(feedFetcher.fetch(URI)).thenReturn(new FeedFetcher.Result(500, null));
    when(rssCacheRepository.saveAndFlush(cache)).thenReturn(cache);

    rssCacheRefresh.refresh(cache);

    assertFalse(cache.getActive());
  }

  @Test
  void refresh_treatsHtmlPageAsFailure() throws IOException {
    when(feedFetcher.fetch(URI))
        .thenReturn(new FeedFetcher.Result(200, "<!doctype html><html></html>"));
    when(rssCacheRepository.saveAndFlush(cache)).thenReturn(cache);

    rssCacheRefresh.refresh(cache);

    assertEquals("", cache.getContent());
    assertEquals(1, cache.getErrorCount());
  }

  @Test
  void refresh_storesApostrophesUnchanged() throws IOException {
    when(feedFetcher.fetch(URI))
        .thenReturn(new FeedFetcher.Result(200, "<rss><title>Luke's blog</title></rss>"));
    when(rssCacheRepository.saveAndFlush(cache)).thenReturn(cache);

    rssCacheRefresh.refresh(cache);

    assertTrue(cache.getContent().contains("Luke's blog"), cache.getContent());
    assertFalse(cache.getContent().contains("\\'"), cache.getContent());
  }
}
