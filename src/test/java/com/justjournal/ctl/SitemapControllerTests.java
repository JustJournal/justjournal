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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.Journal;
import com.justjournal.model.Url;
import com.justjournal.model.UrlSet;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The sitemap lists the site pages and every journal that may be crawled. */
class SitemapControllerTests {

  private static final String BASE = "https://www.example.com/";

  private Settings settings;
  private UserRepository userRepository;
  private SitemapController controller;

  @BeforeEach
  void setUp() {
    settings = mock(Settings.class);
    userRepository = mock(UserRepository.class);
    when(settings.getBaseUri()).thenReturn(BASE);
    when(settings.getBlogBaseUrl(anyString()))
        .thenAnswer(inv -> BASE + "users/" + inv.getArgument(0));
    controller = new SitemapController(settings, userRepository);
  }

  private static User user(final String username, final boolean spider, final boolean ownerOnly) {
    final User user = new User();
    user.setUsername(username);
    final Journal journal = new Journal();
    journal.setUser(user);
    journal.setAllowSpider(spider);
    journal.setOwnerViewOnly(ownerOnly);
    user.getJournals().add(journal);
    return user;
  }

  private static Map<String, Url> byLocation(final UrlSet set) {
    return set.getUrl().stream().collect(Collectors.toMap(Url::getLoc, Function.identity(), (a, b) -> a));
  }

  @Test
  void get_listsTheSitePages() {
    when(userRepository.findAll()).thenReturn(List.of());

    final UrlSet set = controller.get();
    final Map<String, Url> urls = byLocation(set);

    assertEquals(11, set.getUrl().size());
    assertEquals(Url.Priority.HIGH.getValue(), urls.get(BASE).getPriority());
    assertEquals(Url.ChangeFreqency.MONTHLY.getValue(), urls.get(BASE).getChangefreq());
    assertEquals(Url.ChangeFreqency.DAILY.getValue(), urls.get(BASE + "RecentBlogs").getChangefreq());
    assertEquals(Url.ChangeFreqency.DAILY.getValue(), urls.get(BASE + "members").getChangefreq());
    assertEquals(Url.Priority.LOW.getValue(), urls.get(BASE + "support/api").getPriority());
    assertEquals(Url.ChangeFreqency.YEARLY.getValue(), urls.get(BASE + "moodlist").getChangefreq());
    assertTrue(urls.get(BASE).getLastmod().startsWith("20"));
  }

  @Test
  void get_addsEveryCrawlableJournal_andSkipsTheRest() {
    when(userRepository.findAll())
        .thenReturn(
            List.of(
                user("ada", true, false), user("hidden", false, false), user("owner", true, true)));

    final UrlSet set = controller.get();
    final Map<String, Url> urls = byLocation(set);

    assertEquals(11 + 8, set.getUrl().size());
    final String ada = BASE + "users/ada";
    assertEquals(Url.Priority.HIGH.getValue(), urls.get(ada).getPriority());
    assertEquals(Url.ChangeFreqency.DAILY.getValue(), urls.get(ada).getChangefreq());
    assertEquals(Url.Priority.MEDIUMLOW.getValue(), urls.get(ada + "/calendar").getPriority());
    assertEquals(Url.ChangeFreqency.MONTHLY.getValue(), urls.get(ada + "/calendar").getChangefreq());
    assertEquals(Url.ChangeFreqency.DAILY.getValue(), urls.get(ada + "/friends").getChangefreq());
    assertEquals(Url.ChangeFreqency.WEEKLY.getValue(), urls.get(ada + "/pictures").getChangefreq());
    assertEquals(Url.Priority.LOW.getValue(), urls.get(ada + "/favorites").getPriority());
    assertEquals(Url.Priority.MEDIUM.getValue(), urls.get(ada + "/rss").getPriority());
    assertEquals(Url.Priority.MEDIUM.getValue(), urls.get(ada + "/atom").getPriority());
    assertEquals(Url.Priority.MEDIUM.getValue(), urls.get(ada + "/json").getPriority());
    assertFalse(urls.containsKey(BASE + "users/hidden"));
    assertFalse(urls.containsKey(BASE + "users/owner"));
  }
}
