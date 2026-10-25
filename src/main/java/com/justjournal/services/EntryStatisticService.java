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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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

  /**
   * Recompute a user's yearly entry counts, from the year they joined (no earlier than 2003) to
   * the current year.
   *
   * <p>Uses one grouped count query and one lookup of the existing rows, and only writes years
   * whose count changed. Each call is its own short transaction.
   *
   * @param user user to compute statistics for
   */
  @Transactional(isolation = Isolation.READ_UNCOMMITTED)
  public void compute(final User user) {
    final GregorianCalendar calendarg = new GregorianCalendar();
    int endYear = calendarg.get(Calendar.YEAR);
    int startYear = user.getSince();

    if (endYear < 2003) endYear = 2004;

    if (startYear < 2003) startYear = 2003;

    log.trace(
        "Computing statistics for user: {} Start year: {}, End year: {}",
        user.getUsername(),
        startYear,
        endYear);

    final Map<Integer, Long> counts = new HashMap<>();
    for (final Object[] row : entryRepository.countByYear(user)) {
      if (row[0] != null) counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
    }

    final Map<Integer, EntryStatistic> existing = new HashMap<>();
    for (final EntryStatistic es : entryStatisticRepository.findByUser(user)) {
      existing.putIfAbsent(es.getYear(), es);
    }

    final Date now = calendarg.getTime();
    final List<EntryStatistic> changed = new ArrayList<>();

    for (int yr = startYear; yr <= endYear; yr++) {
      final long count = counts.getOrDefault(yr, 0L);
      EntryStatistic es = existing.get(yr);

      if (es == null) {
        es = new EntryStatistic();
        es.setUser(user);
        es.setYear(yr);
      } else if (es.getCount() == count) {
        continue;
      }

      es.setCount(count);
      es.setModified(now);
      changed.add(es);
    }

    if (!changed.isEmpty()) {
      log.trace("Saving {} entry statistics for {}", changed.size(), user.getUsername());
      entryStatisticRepository.saveAll(changed);
    }
  }
}
