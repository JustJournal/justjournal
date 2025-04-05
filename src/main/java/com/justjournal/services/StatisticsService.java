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


import com.justjournal.exception.ServiceException;
import com.justjournal.model.*;
import com.justjournal.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provide Statistics Services for Just Journal
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
@Transactional
public class StatisticsService {

  private final EntryRepository entryRepository;

  private final UserRepository userRepository;

  private final CommentRepository commentRepository;

  private final TagRepository tagDao;


  private final StyleRepository styleRepository;

  public StatisticsService(EntryRepository entryRepository, UserRepository userRepository, CommentRepository commentRepository, TagRepository tagDao, StyleRepository styleRepository) {
    this.entryRepository = entryRepository;
    this.userRepository = userRepository;
    this.commentRepository = commentRepository;
    this.tagDao = tagDao;
    this.styleRepository = styleRepository;
  }

  @Transactional
  public UserStatistics getUserStatistics(final String username) throws ServiceException {

    try {
      final UserStatistics userStatistics = new UserStatistics();

      if (username == null) {
        return null;
      }
      final User user = userRepository.findByUsername(username);
      if (user == null) return null;

      userStatistics.setUsername(username);

      userStatistics.setEntryCount(entryRepository.countByUsername(username).intValue());
      userStatistics.setCommentCount(commentRepository.countByUsername(username).intValue());

      return userStatistics;
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      throw new ServiceException(e);
    }
  }

  public Statistics getStatistics() throws ServiceException {
    try {
      final Statistics statistics = new StatisticsImpl();

      statistics.setComments(commentRepository.count());
      statistics.setEntries(entryRepository.count());
      statistics.setUsers(userRepository.count());
      statistics.setTags(tagDao.count());

      statistics.setStyles(styleRepository.count());

      statistics.setPublicEntries(
          entryRepository.countBySecurity(Security.PUBLIC));
      statistics.setPrivateEntries(
          entryRepository.countBySecurity(Security.PRIVATE));
      statistics.setFriendsEntries(
          entryRepository.countBySecurity(Security.FRIENDS));

      return statistics;
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      throw new ServiceException(e);
    }
  }
}
