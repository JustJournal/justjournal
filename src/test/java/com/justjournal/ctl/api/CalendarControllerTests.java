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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.CalendarCount;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** The calendar API counts a user's entries per year and per month. */
class CalendarControllerTests {

  private static final int THIS_YEAR = Calendar.getInstance().get(Calendar.YEAR);

  private UserRepository userRepository;
  private EntryRepository entryRepository;
  private CalendarController controller;
  private User user;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    entryRepository = mock(EntryRepository.class);
    controller = new CalendarController(userRepository, entryRepository);
    user = new User();
    user.setId(1);
    user.setUsername("ada");
    user.setSince(THIS_YEAR - 2);
    when(userRepository.findByUsername("ada")).thenReturn(user);
  }

  @Test
  void getYearCounts_coversEveryYearSinceTheUserJoined() {
    when(entryRepository.calendarCount(THIS_YEAR, "ada")).thenReturn(5L);
    when(entryRepository.calendarCount(THIS_YEAR - 1, "ada")).thenReturn(12L);
    when(entryRepository.calendarCount(THIS_YEAR - 2, "ada")).thenReturn(0L);

    final List<CalendarCount> counts =
        new ArrayList<>(controller.getYearCounts("ada", new MockHttpServletResponse()));

    assertEquals(3, counts.size());
    assertEquals(THIS_YEAR, counts.get(0).getYear());
    assertEquals(5L, counts.get(0).getCount());
    assertEquals(THIS_YEAR - 1, counts.get(1).getYear());
    assertEquals(12L, counts.get(1).getCount());
    assertEquals(THIS_YEAR - 2, counts.get(2).getYear());
    assertEquals(0L, counts.get(2).getCount());
  }

  @Test
  void getYearCounts_stopsAt2002_andTreatsFailuresAsZero() {
    user.setSince(1990);
    when(entryRepository.calendarCount(anyInt(), eq("ada"))).thenThrow(new IllegalStateException("db"));

    final List<CalendarCount> counts =
        new ArrayList<>(controller.getYearCounts("ada", new MockHttpServletResponse()));

    assertEquals(THIS_YEAR - 2002 + 1, counts.size());
    assertEquals(2002, counts.get(counts.size() - 1).getYear());
    assertEquals(0L, counts.get(0).getCount());
  }

  @Test
  void getYearCounts_unknownOrInvalidUser_isNotFound() {
    assertThrows(
        NotFoundException.class, () -> controller.getYearCounts("no!", new MockHttpServletResponse()));
    assertThrows(
        NotFoundException.class, () -> controller.getYearCounts("nobody", new MockHttpServletResponse()));
  }

  @Test
  void getMonthCounts_reportsEachMonthOfTheYear() {
    when(entryRepository.calendarCount(eq(2024), anyInt(), eq("ada"))).thenReturn(1L);
    when(entryRepository.calendarCount(2024, 3, "ada")).thenReturn(7L);
    when(entryRepository.calendarCount(2024, 5, "ada")).thenThrow(new IllegalStateException("db"));

    final List<CalendarCount> counts = new ArrayList<>(controller.getMonthCounts("ada", 2024));

    assertEquals(11, counts.size());
    for (int i = 0; i < counts.size(); i++) {
      assertEquals(2024, counts.get(i).getYear());
      assertEquals(i + 1, counts.get(i).getMonth());
    }
    assertEquals(1L, counts.get(0).getCount());
    assertEquals(7L, counts.get(2).getCount());
    assertEquals(0L, counts.get(4).getCount());
  }

  @Test
  void getMonthCounts_unknownOrInvalidUser_isNotFound() {
    assertThrows(NotFoundException.class, () -> controller.getMonthCounts("no!", 2024));
    assertThrows(NotFoundException.class, () -> controller.getMonthCounts("nobody", 2024));
  }
}
