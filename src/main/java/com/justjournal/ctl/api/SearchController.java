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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.PARAM_USERNAME;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.justjournal.Login;
import com.justjournal.ctl.api.assembler.BlogEntrySearchResourceAssembler;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.search.BlogEntry;
import com.justjournal.services.BlogSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** @author Lucas Holt */
@Slf4j
@RestController
@RequestMapping("/api/search")
public class SearchController {

  private final BlogEntrySearchResourceAssembler blogEntrySearchResourceAssembler;

  private final BlogSearchService blogSearchService;

  @Autowired
  public SearchController(
      final BlogEntrySearchResourceAssembler blogEntrySearchResourceAssembler,
      final BlogSearchService blogSearchService) {
    this.blogEntrySearchResourceAssembler = blogEntrySearchResourceAssembler;
    this.blogSearchService = blogSearchService;
  }

  @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PagedModel<BlogEntry>> search(
      @RequestParam("term") final String term,
      final Pageable page,
      final PagedResourcesAssembler<BlogEntry> assembler) {

    try {
      final Page<BlogEntry> entries = blogSearchService.publicSearch(term, page);

      final Link link =
          linkTo(methodOn(SearchController.class).search(term, page, assembler)).withSelfRel();

      final PagedModel<EntityModel<BlogEntry>> resources =
          assembler.toModel(entries, blogEntrySearchResourceAssembler, link);
      return new ResponseEntity(resources, HttpStatus.OK);
    } catch (final Exception e) {
      log.error(e.getMessage());
    }

    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
  }

  @GetMapping(value = "/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PagedModel<BlogEntry>> search(
      @PathVariable(PARAM_USERNAME) final String username,
      @RequestParam("term") final String term,
      final Pageable page,
      final PagedResourcesAssembler<BlogEntry> assembler) {

    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    try {
      final Page<BlogEntry> entries = blogSearchService.publicSearch(term, username, page);

      final Link link =
          linkTo(methodOn(SearchController.class).search(username, term, page, assembler))
              .withSelfRel();

      final PagedModel<EntityModel<BlogEntry>> resources =
          assembler.toModel(entries, blogEntrySearchResourceAssembler, link);
      return new ResponseEntity(resources, HttpStatus.OK);
    } catch (final Exception e) {
      log.error(e.getMessage());
    }

    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
  }
}
