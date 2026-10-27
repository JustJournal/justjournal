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
package com.justjournal.ctl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.ctl.api.assembler.BlogEntrySearchResourceAssembler;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.search.BlogEntry;
import com.justjournal.services.BlogSearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
class SearchControllerTests {

  @Mock private BlogSearchService blogSearchService;

  private SearchController searchController;
  private PagedResourcesAssembler<BlogEntry> assembler;
  private final Pageable page = PageRequest.of(0, 20);

  @BeforeEach
  void setUp() {
    searchController =
        new SearchController(new BlogEntrySearchResourceAssembler(), blogSearchService);
    assembler = new PagedResourcesAssembler<>(new HateoasPageableHandlerMethodArgumentResolver(), null);
  }

  private Page<BlogEntry> results() {
    final BlogEntry entry = new BlogEntry();
    entry.setId(1);
    entry.setAuthor("technews");
    entry.setPrivateEntry(false);
    entry.setSubject("linux news");
    entry.setBody("body");
    return new PageImpl<>(List.of(entry), page, 1);
  }

  @Test
  void siteSearch() {
    // clients asking for a different size or order still get 20 newest first
    final Pageable requested = PageRequest.of(0, 100, Sort.by("subject"));
    when(blogSearchService.publicSearch("linux", SearchController.sitePage(requested)))
        .thenReturn(results());

    final ResponseEntity<PagedModel<BlogEntry>> response =
        searchController.search("linux", requested, assembler);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getContent().size());
    assertEquals(1, response.getBody().getMetadata().getTotalElements());
    assertNotNull(response.getBody().getLink("self").orElse(null));
  }

  @Test
  void siteSearchFailureReturnsBadRequest() {
    when(blogSearchService.publicSearch(anyString(), any(Pageable.class)))
        .thenThrow(new IllegalStateException("[x_content_parse_exception]"));

    final ResponseEntity<PagedModel<BlogEntry>> response =
        searchController.search("linux", page, assembler);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
  }

  @Test
  void userSearch() {
    when(blogSearchService.publicSearch("news", "technews", page)).thenReturn(results());

    final ResponseEntity<PagedModel<BlogEntry>> response =
        searchController.search("technews", "news", page, assembler);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getContent().size());
  }

  @Test
  void userSearchInvalidUser() {
    assertThrows(
        NotFoundException.class, () -> searchController.search("bad user!", "news", page, assembler));
    verify(blogSearchService, never()).publicSearch(anyString(), anyString(), any(Pageable.class));
  }

  @Test
  void sitePageIsTwentyNewestFirst() {
    final Pageable result = SearchController.sitePage(PageRequest.of(3, 5, Sort.by("subject")));

    assertEquals(3, result.getPageNumber());
    assertEquals(SearchController.SITE_PAGE_SIZE, result.getPageSize());
    assertEquals(Sort.by(Sort.Direction.DESC, "date"), result.getSort());
  }

  @Test
  void sitePageStopsAtResultWindow() {
    final Pageable result = SearchController.sitePage(PageRequest.of(100_000, 20));

    assertEquals(SearchController.SITE_MAX_PAGE, result.getPageNumber());
    assertTrue(
        (result.getPageNumber() + 1) * result.getPageSize() <= 10_000,
        "last page must stay inside elasticsearch's result window");
  }
}
