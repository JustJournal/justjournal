/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.QueueMail;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.cache.PasswordResetRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTests {

  private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

  @Mock private UserRepository userRepository;

  @Mock private UserContactRepository userContactRepository;

  @Mock private QueueMailRepository queueMailRepository;

  @Mock private PasswordResetRepository passwordResetRepository;

  @Mock private Settings settings;

  @Mock private Login login;

  @InjectMocks private PasswordResetService passwordResetService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(7);
    user.setUsername("jjsite");
    final UserContact contact = new UserContact();
    contact.setEmail("Owner@JustJournal.com");
    when(userRepository.findByUsername("jjsite")).thenReturn(user);
    when(userRepository.findById(7)).thenReturn(Optional.of(user));
    when(userContactRepository.findByUser(user)).thenReturn(contact);
    when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");
    when(settings.getMailFrom()).thenReturn("noreply@justjournal.com");
    when(passwordResetRepository.tryAcquireUser(eq(7), any(Duration.class)))
        .thenReturn(Mono.just(true));
    when(passwordResetRepository.saveToken(anyString(), eq(7), any(Duration.class)))
        .thenReturn(Mono.just(true));
  }

  @Test
  void matchingUsernameAndEmailSendsResetLink() {
    passwordResetService.requestReset("jjsite", " owner@justjournal.com ");

    final ArgumentCaptor<QueueMail> mail = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).save(mail.capture());
    assertEquals("Owner@JustJournal.com", mail.getValue().getTo());
    assertEquals("password_reset", mail.getValue().getPurpose());

    final Matcher m =
        Pattern.compile("https://www\\.justjournal\\.com/password/reset#([A-Za-z0-9_-]{43})")
            .matcher(mail.getValue().getBody());
    assertTrue(m.find());

    // only the hash of the mailed token is stored
    final ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
    verify(passwordResetRepository)
        .saveToken(stored.capture(), eq(7), eq(PasswordResetService.TOKEN_TTL));
    assertEquals(PasswordResetService.hash(m.group(1)), stored.getValue());
  }

  @Test
  void wrongEmailSendsNothing() {
    passwordResetService.requestReset("jjsite", "someone@else.com");

    verify(passwordResetRepository, never()).saveToken(anyString(), anyInt(), any());
    verify(queueMailRepository, never()).save(any());
  }

  @Test
  void unknownUserSendsNothing() {
    passwordResetService.requestReset("nobody", "owner@justjournal.com");

    verify(queueMailRepository, never()).save(any());
  }

  @Test
  void throttledAccountSendsNothing() {
    when(passwordResetRepository.tryAcquireUser(eq(7), any(Duration.class)))
        .thenReturn(Mono.just(false));

    passwordResetService.requestReset("jjsite", "owner@justjournal.com");

    verify(passwordResetRepository, never()).saveToken(anyString(), anyInt(), any());
    verify(queueMailRepository, never()).save(any());
  }

  @Test
  void validTokenResetsPasswordAndNotifies() {
    when(passwordResetRepository.consumeToken(PasswordResetService.hash(TOKEN)))
        .thenReturn(Mono.just(7));
    when(login.resetPass(7, "newpass1")).thenReturn(true);

    assertTrue(passwordResetService.resetPassword(TOKEN, "newpass1"));

    final ArgumentCaptor<QueueMail> mail = ArgumentCaptor.forClass(QueueMail.class);
    verify(queueMailRepository).save(mail.capture());
    assertEquals("password_changed", mail.getValue().getPurpose());
  }

  @Test
  void unknownTokenIsRejected() {
    when(passwordResetRepository.consumeToken(anyString())).thenReturn(Mono.empty());

    assertFalse(passwordResetService.resetPassword(TOKEN, "newpass1"));
    verify(login, never()).resetPass(anyInt(), anyString());
  }

  @Test
  void malformedTokenIsRejectedWithoutLookup() {
    assertFalse(passwordResetService.resetPassword("short", "newpass1"));
    assertFalse(passwordResetService.resetPassword(null, "newpass1"));
    verify(passwordResetRepository, never()).consumeToken(anyString());
  }

  @Test
  void invalidPasswordDoesNotUseToken() {
    assertFalse(passwordResetService.resetPassword(TOKEN, "no"));
    verify(passwordResetRepository, never()).consumeToken(anyString());
  }
}
