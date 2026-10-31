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
package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.exception.ServiceException;
import com.justjournal.model.Security;
import com.justjournal.model.Statistics;
import com.justjournal.model.User;
import com.justjournal.model.UserStatistics;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.StyleRepository;
import com.justjournal.repository.TagRepository;
import com.justjournal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Site and per-user statistics are plain counts from the repositories. */
class StatisticsServiceTests {

  private EntryRepository entryRepository;
  private UserRepository userRepository;
  private CommentRepository commentRepository;
  private TagRepository tagRepository;
  private StyleRepository styleRepository;
  private StatisticsService service;

  @BeforeEach
  void setUp() {
    entryRepository = mock(EntryRepository.class);
    userRepository = mock(UserRepository.class);
    commentRepository = mock(CommentRepository.class);
    tagRepository = mock(TagRepository.class);
    styleRepository = mock(StyleRepository.class);
    service =
        new StatisticsService(
            entryRepository, userRepository, commentRepository, tagRepository, styleRepository);
  }

  @Test
  void getUserStatistics_countsEntriesAndComments() throws ServiceException {
    final User user = new User();
    user.setUsername("ada");
    when(userRepository.findByUsername("ada")).thenReturn(user);
    when(entryRepository.countByUsername("ada")).thenReturn(12L);
    when(commentRepository.countByUsername("ada")).thenReturn(3L);

    final UserStatistics stats = service.getUserStatistics("ada");

    assertEquals("ada", stats.getUsername());
    assertEquals(12, stats.getEntryCount());
    assertEquals(3, stats.getCommentCount());
  }

  @Test
  void getUserStatistics_isNullForMissingUsers() throws ServiceException {
    assertNull(service.getUserStatistics(null));
    when(userRepository.findByUsername("nobody")).thenReturn(null);
    assertNull(service.getUserStatistics("nobody"));
  }

  @Test
  void getUserStatistics_wrapsRepositoryFailures() {
    when(userRepository.findByUsername("ada")).thenThrow(new IllegalStateException("db"));

    assertThrows(ServiceException.class, () -> service.getUserStatistics("ada"));
  }

  @Test
  void getStatistics_collectsEveryCount() throws ServiceException {
    when(commentRepository.count()).thenReturn(10L);
    when(entryRepository.count()).thenReturn(20L);
    when(userRepository.count()).thenReturn(5L);
    when(tagRepository.count()).thenReturn(7L);
    when(styleRepository.count()).thenReturn(2L);
    when(entryRepository.countBySecurity(Security.PUBLIC)).thenReturn(11L);
    when(entryRepository.countBySecurity(Security.PRIVATE)).thenReturn(6L);
    when(entryRepository.countBySecurity(Security.FRIENDS)).thenReturn(3L);

    final Statistics stats = service.getStatistics();

    assertEquals(10L, stats.getComments());
    assertEquals(20L, stats.getEntries());
    assertEquals(5L, stats.getUsers());
    assertEquals(7L, stats.getTags());
    assertEquals(2L, stats.getStyles());
    assertEquals(11L, stats.getPublicEntries());
    assertEquals(6L, stats.getPrivateEntries());
    assertEquals(3L, stats.getFriendsEntries());
  }

  @Test
  void getStatistics_wrapsRepositoryFailures() {
    when(commentRepository.count()).thenThrow(new IllegalStateException("db"));

    assertThrows(ServiceException.class, () -> service.getStatistics());
  }
}
