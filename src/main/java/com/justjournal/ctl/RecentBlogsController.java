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
import static com.justjournal.core.Constants.MIME_TYPE_RSS;

import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.Security;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.rss.Rss;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Display recent blog entries in RSS format.
 *
 * @author Lucas Holt
 * @version $Id: RecentBlogs.java,v 1.15 2011/07/01 11:54:31 laffer1 Exp $
 */
@Slf4j
@RestController
@RequestMapping("/RecentBlogs")
public class RecentBlogsController {

  private final EntryRepository entryRepository;

  private final Settings set;

  private final Rss rss;

  private final RecentBlogsRepository recentBlogsRepository;

  public RecentBlogsController(EntryRepository entryRepository, Settings set, Rss rss, RecentBlogsRepository recentBlogsRepository) {
    this.entryRepository = entryRepository;
    this.set = set;
    this.rss = rss;
    this.recentBlogsRepository = recentBlogsRepository;
  }

  @GetMapping(produces = MIME_TYPE_RSS + ";charset=UTF-8")
  public ResponseEntity<String> get() {
    long expiresTime = System.currentTimeMillis() + 1000 * 60;

    Optional<String> blogs = recentBlogsRepository.getBlogs().blockOptional(Duration.ofMinutes(1));
    if (blogs.isPresent()) {
      return ResponseEntity.ok()
              .header(HEADER_CACHE_CONTROL, "max-age=60, private, proxy-revalidate")
              .header(HEADER_EXPIRES, String.valueOf(expiresTime))
              .body(blogs.get());
    }

    // Create an RSS object, set the required
    // properties (title, description language, url)
    // and write it to the sb output.
    try {

      final java.util.GregorianCalendar gregorianCalendar =
          new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"));
      gregorianCalendar.setTime(new java.util.Date());

      rss.setTitle("JJ New Posts");
      rss.setLink(set.getBaseUri());
      rss.setDescription("New blog posts on Just Journal");
      rss.setLanguage("en-us");
      rss.setCopyright(
          "Copyright "
              + gregorianCalendar.get(Calendar.YEAR)
              + " JustJournal.com and its blog account owners.");
      rss.setWebMaster(set.getSiteAdminEmail() + " (" + set.getSiteAdmin() + ")");
      rss.setManagingEditor(set.getSiteAdminEmail() + " (" + set.getSiteAdmin() + ")");
      rss.setSelfLink(set.getBaseUri() + "RecentBlogs");

      final Pageable pageable = PageRequest.of(1, 20);
      final Page<Entry> entries = entryRepository.findBySecurityOrderByDateDesc(Security.PUBLIC, pageable);

      final Map<String, Entry> map = new HashMap<>();
      int count = 0;
      for (final Entry e : entries) {
        if (count == 15) {
          break;
        }

        if (map.containsKey(e.getUser().getUsername())) {
          continue;
        }

        map.put(e.getUser().getUsername(), e);
        count++;
      }

      rss.populate(map.values());

      final Date d = rss.getNewestEntryDate();
      String result = rss.toXml();
      recentBlogsRepository.setBlogs(result).subscribe();

      return ResponseEntity.ok()
              .header(HEADER_CACHE_CONTROL, "max-age=60, private, proxy-revalidate")
              .header(HEADER_EXPIRES, String.valueOf(expiresTime))
              .header(HEADER_LAST_MODIFIED, d != null ? String.valueOf(d.getTime()) : String.valueOf(System.currentTimeMillis()))
              .body(result);
    } catch (final Exception e) {
      // oops, we goofed somewhere.  It's not in the original spec
      // how to handle error conditions with rss.
      // html back isn't good, but what do we do?
      log.error("Could not generate recent blogs", e);
    }

    return ResponseEntity.internalServerError().body("Could not generate recent blogs");
  }
}
