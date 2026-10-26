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
package com.justjournal.repository.search;


import com.justjournal.model.search.BlogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.repository.query.Param;

/** @author Lucas Holt */
public interface BlogEntryRepository extends ElasticsearchRepository<BlogEntry, Integer> {

  Page<BlogEntry> findBySubjectContainsOrBodyContainsAllIgnoreCase(
      @Param("subject") String subject, @Param("body") String body, Pageable pageable);

  Page<BlogEntry> findByAuthor(@Param("author") String author, Pageable pageable);

  Page<BlogEntry> findByAuthorAndSubjectContainsOrBodyContainsAllIgnoreCase(
      @Param("author") String author,
      @Param("subject") String subject,
      @Param("body") String body,
      Pageable pageable);

  @Query("{\"bool\" : { \"must\" : [ { \"match\" : { \"privateEntry\" : { \"query\" : \"false\" } } }," +
          "      { \"multi_match\" : {" +
          "          \"query\" : \"?0\"," +
          "          \"fields\" : [ \"body\", \"subject\" ]," +
          "          \"type\" : \"best_fields\", \"operator\" : \"OR\" } } ] } }")
  Page<BlogEntry> findByPublicSearch(@Param("term") String term, Pageable pageable);

  @Query("{\n" +
          "  \"bool\" : {\n" +
          "    \"must\" : [\n" +
          "      {\n" +
          "        \"match\" : {\n" +
          "          \"privateEntry\" : {\n" +
          "            \"query\" : \"false\",\n" +
          "            \"operator\" : \"OR\",\n" +
          "            \"prefix_length\" : 0,\n" +
          "            \"max_expansions\" : 50,\n" +
          "            \"fuzzy_transpositions\" : true,\n" +
          "            \"lenient\" : false,\n" +
          "            \"zero_terms_query\" : \"NONE\",\n" +
          "            \"auto_generate_synonyms_phrase_query\" : true,\n" +
          "            \"boost\" : 1.0\n" +
          "          }\n" +
          "        }\n" +
          "      },\n" +
          "      {\n" +
          "        \"match\" : {\n" +
          "          \"author\" : {\n" +
          "            \"query\" : \"?1\",\n" +
          "            \"operator\" : \"OR\",\n" +
          "            \"prefix_length\" : 0,\n" +
          "            \"max_expansions\" : 50,\n" +
          "            \"fuzzy_transpositions\" : true,\n" +
          "            \"lenient\" : false,\n" +
          "            \"zero_terms_query\" : \"NONE\",\n" +
          "            \"auto_generate_synonyms_phrase_query\" : true,\n" +
          "            \"boost\" : 1.0\n" +
          "          }\n" +
          "        }\n" +
          "      },\n" +
          "      {\n" +
          "        \"multi_match\" : {\n" +
          "          \"query\" : \"?0\",\n" +
          "          \"fields\" : [\n" +
          "            \"body^1.0\",\n" +
          "            \"subject^1.0\"\n" +
          "          ],\n" +
          "          \"type\" : \"best_fields\",\n" +
          "          \"operator\" : \"OR\",\n" +
          "          \"slop\" : 0,\n" +
          "          \"prefix_length\" : 0,\n" +
          "          \"max_expansions\" : 50,\n" +
          "          \"zero_terms_query\" : \"NONE\",\n" +
          "          \"auto_generate_synonyms_phrase_query\" : true,\n" +
          "          \"fuzzy_transpositions\" : true,\n" +
          "          \"boost\" : 1.0\n" +
          "        }\n" +
          "      }\n" +
          "    ],\n" +
          "    \"adjust_pure_negative\" : true,\n" +
          "    \"boost\" : 1.0\n" +
          "  }\n" +
          "}\n")
  Page<BlogEntry> findByPublicSearchAndAuthor(@Param("term") String term, @Param("author") String author, Pageable pageable);

  /**
   * Search by author, can include private entries.
   * @param term
   * @param author
   * @param pageable
   * @return
   */
  @Query("{\n" +
          "  \"bool\" : {\n" +
          "    \"must\" : [\n" +
          "      {\n" +
          "        \"match\" : {\n" +
          "          \"author\" : {\n" +
          "            \"query\" : \"?1\",\n" +
          "            \"operator\" : \"OR\",\n" +
          "            \"prefix_length\" : 0,\n" +
          "            \"max_expansions\" : 50,\n" +
          "            \"fuzzy_transpositions\" : true,\n" +
          "            \"lenient\" : false,\n" +
          "            \"zero_terms_query\" : \"NONE\",\n" +
          "            \"auto_generate_synonyms_phrase_query\" : true,\n" +
          "            \"boost\" : 1.0\n" +
          "          }\n" +
          "        }\n" +
          "      },\n" +
          "      {\n" +
          "        \"multi_match\" : {\n" +
          "          \"query\" : \"?0\",\n" +
          "          \"fields\" : [\n" +
          "            \"body^1.0\",\n" +
          "            \"subject^1.0\"\n" +
          "          ],\n" +
          "          \"type\" : \"best_fields\",\n" +
          "          \"operator\" : \"OR\",\n" +
          "          \"slop\" : 0,\n" +
          "          \"prefix_length\" : 0,\n" +
          "          \"max_expansions\" : 50,\n" +
          "          \"zero_terms_query\" : \"NONE\",\n" +
          "          \"auto_generate_synonyms_phrase_query\" : true,\n" +
          "          \"fuzzy_transpositions\" : true,\n" +
          "          \"boost\" : 1.0\n" +
          "        }\n" +
          "      }\n" +
          "    ],\n" +
          "    \"adjust_pure_negative\" : true,\n" +
          "    \"boost\" : 1.0\n" +
          "  }\n" +
          "}")
  Page<BlogEntry> findBySearchAndAuthor(@Param("term") String term, @Param("author") String author, Pageable pageable);
}
