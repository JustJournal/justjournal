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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.model.QueueMail;
import com.justjournal.model.Settings;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CommentMailServiceTests {

  private SettingsRepository settingsRepository;
  private QueueMailRepository queueMailRepository;
  private CommentMailService service;
  private Entry entry;

  @BeforeEach
  void setUp() {
    settingsRepository = mock(SettingsRepository.class);
    queueMailRepository = mock(QueueMailRepository.class);
    service = new CommentMailService(settingsRepository, queueMailRepository);

    final User owner = new User();
    owner.setId(7);
    owner.setUsername("bob");
    final UserContact contact = new UserContact();
    contact.setEmail("bob@example.com");
    ReflectionTestUtils.setField(owner, "userContact", contact);
    entry = new Entry();
    entry.setId(5);
    entry.setUser(owner);
    entry.setEmailComments(PrefBool.Y);

    final Settings base = new Settings();
    base.setValue("https://www.justjournal.com/");
    when(settingsRepository.findByName("baseuri")).thenReturn(base);
  }

  @Test
  void theOwnerIsMailedWithALinkToTheEntry() {
    service.commentAdded(entry, "Alice (@alice@remote.social)", "(no subject)", "Nice post");

    final ArgumentCaptor<QueueMail> captor = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).save(captor.capture());
    final QueueMail mail = captor.getValue();
    assertEquals("bob@example.com", mail.getTo());
    assertEquals("root@localhost", mail.getFrom());
    assertEquals("comment_notify", mail.getPurpose());
    assertTrue(mail.getBody().startsWith("Alice (@alice@remote.social) said: \n"), mail.getBody());
    assertTrue(mail.getBody().contains("Nice post"), mail.getBody());
    assertTrue(
        mail.getBody().contains("https://www.justjournal.com/users/bob/entry/5"), mail.getBody());
  }

  @Test
  void nothingIsSentWhenTheEntryAsksForNoMail() {
    entry.setEmailComments(PrefBool.N);

    service.commentAdded(entry, "alice", "hi", "Nice post");

    verify(queueMailRepository, never()).save(any());
  }
}
