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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.Security;
import com.justjournal.model.Tag;
import com.justjournal.model.User;
import com.justjournal.model.search.BlogEntry;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.search.BlogEntryRepository;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Blog search converts entries into search documents and delegates lookups to the index. */
class BlogSearchServiceTests {

  private BlogEntryRepository blogEntryRepository;
  private EntryRepository entryRepository;
  private BlogSearchService service;
  private User author;

  @BeforeEach
  void setUp() {
    blogEntryRepository = mock(BlogEntryRepository.class);
    entryRepository = mock(EntryRepository.class);
    service = new BlogSearchService(blogEntryRepository, entryRepository);
    author = new User();
    author.setId(1);
    author.setUsername("ada");
  }

  private Entry entry(final int id, final Security security, final Date date, final String... tags) {
    final Entry entry = new Entry();
    entry.setId(id);
    entry.setUser(author);
    entry.setSecurity(security);
    entry.setSubject("Subject " + id);
    entry.setBody("Body " + id);
    entry.setDate(date);
    final Set<EntryTag> entryTags = new LinkedHashSet<>();
    for (final String name : tags) {
      final EntryTag entryTag = new EntryTag();
      entryTag.setTag(new Tag(name));
      entryTags.add(entryTag);
    }
    entry.setTags(entryTags);
    return entry;
  }

  /** Serves the given entries as page 0 and an empty page for anything after it. */
  private static Page<Entry> page(final List<Entry> entries, final Pageable pageable) {
    if (pageable.getPageNumber() == 0) return new PageImpl<>(entries, pageable, entries.size());
    return new PageImpl<>(List.of(), pageable, entries.size());
  }

  @SuppressWarnings("unchecked")
  private List<BlogEntry> savedEntries(final int times) {
    final ArgumentCaptor<Iterable<BlogEntry>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(blogEntryRepository, times(times)).saveAll(captor.capture());
    final List<BlogEntry> saved = new ArrayList<>();
    captor.getAllValues().forEach(batch -> batch.forEach(saved::add));
    return saved;
  }

  @Test
  void searches_delegateToTheIndex() {
    final Pageable page = PageRequest.of(0, 10);
    final Page<BlogEntry> any = new PageImpl<>(List.of(new BlogEntry()));
    final Page<BlogEntry> pub = new PageImpl<>(List.of());
    final Page<BlogEntry> mine = new PageImpl<>(List.of(new BlogEntry(), new BlogEntry()));
    final Page<BlogEntry> minePublic = new PageImpl<>(List.of(new BlogEntry()));
    when(blogEntryRepository.findBySubjectContainsOrBodyContainsAllIgnoreCase("cats", "cats", page))
        .thenReturn(any);
    when(blogEntryRepository.findByPublicSearch("cats", page)).thenReturn(pub);
    when(blogEntryRepository.findBySearchAndAuthor("cats", "ada", page)).thenReturn(mine);
    when(blogEntryRepository.findByPublicSearchAndAuthor("cats", "ada", page)).thenReturn(minePublic);

    assertSame(any, service.search("cats", page));
    assertSame(pub, service.publicSearch("cats", page));
    assertSame(mine, service.search("cats", "ada", page));
    assertSame(minePublic, service.publicSearch("cats", "ada", page));
  }

  @Test
  void convert_copiesTheEntryAndDeduplicatesTags() {
    final Date date = new Date(1_700_000_000_000L);
    final Entry entry = entry(5, Security.FRIENDS, date, "java", "Java", "java", "bsd"); // Tag lower-cases names

    final BlogEntry blogEntry = service.convert(entry);

    assertEquals(5, blogEntry.getId());
    assertEquals("ada", blogEntry.getAuthor());
    assertEquals("Subject 5", blogEntry.getSubject());
    assertEquals("Body 5", blogEntry.getBody());
    assertEquals(date, blogEntry.getDate());
    assertTrue(blogEntry.getPrivateEntry());
    final List<String> tags =
        blogEntry.getTags().stream().map(com.justjournal.model.search.Tag::getName).sorted().collect(Collectors.toList());
    assertEquals(List.of("bsd", "java"), tags);
  }

  @Test
  void convert_marksPublicEntriesAsPublic() {
    assertFalse(service.convert(entry(1, Security.PUBLIC, new Date())).getPrivateEntry());
  }

  @Test
  void index_savesOneDocument() {
    final Entry entry = entry(9, Security.PUBLIC, new Date());

    service.index(entry);

    final ArgumentCaptor<BlogEntry> captor = ArgumentCaptor.forClass(BlogEntry.class);
    verify(blogEntryRepository).save(captor.capture());
    assertEquals(9, captor.getValue().getId());
  }

  @Test
  void indexAllBlogEntries_savesEveryPage() {
    final List<Entry> entries = List.of(entry(1, Security.PUBLIC, new Date()), entry(2, Security.PRIVATE, new Date()));
    when(entryRepository.findAll(any(Pageable.class))).thenAnswer(inv -> page(entries, inv.getArgument(0)));

    service.indexAllBlogEntries();

    final List<BlogEntry> saved = savedEntries(1);
    assertEquals(List.of(1, 2), saved.stream().map(BlogEntry::getId).collect(Collectors.toList()));
  }

