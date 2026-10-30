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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.PasswordType;
import com.justjournal.model.QueueMail;
import com.justjournal.model.User;
import com.justjournal.model.UserPref;
import com.justjournal.model.api.NewUser;
import com.justjournal.repository.JournalRepository;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.UserBioRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserPrefRepository;
import com.justjournal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
class AccountServiceTests {

  @Mock private UserRepository userRepository;
  @Mock private UserBioRepository userBioDao;
  @Mock private UserContactRepository userContactRepository;
  @Mock private UserPrefRepository userPrefRepository;
  @Mock private JournalRepository journalRepository;
  @Mock private StyleService styleService;
  @Mock private Login login;
  @Mock private QueueMailRepository queueMailRepository;
  @Mock private Settings settings;

  @InjectMocks private AccountService accountService;

  private NewUser newUser() {
    final NewUser newUser = new NewUser();
    newUser.setUsername("technews");
    newUser.setPassword("secret123");
    newUser.setEmail(" test@justjournal.com ");
    newUser.setFirstName("Tech");
    return newUser;
  }

  @Test
  void signupSetsNotNullColumns() throws Exception {
    final NewUser newUser = new NewUser();
    newUser.setUsername("technews");
    newUser.setPassword("secret123");
    newUser.setEmail("test@justjournal.com");
    newUser.setFirstName("Tech");

    when(login.getHashedPassword(anyString(), anyString())).thenReturn("$argon2id$hash");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    accountService.signup(newUser);

    final ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(user.capture());
    assertNotNull(user.getValue().getModified(), "user.modified is NOT NULL in the database");
    assertEquals(PasswordType.ARGON2, user.getValue().getPasswordType());

    final ArgumentCaptor<UserPref> pref = ArgumentCaptor.forClass(UserPref.class);
    verify(userPrefRepository).save(pref.capture());
    assertNotNull(pref.getValue().getModified(), "user_pref.modified is NOT NULL in the database");
  }

  @Test
  void signupQueuesAWelcomeMailWithTheBlogLink() throws Exception {
    when(login.getHashedPassword(anyString(), anyString())).thenReturn("$argon2id$hash");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(settings.getMailFrom()).thenReturn("noreply@justjournal.com");
    when(settings.getSiteName()).thenReturn("JustJournal");
    when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");
    when(settings.getBlogBaseUrl("technews")).thenReturn("https://www.justjournal.com/users/technews");

    accountService.signup(newUser());

    final ArgumentCaptor<QueueMail> mail = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).save(mail.capture());
    assertEquals("test@justjournal.com", mail.getValue().getTo(), "address is trimmed");
    assertEquals("noreply@justjournal.com", mail.getValue().getFrom());
    assertEquals("Welcome to JustJournal", mail.getValue().getSubject());
    assertEquals("welcome", mail.getValue().getPurpose());
    assertTrue(mail.getValue().getBody().contains("Hi Tech,"));
    assertTrue(mail.getValue().getBody().contains("https://www.justjournal.com/users/technews"));
  }

  @Test
  void signupStillSucceedsWhenTheWelcomeMailCannotBeQueued() throws Exception {
    when(login.getHashedPassword(anyString(), anyString())).thenReturn("$argon2id$hash");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(settings.getBlogBaseUrl("technews")).thenReturn("https://www.justjournal.com/users/technews");
    doThrow(new RuntimeException("db down")).when(queueMailRepository).save(any(QueueMail.class));

    assertNotNull(accountService.signup(newUser()));
  }

  @Test
  void noWelcomeMailWithoutAnAddress() throws Exception {
    when(login.getHashedPassword(anyString(), anyString())).thenReturn("$argon2id$hash");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    final NewUser user = newUser();
    user.setEmail("");

    accountService.signup(user);

    verify(queueMailRepository, never()).save(any(QueueMail.class));
  }
}
