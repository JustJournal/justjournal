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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

import com.justjournal.model.EntryStatistic;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryStatisticRepository;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Lucas Holt
 */
@ExtendWith(MockitoExtension.class)
class EntryStatisticsServiceTests {

    private static final int CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR);

    @Mock
    private EntryStatisticRepository entryStatisticRepository;

    @Mock
    private EntryRepository entryRepository;

    @InjectMocks
    private EntryStatisticService entryStatisticService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setSince(2010);
    }

    private static Object[] row(final int year, final long count) {
        return new Object[] {year, count};
    }

    private static EntryStatistic stat(final User user, final int year, final long count) {
        final EntryStatistic es = new EntryStatistic();
        es.setUser(user);
        es.setYear(year);
        es.setCount(count);
        return es;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, EntryStatistic> savedByYear() {
        final ArgumentCaptor<Iterable<EntryStatistic>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(entryStatisticRepository).saveAll(captor.capture());
        final List<EntryStatistic> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);
        return saved.stream().collect(Collectors.toMap(EntryStatistic::getYear, es -> es));
    }

    @Test
    void compute_usesOneCountQuery_andCreatesEveryYearSinceJoining() {
        when(entryRepository.countByYear(testUser)).thenReturn(List.of(row(2012, 7L), row(2015, 3L)));
        when(entryStatisticRepository.findByUser(testUser)).thenReturn(List.of());

        entryStatisticService.compute(testUser);

        verify(entryRepository, times(1)).countByYear(testUser);
        verify(entryRepository, never()).calendarCount(anyInt(), anyString());
        final Map<Integer, EntryStatistic> saved = savedByYear();
        assertEquals(CURRENT_YEAR - 2010 + 1, saved.size());
        assertEquals(7L, saved.get(2012).getCount());
        assertEquals(3L, saved.get(2015).getCount());
        assertEquals(0L, saved.get(2010).getCount());
        assertSame(testUser, saved.get(2012).getUser());
        assertNotNull(saved.get(2012).getModified());
    }

    @Test
    void compute_updatesOnlyYearsWhoseCountChanged() {
        testUser.setSince(CURRENT_YEAR - 2);
        final EntryStatistic unchanged = stat(testUser, CURRENT_YEAR - 2, 5L);
        final EntryStatistic stale = stat(testUser, CURRENT_YEAR - 1, 5L);
        final EntryStatistic same = stat(testUser, CURRENT_YEAR, 0L);
        when(entryRepository.countByYear(testUser))
                .thenReturn(List.of(row(CURRENT_YEAR - 2, 5L), row(CURRENT_YEAR - 1, 15L)));
        when(entryStatisticRepository.findByUser(testUser)).thenReturn(List.of(unchanged, stale, same));

        entryStatisticService.compute(testUser);

        final Map<Integer, EntryStatistic> saved = savedByYear();
        assertEquals(1, saved.size());
        assertSame(stale, saved.get(CURRENT_YEAR - 1));
        assertEquals(15L, stale.getCount());
    }

    @Test
    void compute_writesNothing_whenAllCountsAreCurrent() {
        testUser.setSince(CURRENT_YEAR);
        when(entryRepository.countByYear(testUser)).thenReturn(List.<Object[]>of(row(CURRENT_YEAR, 4L)));
        when(entryStatisticRepository.findByUser(testUser))
                .thenReturn(List.of(stat(testUser, CURRENT_YEAR, 4L)));

        entryStatisticService.compute(testUser);

        verify(entryStatisticRepository, never()).saveAll(anyIterable());
    }

    @Test
    void compute_startsAt2003_whenUserJoinedEarlier() {
        testUser.setSince(2000);
        when(entryRepository.countByYear(testUser)).thenReturn(List.of(row(2001, 9L), row(2003, 2L)));
        when(entryStatisticRepository.findByUser(testUser)).thenReturn(List.of());

        entryStatisticService.compute(testUser);

        final Map<Integer, EntryStatistic> saved = savedByYear();
        assertEquals(CURRENT_YEAR - 2003 + 1, saved.size());
        assertFalse(saved.containsKey(2001));
        assertEquals(2L, saved.get(2003).getCount());
    }

    @Test
    void compute_ignoresDuplicateExistingRowsForAYear() {
        testUser.setSince(CURRENT_YEAR);
        final EntryStatistic first = stat(testUser, CURRENT_YEAR, 1L);
        final EntryStatistic duplicate = stat(testUser, CURRENT_YEAR, 1L);
        when(entryRepository.countByYear(testUser)).thenReturn(List.<Object[]>of(row(CURRENT_YEAR, 2L)));
        when(entryStatisticRepository.findByUser(testUser)).thenReturn(List.of(first, duplicate));

        entryStatisticService.compute(testUser);

        final Map<Integer, EntryStatistic> saved = savedByYear();
        assertSame(first, saved.get(CURRENT_YEAR));
        assertEquals(1L, duplicate.getCount());
    }
}
