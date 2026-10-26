/*
 * Copyright (c) 2003-2026 Lucas Holt
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
package com.justjournal.ctl.api.entry.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.justjournal.config.WebMvcConfig;
import com.justjournal.model.EntryStatistic;
import com.justjournal.services.EntryStatisticService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/** The archive sidebar must not be served stale when an entry count changes. */
@ExtendWith(MockitoExtension.class)
class EntryStatisticsControllerTests {

  private static final String URL = "/api/entry/technews/statistics";

  @Mock private EntryStatisticService entryStatisticService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new EntryStatisticsController(entryStatisticService))
            .addMappedInterceptors(new String[] {"/api/**"}, WebMvcConfig.apiCacheInterceptor())
            .addFilters(new ShallowEtagHeaderFilter())
            .build();
  }

  private List<EntryStatistic> stats(final long count) {
    final EntryStatistic s = new EntryStatistic();
    s.setYear(2026);
    s.setCount(count);
    return new ArrayList<>(List.of(s));
  }

  @Test
  void etagChangesWhenCountChanges() throws Exception {
    when(entryStatisticService.getEntryCounts("technews")).thenReturn(stats(48), stats(49));

    final String first = mockMvc.perform(get(URL)).andReturn().getResponse().getHeader("ETag");
    final String second = mockMvc.perform(get(URL)).andReturn().getResponse().getHeader("ETag");

    assertNotNull(first);
    assertNotEquals(first, second);
  }

  @Test
  void unchangedDataRevalidatesWith304() throws Exception {
    when(entryStatisticService.getEntryCounts("technews")).thenReturn(stats(48), stats(48));

    final String etag = mockMvc.perform(get(URL)).andReturn().getResponse().getHeader("ETag");
    final MvcResult again = mockMvc.perform(get(URL).header("If-None-Match", etag)).andReturn();

    assertEquals(304, again.getResponse().getStatus());
  }

  @Test
  void apiResponsesMustBeRevalidatedAndNotShared() throws Exception {
    when(entryStatisticService.getEntryCounts("technews")).thenReturn(stats(48));

    final String cacheControl =
        mockMvc.perform(get(URL)).andReturn().getResponse().getHeader("Cache-Control");

    assertEquals("no-cache, private", cacheControl);
  }
}