  @Test
  void indexAllBlogEntries_swallowsFailures() {
    when(entryRepository.findAll(any(Pageable.class))).thenThrow(new IllegalStateException("db"));

    service.indexAllBlogEntries();

    verify(blogEntryRepository, never()).saveAll(any());
  }

  @Test
  void indexAllPublicBlogEntries_savesOnlyPublicPages() {
    final List<Entry> entries = List.of(entry(3, Security.PUBLIC, new Date()));
    when(entryRepository.findBySecurityOrderByDateDesc(eq(Security.PUBLIC), any(Pageable.class)))
        .thenAnswer(inv -> page(entries, inv.getArgument(1)));

    service.indexAllPublicBlogEntries();

    assertEquals(3, savedEntries(1).get(0).getId());
    verify(entryRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void indexAllPublicBlogEntries_swallowsFailures() {
    when(entryRepository.findBySecurityOrderByDateDesc(eq(Security.PUBLIC), any(Pageable.class)))
        .thenThrow(new IllegalStateException("db"));

    service.indexAllPublicBlogEntries();

    verify(blogEntryRepository, never()).saveAll(any());
  }

  @Test
  void indexBlogEntriesSince_stopsAtTheFirstOlderEntry() {
    final Date cutoff = new Date(1_700_000_000_000L);
    final List<Entry> entries =
        List.of(
            entry(1, Security.PUBLIC, new Date(cutoff.getTime() + 2_000)),
            entry(2, Security.PUBLIC, new Date(cutoff.getTime() + 1_000)),
            entry(3, Security.PUBLIC, new Date(cutoff.getTime() - 1_000)),
            entry(4, Security.PUBLIC, new Date(cutoff.getTime() - 2_000)));
    when(entryRepository.findAll(any(Pageable.class))).thenAnswer(inv -> page(entries, inv.getArgument(0)));

    service.indexBlogEntriesSince(cutoff);

    assertEquals(List.of(1, 2), savedEntries(1).stream().map(BlogEntry::getId).collect(Collectors.toList()));
  }

  @Test
  void indexBlogEntriesSince_savesNothingWhenEverythingIsOlder() {
    final Date cutoff = new Date(1_700_000_000_000L);
    final List<Entry> entries = List.of(entry(1, Security.PUBLIC, new Date(cutoff.getTime() - 1_000)));
    when(entryRepository.findAll(any(Pageable.class))).thenAnswer(inv -> page(entries, inv.getArgument(0)));

    service.indexBlogEntriesSince(cutoff);

    verify(blogEntryRepository, never()).saveAll(any());
  }

  @Test
  void indexBlogEntriesSince_savesWholePagesWhenAllAreNewer() {
    final Date cutoff = new Date(1_700_000_000_000L);
    final List<Entry> entries = List.of(entry(1, Security.PUBLIC, new Date(cutoff.getTime() + 1_000)));
    when(entryRepository.findAll(any(Pageable.class))).thenAnswer(inv -> page(entries, inv.getArgument(0)));

    service.indexBlogEntriesSince(cutoff);

    assertEquals(1, savedEntries(1).size());
  }

  @Test
  void indexPublicBlogEntriesSince_stopsAtTheFirstOlderEntry() {
    final Date cutoff = new Date(1_700_000_000_000L);
    final List<Entry> entries =
        List.of(
            entry(1, Security.PUBLIC, new Date(cutoff.getTime() + 1_000)),
            entry(2, Security.PUBLIC, new Date(cutoff.getTime() - 1_000)));
    when(entryRepository.findBySecurityOrderByDateDesc(eq(Security.PUBLIC), any(Pageable.class)))
        .thenAnswer(inv -> page(entries, inv.getArgument(1)));

    service.indexPublicBlogEntriesSince(cutoff);

    assertEquals(List.of(1), savedEntries(1).stream().map(BlogEntry::getId).collect(Collectors.toList()));
  }

  @Test
  void indexPublicBlogEntriesSince_savesWholePagesWhenAllAreNewer_andNothingWhenAllAreOlder() {
    final Date cutoff = new Date(1_700_000_000_000L);
    final List<Entry> newer = List.of(entry(1, Security.PUBLIC, new Date(cutoff.getTime() + 1_000)));
    when(entryRepository.findBySecurityOrderByDateDesc(eq(Security.PUBLIC), any(Pageable.class)))
        .thenAnswer(inv -> page(newer, inv.getArgument(1)));
    service.indexPublicBlogEntriesSince(cutoff);
    assertEquals(1, savedEntries(1).size());

    final List<Entry> older = List.of(entry(2, Security.PUBLIC, new Date(cutoff.getTime() - 1_000)));
    when(entryRepository.findBySecurityOrderByDateDesc(eq(Security.PUBLIC), any(Pageable.class)))
        .thenAnswer(inv -> page(older, inv.getArgument(1)));
    service.indexPublicBlogEntriesSince(cutoff);
    assertEquals(1, savedEntries(1).size());
  }
}
