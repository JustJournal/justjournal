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

import com.justjournal.Application;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Statistics;
import com.justjournal.model.UserStatistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/** @author Lucas Holt */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@ActiveProfiles("it")
 class ITStatisticsServiceTest {
  private static final String TEST_USER = "testuser";

  @Autowired private StatisticsService statisticsService;

  @Test
   void testGetStatistics() throws ServiceException {
    final Statistics statistics = statisticsService.getStatistics();
    Assertions.assertNotNull(statistics);
    Assertions.assertTrue(statistics.getComments() > 0);
    Assertions.assertTrue(statistics.getEntries() > 0);
    // assertTrue(statistics.getTags() > 0);
    // assertTrue(statistics.getStyles() > 0);
    // assertTrue(statistics.getPrivateEntries() > 0);
    Assertions.assertTrue(statistics.getPublicEntries() > 0);
    // assertTrue(statistics.getFriendsEntries() > 0);
  }

  @Test
   void testGetUserStatistics() throws ServiceException {
    final UserStatistics statistics = statisticsService.getUserStatistics(TEST_USER);
    Assertions.assertNotNull(statistics);
    Assertions.assertEquals("testuser", statistics.getUsername());
    Assertions.assertTrue(statistics.getEntryCount() > 0);
    Assertions.assertTrue(statistics.getCommentCount() > 0);
  }

  @Test
   void testGetUserStatisticsBadUser() throws ServiceException {
    final UserStatistics statistics = statisticsService.getUserStatistics("root");
    Assertions.assertNull(statistics);
  }
}
