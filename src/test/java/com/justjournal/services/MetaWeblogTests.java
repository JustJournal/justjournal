/*
 * Copyright (c) 2003-2026 Lucas Holt
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
package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.Journal;
import com.justjournal.model.Location;
import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.LocationRepository;
import com.justjournal.repository.MoodRepository;
import com.justjournal.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Verifies entry bodies are cleaned before they are saved through the MetaWeblog API. */
@ExtendWith(MockitoExtension.class)
class MetaWeblogTests {

  private static final String BROKEN_HTML = "<p>hello <textarea>oops";

  @Mock private EntryRepository entryRepository;
  @Mock private Login webLogin;
  @Mock private UserRepository userRepository;
  @Mock private MoodRepository moodDao;
  @Mock private LocationRepository locationDao;

  @InjectMocks private MetaWeblog metaWeblog;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(1);
    final Journal journal = new Journal();
    journal.setPingServices(false);
    user.getJournals().add(journal);

    when(webLogin.validate("laffer1", "secret")).thenReturn(1);
    lenient().when(userRepository.findById(1)).thenReturn(Optional.of(user));
    lenient().when(locationDao.findById(anyInt())).thenReturn(Optional.of(new Location()));
  }

  private Map<String, Object> content() {
    final Map<String, Object> content = new HashMap<>();
    content.put("title", "test");
    content.put("description", BROKEN_HTML);
    content.put("categories", new String[0]);
    return content;
  }

  @Test
  void newPostCleansBody() {
    when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

    metaWeblog.newPost("", "laffer1", "secret", content(), true);

    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    final Entry saved = captor.getValue();
    assertTrue(saved.getBody().contains("</textarea>"), saved.getBody());
    assertEquals(FormatType.HTML, saved.getFormat());
    assertEquals(PrefBool.N, saved.getAutoFormat());
  }

  @Test
  void editPostCleansBody() {
    final Entry existing = new Entry();
    existing.setId(5);
    existing.setUser(user);
    existing.setFormat(FormatType.TEXT);
    existing.setAutoFormat(PrefBool.Y);
    when(entryRepository.findById(5)).thenReturn(Optional.of(existing));

    metaWeblog.editPost("5", "laffer1", "secret", content(), true);

    verify(entryRepository).save(existing);
    assertTrue(existing.getBody().contains("</textarea>"), existing.getBody());
    assertEquals(FormatType.HTML, existing.getFormat());
    assertEquals(PrefBool.N, existing.getAutoFormat());
  }
}
