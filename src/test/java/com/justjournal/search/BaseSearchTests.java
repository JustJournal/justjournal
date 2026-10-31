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
package com.justjournal.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** BaseSearch builds a LIKE query from the search terms and runs it. */
class BaseSearchTests {

  private JdbcTemplate jdbcTemplate;
  private BaseSearch search;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    search = new BaseSearch(jdbcTemplate);
    search.setBaseQuery("SELECT id FROM entry WHERE");
    search.setFields("subject body");
  }

  private String runAndCaptureSql(final String query) {
    search.search(query);
    final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbcTemplate).queryForList(captor.capture());
    return captor.getValue();
  }

  @Test
  void search_matchesEveryTermAgainstEveryField() {
    search.setSortDescending("date");
    search.setMaxResults(5);
    final List<Map<String, Object>> rows = List.of(Map.of("id", 1));
    when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

    final List<Map<String, Object>> result = search.search("cats dogs");

    assertSame(rows, result);
    final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbcTemplate).queryForList(captor.capture());
    assertEquals(
        "SELECT id FROM entry WHERE (subject like '%cats%' or body like '%cats%') and "
            + " (subject like '%dogs%' or body like '%dogs%') and  1=1 ORDER BY date DESC LIMIT 0,5;",
        captor.getValue());
  }

  @Test
  void search_dropsAndWildcardsAndSqlPunctuation() {
    search.setSortAscending("subject");

    final String sql = runAndCaptureSql("cats and dog* big|small x;y");

    assertEquals(
        "SELECT id FROM entry WHERE (subject like '%cats%' or body like '%cats%') and  1=1 ORDER BY subject LIMIT 0,30;",
        sql);
  }

  @Test
  void emptyBaseQueryAndSortAreIgnored() {
    search.setBaseQuery(null);
    search.setBaseQuery("");
    search.setSortAscending("");
    search.setSortDescending(null);

    final String sql = runAndCaptureSql("x");

    assertTrue(sql.startsWith("SELECT id FROM entry WHERE (subject like '%x%' or body like '%x%') and  1=1 null LIMIT 0,30;"), sql);
  }

  @Test
  void aFailedQuery_givesNoResults() {
    when(jdbcTemplate.queryForList(anyString())).thenThrow(new IllegalStateException("db"));

    assertTrue(search.search("cats").isEmpty());
  }
}
