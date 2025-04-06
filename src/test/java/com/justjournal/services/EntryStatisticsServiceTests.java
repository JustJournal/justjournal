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

import static org.mockito.Mockito.*;

import com.justjournal.model.EntryStatistic;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryStatisticRepository;

import java.util.Calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

/**
 * @author Lucas Holt
 */
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class EntryStatisticsServiceTests {

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

    @Test
    void compute_shouldUpdateExistingStatistics() {
        // Given
        int testYear = 2015;
        testUser.setSince(testYear);
        EntryStatistic existingStatistic = new EntryStatistic();
        existingStatistic.setUser(testUser);
        existingStatistic.setYear(testYear);
        existingStatistic.setCount(5L);

        when(entryStatisticRepository.findByUserAndYear(testUser, testYear)).thenReturn(existingStatistic);
        when(entryRepository.calendarCount(testYear, "testuser")).thenReturn(15L);

        // When
        entryStatisticService.compute(testUser);

        // Then
        verify(entryStatisticRepository).findByUserAndYear(eq(testUser), eq(testYear));
        verify(entryRepository).calendarCount(eq(testYear), eq("testuser"));
        verify(entryStatisticRepository).saveAndFlush(argThat(stat ->
                stat.getYear() == testYear && stat.getCount() == 15L && stat.getUser() == testUser
        ));
    }

    @Test
    void compute_shouldCreateAndUpdateStatistics() {
        // Given
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        testUser.setSince(2010);
        when(entryRepository.calendarCount(anyInt(), eq("testuser"))).thenReturn(10L);

        // When
        entryStatisticService.compute(testUser);

        // Then
        int expectedCalls = currentYear - 2010 + 1;
        verify(entryStatisticRepository, times(expectedCalls)).findByUserAndYear(eq(testUser), anyInt());
        verify(entryRepository, times(expectedCalls)).calendarCount(anyInt(), eq("testuser"));
        verify(entryStatisticRepository, times(expectedCalls)).saveAndFlush(any(EntryStatistic.class));
    }

    @Test
    void compute_shouldHandleUserWithSinceBelow2003() {
        // Given
        testUser.setSince(2000);
        when(entryRepository.calendarCount(anyInt(), eq("testuser"))).thenReturn(5L);

        // When
        entryStatisticService.compute(testUser);

        // Then
        verify(entryStatisticRepository, atLeastOnce()).findByUserAndYear(eq(testUser), eq(2003));
        verify(entryRepository, atLeastOnce()).calendarCount(eq(2003), eq("testuser"));
        verify(entryStatisticRepository, atLeastOnce()).saveAndFlush(any(EntryStatistic.class));
    }

    @Test
    void compute_shouldHandleCurrentYearBelow2004() {
        // Given
        testUser.setSince(2003);
        when(entryRepository.calendarCount(anyInt(), eq("testuser"))).thenReturn(5L);

        // When
        entryStatisticService.compute(testUser);

        // Then
        verify(entryStatisticRepository, atLeast(23)).findByUserAndYear(eq(testUser), anyInt());
        verify(entryRepository, atLeast(23)).calendarCount(anyInt(), eq("testuser"));
        verify(entryStatisticRepository, atLeast(23)).saveAndFlush(any(EntryStatistic.class));
    }
}
