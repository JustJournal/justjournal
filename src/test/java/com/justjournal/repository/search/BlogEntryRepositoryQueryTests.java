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
package com.justjournal.repository.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.annotations.Query;

/**
 * Validates the hand written Elasticsearch queries. A malformed query is only rejected by the
 * Elasticsearch server at runtime, so check the JSON here.
 *
 * @author Lucas Holt
 */
class BlogEntryRepositoryQueryTests {

  private final ObjectMapper mapper = new ObjectMapper();

  private List<Method> queryMethods() {
    final List<Method> methods = new ArrayList<>();
    for (final Method method : BlogEntryRepository.class.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Query.class)) methods.add(method);
    }
    return methods;
  }

  private JsonNode parse(final Method method) throws Exception {
    final String query =
        method.getAnnotation(Query.class).value().replace("?0", "linux").replace("?1", "laffer1");
    return mapper.readTree(query);
  }

  @Test
  void hasQueries() {
    assertFalse(queryMethods().isEmpty());
  }

  @Test
  void queriesAreValidJson() throws Exception {
    for (final Method method : queryMethods()) {
      final JsonNode node = parse(method);
      assertTrue(node.has("bool"), method.getName() + " should be a bool query");
    }
  }

  @Test
  void mustClausesAreObjects() throws Exception {
    for (final Method method : queryMethods()) {
      final JsonNode must = parse(method).path("bool").path("must");
      assertTrue(must.isArray(), method.getName() + " must should be an array");
      for (final JsonNode clause : must) {
        assertTrue(clause.isObject(), method.getName() + " must clause should be an object");
      }
    }
  }

  @Test
  void publicSearchFiltersPrivateEntries() throws Exception {
    final JsonNode must =
        parse(BlogEntryRepository.class.getMethod(
                "findByPublicSearch", String.class, org.springframework.data.domain.Pageable.class))
            .path("bool")
            .path("must");
    boolean found = false;
    for (final JsonNode clause : must) {
      if ("false".equals(clause.path("match").path("privateEntry").path("query").asText()))
        found = true;
    }
    assertTrue(found, "public search must exclude private entries");
  }
}
