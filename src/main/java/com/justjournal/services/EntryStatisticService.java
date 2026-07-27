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


import com.justjournal.model.EntryStatistic;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryStatisticRepository;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Compute yearly entry statistics
 *
 * @author Lucas Holt
 */
@Service
@Slf4j
public class EntryStatisticService {

  private final EntryStatisticRepository entryStatisticRepository;

  private final EntryRepository entryRepository;

  @Autowired
  public EntryStatisticService(
      final EntryStatisticRepository entryStatisticRepository,
      final EntryRepository entryRepository) {
    this.entryStatisticRepository = entryStatisticRepository;
    this.entryRepository = entryRepository;
  }

  @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
  public EntryStatistic getEntryCount(final String username, final int year) {
    log.trace("Fetching entry count for {} and year {}", username, year);

    return entryStatisticRepository.findByUsernameAndYear(username, year);
  }

  @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
  public List<EntryStatistic> getEntryCounts(final String username) {
    return entryStatisticRepository.findByUsernameOrderByYearDesc(username);
  }

  @Transactional(isolation = Isolation.READ_UNCOMMITTED)
  public void compute(final User user) {

      final GregorianCalendar calendarg = new GregorianCalendar();
      int endYear = calendarg.get(Calendar.YEAR);
      int startYear = user.getSince();

      log.debug("Computing statistics for user: {} Start year: {}, End year: {}", user.getUsername(), startYear, endYear);

      if (endYear < 2003) endYear = 2004;

      if (startYear < 2003) startYear = 2003;

    for (int yr = startYear; yr <= endYear; yr++) {
        log.debug("testing with year: {} user: {}", yr , user.getUsername());
        EntryStatistic es = entryStatisticRepository.findByUserAndYear(user, yr);
        final long count = entryRepository.calendarCount(yr, user.getUsername());

        if (es == null) {
            log.trace("Creating new entry statistic for {}", user.getUsername());
            es = new EntryStatistic();
            es.setUser(user);
        } else {
            log.trace("Updating existing entry statistic for year: {}", yr);
        }

        es.setCount(count);
        es.setYear(yr);
        es.setModified(calendarg.getTime());

        log.trace("save and flush year: {} user: {} count: {}", yr, user.getUsername(), count);
        entryStatisticRepository.saveAndFlush(es);
    }
  }
}
