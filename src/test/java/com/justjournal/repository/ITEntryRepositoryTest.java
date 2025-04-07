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
package com.justjournal.repository;

import com.justjournal.Application;
import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.Date;
import java.util.List;

/** @author Lucas Holt */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@ActiveProfiles("it")
class ITEntryRepositoryTest {
  @Autowired EntryRepository entryRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private LocationRepository locationRepository;

  @Autowired
  private MoodRepository moodRepository;

  @Test
  void list() {
    Iterable<Entry> list = entryRepository.findAll();
    Assertions.assertNotNull(list);
    Assertions.assertTrue(entryRepository.count() > 0);
  }

  @Test
  void get() {
    Entry entry = entryRepository.findById(33661).orElse(null);
    Assertions.assertNotNull(entry);
    Assertions.assertEquals(33661, entry.getId());
    Assertions.assertNotNull(entry.getSubject());
  }

  @Test
  void findByUser() {
    User user = userRepository.findByUsername("testuser");
    List<Entry> entries = entryRepository.findByUser(user);
    Assertions.assertFalse(entries.isEmpty());
    entries.forEach(entry -> Assertions.assertEquals(user.getUsername(), entry.getUser().getUsername()));
  }

  @Test
  void findByUsernameAndSecurity() {
    List<Entry> entries = entryRepository.findByUsernameAndSecurity("testuser", Security.PUBLIC);
    Assertions.assertFalse(entries.isEmpty());
    entries.forEach(entry -> {
      Assertions.assertEquals("testuser", entry.getUser().getUsername());
      Assertions.assertEquals(Security.PUBLIC, entry.getSecurity());
    });
  }

  @Test
  void findByUserAndSecurityAndDraftOrderByDateDesc() {
    User user = userRepository.findByUsername("testuser");
    List<Entry> entries = entryRepository.findByUserAndSecurityAndDraftOrderByDateDesc(user, Security.PUBLIC, PrefBool.N);
    Assertions.assertFalse(entries.isEmpty());
    entries.forEach(entry -> {
      Assertions.assertEquals(user.getUsername(), entry.getUser().getUsername());
      Assertions.assertEquals(Security.PUBLIC, entry.getSecurity());
      Assertions.assertEquals(PrefBool.N, entry.getDraft());
    });
    // Check if entries are ordered by date descending
    for (int i = 1; i < entries.size(); i++) {
      var e1 = entries.get(i-1).getDate();
      var e2 = entries.get(i).getDate();
      Assertions.assertTrue(e1.equals(e2) || e1.after(e2));
    }
  }

  @Test
  void findBySecurityOrderByDateDesc() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<Entry> entryPage = entryRepository.findBySecurityOrderByDateDesc(Security.PUBLIC, pageable);
    Assertions.assertFalse(entryPage.isEmpty());
    entryPage.forEach(entry -> Assertions.assertEquals(Security.PUBLIC, entry.getSecurity()));
    // Check if entries are ordered by date descending
    List<Entry> entries = entryPage.getContent();
    for (int i = 1; i < entries.size(); i++) {
      var e1 = entries.get(i-1).getDate();
      var e2 = entries.get(i).getDate();
      Assertions.assertTrue(e1.equals(e2) || e1.after(e2));
    }
  }

  @Test
  void findByUsernameAndDate() {
    var oneDayAgo = System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000;
    Date startDate = new Date(oneDayAgo);
    Date endDate = new Date();
    Entry entry1 = new Entry();
    entry1.setDate(new Date(oneDayAgo + 120));
    entry1.setSubject("Test Entry");
    entry1.setBody("testing from entry repository tests");
    entry1.setUser(userRepository.findByUsername("testuser"));
    entry1.setSecurity(Security.PUBLIC);
    entry1.setDraft(PrefBool.N);
    entry1.setLocation(locationRepository.findById(1).orElse(null));
    entry1.setModified(startDate);
    entry1.setMood(moodRepository.findAll().get(0));
    entry1 = entryRepository.save(entry1);

    List<Entry> entries = entryRepository.findByUsernameAndDate("testuser", startDate, endDate);
    Assertions.assertFalse(entries.isEmpty());
    entries.forEach(entry -> {
      Assertions.assertEquals("testuser", entry.getUser().getUsername());
      Assertions.assertTrue(entry.getDate().after(startDate) || entry.getDate().equals(startDate));
      Assertions.assertTrue(entry.getDate().before(endDate) || entry.getDate().equals(endDate));
    });

    entryRepository.deleteById(entry1.getId());
  }

  @Test
  void calendarCount() {
    int year = 2023;
    int month = 5;
    String username = "testuser";
    Long count = entryRepository.calendarCount(year, month, username);
    Assertions.assertNotNull(count);
    Assertions.assertTrue(count >= 0);
  }

  @Test
  void countBySecurity() {
    Long count = entryRepository.countBySecurity(Security.PUBLIC);
    Assertions.assertNotNull(count);
    Assertions.assertTrue(count > 0);
  }


}
