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
package com.justjournal.ctl;

import static com.justjournal.core.Constants.HEADER_CACHE_CONTROL;
import static com.justjournal.core.Constants.HEADER_EXPIRES;
import static com.justjournal.core.Constants.HEADER_LAST_MODIFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.rss.Rss;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/** The recent blogs feed is served from the cache when possible and rebuilt otherwise. */
class RecentBlogsControllerTests {

  private EntryRepository entryRepository;
  private Settings settings;
  private Rss rss;
  private RecentBlogsRepository recentBlogsRepository;
  private RecentBlogsController controller;

  @BeforeEach
  void setUp() {
    entryRepository = mock(EntryRepository.class);
    settings = mock(Settings.class);
    rss = mock(Rss.class);
    recentBlogsRepository = mock(RecentBlogsRepository.class);
    controller = new RecentBlogsController(entryRepository, settings, rss, recentBlogsRepository);

    when(settings.getBaseUri()).thenReturn("https://www.example.com/");
    when(settings.getSiteAdmin()).thenReturn("Ada");
    when(settings.getSiteAdminEmail()).thenReturn("ada@example.com");
    when(recentBlogsRepository.setBlogs(anyString())).thenReturn(Mono.just(true));
  }

  private static Entry entry(final int id, final String username, final Date date) {
    final User user = new User();
    user.setId(id);
    user.setUsername(username);
    final Entry entry = new Entry();
    entry.setId(id);
    entry.setUser(user);
    entry.setDate(date);
    entry.setSecurity(Security.PUBLIC);
    return entry;
  }

  @Test
  void get_servesTheCachedFeedWhenThereIsOne() {
    when(recentBlogsRepository.getBlogs()).thenReturn(Mono.just("<rss>cached</rss>"));

    final ResponseEntity<String> result = controller.get();

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("<rss>cached</rss>", result.getBody());
    assertEquals("max-age=60, private, proxy-revalidate", result.getHeaders().getFirst(HEADER_CACHE_CONTROL));
    assertNotNull(result.getHeaders().getFirst(HEADER_EXPIRES));
    verify(entryRepository, never()).findBySecurityOrderByDateDesc(any(), any(Pageable.class));
  }

  @Test
  void get_buildsTheFeedFromOneEntryPerAuthor_andCachesIt() {
    when(recentBlogsRepository.getBlogs()).thenReturn(Mono.empty());
    final Date newest = new Date(1_700_000_000_000L);
    final List<Entry> entries =
        List.of(
            entry(1, "ada", newest),
            entry(2, "ada", new Date(1_600_000_000_000L)),
            entry(3, "bob", new Date(1_500_000_000_000L)));
    when(entryRepository.findBySecurityOrderByDateDesc(eq(Security.PUBLIC), eq(PageRequest.of(1, 20))))
        .thenReturn(new PageImpl<>(entries));
    when(rss.getNewestEntryDate()).thenReturn(newest);
    when(rss.toXml()).thenReturn("<rss>fresh</rss>");

    final ResponseEntity<String> result = controller.get();

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("<rss>fresh</rss>", result.getBody());
    assertEquals(String.valueOf(newest.getTime()), result.getHeaders().getFirst(HEADER_LAST_MODIFIED));
    verify(rss).setTitle("JJ New Posts");
    verify(rss).setLink("https://www.example.com/");
    verify(rss).setDescription("New blog posts on Just Journal");
    verify(rss).setLanguage("en-us");
    verify(rss)
        .setCopyright(
            "Copyright "
                + Calendar.getInstance().get(Calendar.YEAR)
                + " JustJournal.com and its blog account owners.");
    verify(rss).setWebMaster("ada@example.com (Ada)");
    verify(rss).setManagingEditor("ada@example.com (Ada)");
    verify(rss).setSelfLink("https://www.example.com/RecentBlogs");
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Collection<Entry>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(rss).populate(captor.capture());
    final List<Integer> ids = new ArrayList<>();
    captor.getValue().forEach(e -> ids.add(e.getId()));
    assertEquals(2, ids.size());
    assertTrue(ids.contains(1), ids.toString());
    assertTrue(ids.contains(3), ids.toString());
    verify(recentBlogsRepository).setBlogs("<rss>fresh</rss>");
  }

  @Test
  void get_capsTheFeedAtFifteenAuthors() {
    when(recentBlogsRepository.getBlogs()).thenReturn(Mono.empty());
    final List<Entry> entries = new ArrayList<>();
    for (int i = 1; i <= 20; i++) entries.add(entry(i, "user" + i, new Date()));
    when(entryRepository.findBySecurityOrderByDateDesc(any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(entries));
    when(rss.getNewestEntryDate()).thenReturn(null);
    when(rss.toXml()).thenReturn("<rss/>");

    final ResponseEntity<String> result = controller.get();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Collection<Entry>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(rss).populate(captor.capture());
    assertEquals(15, captor.getValue().size());
    assertNotNull(result.getHeaders().getFirst(HEADER_LAST_MODIFIED));
  }

  @Test
  void get_reportsAFailureToBuildTheFeed() {
    when(recentBlogsRepository.getBlogs()).thenReturn(Mono.empty());
    when(entryRepository.findBySecurityOrderByDateDesc(any(), any(Pageable.class)))
        .thenThrow(new IllegalStateException("db"));

    final ResponseEntity<String> result = controller.get();

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    assertEquals("Could not generate recent blogs", result.getBody());
    verify(recentBlogsRepository, never()).setBlogs(anyString());
  }
}
