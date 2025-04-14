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

import java.util.*;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * @author Lucas Holt
 * @version $Id: BaseSearch.java,v 1.6 2009/05/16 03:15:27 laffer1 Exp $
 */
@Slf4j
@Component
public class BaseSearch {

  protected ArrayList<String> terms = new ArrayList<>();
  protected ArrayList<String> fieldlist = new ArrayList<>();
  protected int maxresults = 30;
  protected String baseQuery;
  protected String sort;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  public BaseSearch(final JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void setMaxResults(final int results) {
    maxresults = results;
  }

  public void setBaseQuery(@Nullable final String base) {
    if (base != null && !base.isEmpty()) baseQuery = base;
  }

  public void setFields(@NotNull final String fields) {
    final String[] q = fields.split("\\s");
    fieldlist.addAll(Arrays.asList(q));
  }

  public void setSortAscending(@Nullable final String field) {
    if (field != null && !field.isEmpty()) sort = "ORDER BY " + field;
  }

  public void setSortDescending(@Nullable final String field) {
    if (field != null && !field.isEmpty()) sort = "ORDER BY " + field + " DESC";
  }

  public List<Map<String, Object>> search(final String query) {
    if (log.isDebugEnabled()) {
        log.debug("search() called with {}", query);
    }
    final List<Map<String, Object>> result;
    parseQuery(query);

    result = realSearch(terms);

    return result;
  }

  protected void parseQuery(@NotNull final String query) {
    final String[] q = query.split("\\s");
    final int qLen = java.lang.reflect.Array.getLength(q);

    for (int i = 0; i < qLen; i++) {
      if (!(q[i].equalsIgnoreCase("and")
          || (q[i].contains("*") || q[i].contains(";") || q[i].contains("|")))) terms.add(q[i]);
    }
  }

  protected List<Map<String, Object>> realSearch(final List<String> terms) {

    final StringBuilder sqlStmt = new StringBuilder(baseQuery);

    for (int i = 0; i < terms.size(); i++) {
      sqlStmt.append(" (");
      for (int y = 0; y < fieldlist.size(); y++) {
        if (y != 0) sqlStmt.append(" or ");
        sqlStmt.append(fieldlist.get(y)).append(" like '%").append(terms.get(i)).append("%'");
      }
      sqlStmt.append(") and ");
    }

    sqlStmt.append(" 1=1 ").append(sort).append(" LIMIT 0,").append(maxresults).append(";");

    try {
      if (log.isDebugEnabled()) {
          log.debug("realSearch() called on {}", sqlStmt);
      }

      return jdbcTemplate.queryForList(sqlStmt.toString());

    } catch (final Exception e) {
        log.error("Error executing search with query: {}; and error {}", sqlStmt, e.getMessage(), e);
    }

    return Collections.emptyList();
  }
}
