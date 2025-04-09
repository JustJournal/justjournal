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

import static com.justjournal.core.Constants.PARAM_USERNAME;
import static com.justjournal.core.Constants.PARAM_YEAR;

import com.justjournal.Login;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.CalendarCount;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * List blog entries by year, month and day for display on the site
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

  private static final int MONTHS = 12;

  private final UserRepository userRepository;

  private final EntryRepository entryRepository;

  public CalendarController(UserRepository userRepository, @Qualifier("entryRepository") EntryRepository entryRepository) {
    this.userRepository = userRepository;
    this.entryRepository = entryRepository;
  }

  @GetMapping(value = "/counts/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Collection<CalendarCount> getYearCounts(
      @PathVariable(PARAM_USERNAME) final String username, final HttpServletResponse response) {

    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    final User user = userRepository.findByUsername(username);
    if (user == null) {
      throw new NotFoundException();
    }

    final GregorianCalendar calendarg = new GregorianCalendar();
    final int yearNow = calendarg.get(Calendar.YEAR);
    final Collection<CalendarCount> counts = new ArrayList<>();

    for (int i = yearNow; i >= user.getSince(); i--) {
      final CalendarCount count = new CalendarCount();
      try {
        count.setCount(entryRepository.calendarCount(i, username));
      } catch (final Exception e) {
        count.setCount(0);
        log.error(e.getMessage());
      }
      count.setYear(i);

      counts.add(count);

      // just in case!
      if (i == 2002) break;
    }

    return counts;
  }

  @GetMapping(value = "/counts/{username}/{year}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Collection<CalendarCount> getMonthCounts(
      @PathVariable(PARAM_USERNAME) final String username,
      @PathVariable(PARAM_YEAR) final int year) {

    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    final User user = userRepository.findByUsername(username);
    if (user == null) {
      throw new NotFoundException();
    }

    final Collection<CalendarCount> counts = new ArrayList<>();

    for (int i = 1; i < MONTHS; i++) {
      final CalendarCount count = new CalendarCount();
      try {
        count.setCount(entryRepository.calendarCount(year, i, username));
      } catch (final Exception e) {
        count.setCount(0);
        log.error(e.getMessage());
      }
      count.setYear(year);
      count.setMonth(i);

      counts.add(count);
    }

    return counts;
  }
}
