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

import com.justjournal.model.Entry;
import com.justjournal.model.search.BlogEntry;
import com.justjournal.model.search.Tag;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.search.BlogEntryRepository;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.justjournal.model.Security.PUBLIC;

/**
 * Blog entry search services
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class BlogSearchService {

  private final BlogEntryRepository blogEntryRepository;

  private final EntryRepository entryRepository;


  @Autowired
  public BlogSearchService(
      final BlogEntryRepository blogEntryRepository,
      final EntryRepository entryRepository) {
    this.blogEntryRepository = blogEntryRepository;
    this.entryRepository = entryRepository;
  }

  /**
   * Find all blog entries mentioning a specific term
   *
   * @param term search term
   * @param page page
   * @return a page of results
   */
  public Page<BlogEntry> search(final String term, final Pageable page) {
    return blogEntryRepository.findBySubjectContainsOrBodyContainsAllIgnoreCase(term, term, page);
  }

  /**
   * Find all public blog entries matching a specific term.
   *
   * @param term search term
   * @param page page
   * @return a page of results
   */
  public Page<BlogEntry> publicSearch(final String term, final Pageable page) {
    return blogEntryRepository.findByPublicSearch(term, page);
  }

  /**
   * Find all blog entries for a specific user
   *
   * @param term search term
   * @param username user to filter on
   * @param page page
   * @return a page of results
   */
  public Page<BlogEntry> search(final String term, final String username, final Pageable page) {
    return blogEntryRepository.findBySearchAndAuthor(term, username, page);
  }

  /**
   * Find all blog entries for a specific user
   *
   * @param term search term
   * @param username user to filter on
   * @param page page
   * @return a page of results
   */
  public Page<BlogEntry> publicSearch(
    final String term, final String username, final Pageable page) {
    return blogEntryRepository.findByPublicSearchAndAuthor(term, username, page);
  }

  /** Index all blog entries regardless of security level. */
  @Async
  public void indexAllBlogEntries() {
    try {
      Pageable pageable = PageRequest.of(0, 100);

      Page<Entry> entries = entryRepository.findAll(pageable);
      for (int i = 0; i < entries.getTotalPages(); i++) {
        final ArrayList<BlogEntry> items = new ArrayList<>();

        for (final Entry entry : entries) {
          items.add(convert(entry));
        }

        blogEntryRepository.saveAll(items);

        pageable = PageRequest.of(i + 1, 100);
        entries = entryRepository.findAll(pageable);
      }
    } catch (final Exception e) {
      log.error(e.getMessage());
    }
  }

  /** Index all public blog entries. */
  @Async
  public void indexAllPublicBlogEntries() {
    try {
      // to not use all the ram, I loop through a page at a time and save the blog
      // entries in a batch of 100 at a time to ES
      // since I can't use java 8 on this project, I don't have streams.

      Pageable pageable = PageRequest.of(0, 100);

      Page<Entry> entries = entryRepository.findBySecurityOrderByDateDesc(PUBLIC, pageable);
      for (int i = 0; i < entries.getTotalPages(); i++) {
        final ArrayList<BlogEntry> items = new ArrayList<>();

        for (final Entry entry : entries) {
          items.add(convert(entry));
        }

        blogEntryRepository.saveAll(items);

        pageable = PageRequest.of(i + 1, 100);
        entries = entryRepository.findBySecurityOrderByDateDesc(PUBLIC, pageable);
      }
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  /**
   * Index public blog entries since a specific date
   *
   * @param date newer blog entries
   */
  @Async
  public void indexBlogEntriesSince(final Date date) {
    Pageable pageable = PageRequest.of(0, 100);

    Page<Entry> entries = entryRepository.findAll(pageable);
    for (int i = 0; i < entries.getTotalPages(); i++) {
      final ArrayList<BlogEntry> items = new ArrayList<>();
      for (final Entry entry : entries) {
        if (entry.getDate().before(date)) {
          if (!items.isEmpty()) blogEntryRepository.saveAll(items);
          // stop processing items.
          return;
        }

        items.add(convert(entry));
      }

      blogEntryRepository.saveAll(items);

      pageable = PageRequest.of(i + 1, 100);
      entries = entryRepository.findAll(pageable);
    }
  }

  /**
   * Index public blog entries since a specific date
   *
   * @param date newer blog entries
   */
  @Async
  public void indexPublicBlogEntriesSince(final Date date) {
    Pageable pageable = PageRequest.of(0, 100);

    Page<Entry> entries = entryRepository.findBySecurityOrderByDateDesc(PUBLIC, pageable);
    for (int i = 0; i < entries.getTotalPages(); i++) {
      ArrayList<BlogEntry> items = new ArrayList<>();
      for (final Entry entry : entries) {
        if (entry.getDate().before(date)) {
          if (!items.isEmpty()) blogEntryRepository.saveAll(items);
          // stop processing items.
          return;
        }

        items.add(convert(entry));
      }

      blogEntryRepository.saveAll(items);

      pageable = PageRequest.of(i + 1, 100);
      entries = entryRepository.findBySecurityOrderByDateDesc(PUBLIC, pageable);
    }
  }

  /**
   * Index a single entry
   *
   * @param entry blog entry to index
   */
  public void index(@NonNull final Entry entry) {
    this.blogEntryRepository.save(convert(entry));
  }

  /**
   * Convert an entry into a blog entry (search indexed document)
   *
   * @param entry entry domain object
   * @return blog entry for ES
   */
  public BlogEntry convert(@NonNull final Entry entry) {
    final BlogEntry blogEntry = new BlogEntry();
    blogEntry.setAuthor(entry.getUser().getUsername());
    blogEntry.setId(entry.getId());
    blogEntry.setPrivateEntry(entry.getSecurity() != PUBLIC);
    blogEntry.setSubject(entry.getSubject());
    blogEntry.setBody(entry.getBody());
    blogEntry.setDate(entry.getDate());

    final HashMap<String, Object> tags = new HashMap<>();
    for (final com.justjournal.model.EntryTag tag : entry.getTags()) {
      final String tagName = tag.getTag().getName();
      if (!tags.containsKey(tagName)) tags.put(tagName, null);
    }

    final List<Tag> targetList = new ArrayList<>();
    for (final String t : tags.keySet()) {
      final Tag tag = new Tag();
      tag.setName(t);
      targetList.add(tag);
    }
    blogEntry.setTags(targetList);

    return blogEntry;
  }
}
