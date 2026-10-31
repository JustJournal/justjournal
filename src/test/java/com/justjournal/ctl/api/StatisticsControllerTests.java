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
package com.justjournal.ctl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.StatisticsImpl;
import com.justjournal.model.UserStatistics;
import com.justjournal.services.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The statistics API passes service results through and maps failures to status codes. */
class StatisticsControllerTests {

  private StatisticsService statisticsService;
  private StatisticsController controller;

  @BeforeEach
  void setUp() {
    statisticsService = mock(StatisticsService.class);
    controller = new StatisticsController(statisticsService);
  }

  @Test
  void get_returnsSiteStatistics_orNotFoundWhenUnavailable() throws ServiceException {
    final StatisticsImpl stats = new StatisticsImpl();
    when(statisticsService.getStatistics()).thenReturn(stats);
    assertSame(stats, controller.get().getBody());

    when(statisticsService.getStatistics()).thenThrow(new ServiceException("db"));
    assertEquals(HttpStatus.NOT_FOUND, controller.get().getStatusCode());
  }

  @Test
  void getById_returnsUserStatistics() throws ServiceException {
    final UserStatistics stats = new UserStatistics();
    stats.setUsername("ada");
    when(statisticsService.getUserStatistics("ada")).thenReturn(stats);

    assertSame(stats, controller.getById("ada").getBody());
  }

  @Test
  void getById_missingUserInvalidNameOrFailure() throws ServiceException {
    when(statisticsService.getUserStatistics("nobody")).thenReturn(null);
    assertEquals(HttpStatus.NOT_FOUND, controller.getById("nobody").getStatusCode());

    assertThrows(NotFoundException.class, () -> controller.getById("no!"));

    when(statisticsService.getUserStatistics("broken")).thenThrow(new ServiceException("db"));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.getById("broken").getStatusCode());
  }
}
