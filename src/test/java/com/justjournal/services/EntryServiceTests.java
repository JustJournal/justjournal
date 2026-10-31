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

import com.justjournal.exception.ServiceException;
import com.justjournal.model.*;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryTagsRepository;
import com.justjournal.repository.TagRepository;
import com.justjournal.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Lucas Holt
 */
@ExtendWith(MockitoExtension.class)
class EntryServiceTests {

    private static final String TEST_USER = "testuser";
    private static final int PUBLIC_ENTRY_ID = 33661;

    @Mock
    private EntryRepository entryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TagRepository tagDao;
    @Mock
    private EntryTagsRepository entryTagsRepository;


    @InjectMocks
    private EntryService entryService;

    @Test
    void entryGetPublicEntry() throws ServiceException {
        User user = new User();
        user.setUsername(TEST_USER);
        user.setId(2);
        Entry entry = new Entry(PUBLIC_ENTRY_ID, "Test Entry");
        entry.setSecurity(Security.PUBLIC);
        entry.setDraft(PrefBool.N);
        entry.setUser(user);

        when(entryRepository.findById(any())).thenReturn(java.util.Optional.of(entry));
        final Entry result = entryService.getPublicEntry(PUBLIC_ENTRY_ID, TEST_USER);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(PUBLIC_ENTRY_ID, result.getId());
        Assertions.assertEquals(TEST_USER, result.getUser().getUsername());
        Assertions.assertEquals(Security.PUBLIC, result.getSecurity());
    }

    @Test
    void entryGetRecentEntriesPublic() throws ServiceException {
        when(entryRepository.findByUserAndSecurityAndDraftWithSubjectOnly(any(), any(), any(), any()))
                .thenReturn(new PageImpl<Entry>(List.of(new Entry(1, "Test Entry 1"), new Entry(2, "Test Entry 2"))));
        final List<RecentEntry> entryList =
                entryService.getRecentEntriesPublic(TEST_USER).collect(Collectors.toList()).block();
        Assertions.assertNotNull(entryList);
        Assertions.assertTrue(entryList.size() > 1);
        final RecentEntry re = entryList.get(0);
        Assertions.assertNotNull(re);
    }

    @Test
    void entryGetRecentEntries() throws ServiceException {
        User user = new User();
        user.setUsername(TEST_USER);
        user.setId(2);
        when(userRepository.findByUsername(any())).thenReturn(user);
        when(entryRepository.findByUserOrderByDateDesc(any(), any()))
                .thenReturn(new PageImpl<Entry>(List.of(new Entry(1, "Test Entry 1"), new Entry(2, "Test Entry 2"))));

        final List<RecentEntry> entryList =
                entryService.getRecentEntries(TEST_USER).collectList().block();
        Assertions.assertNotNull(entryList);
        Assertions.assertTrue(entryList.size() >1);
        final RecentEntry re = entryList.get(0);
        Assertions.assertNotNull(re);
    }

    @Test
    void entryGetPublicEntries() throws ServiceException {
        User user = new User();
        user.setUsername(TEST_USER);
        user.setId(2);
        when(userRepository.findByUsername(TEST_USER)).thenReturn(user);
        when(entryRepository.findByUserAndSecurityAndDraftOrderByDateDesc(any(), any(), any()))
                .thenReturn(List.of(new Entry(1, "Test Entry 1"), new Entry(2, "Test Entry 2")));

        final List<Entry> entryList = entryService.getPublicEntries(TEST_USER);
        Assertions.assertNotNull(entryList);
        Assertions.assertFalse(entryList.isEmpty());
    }

    @Test
    void getPrivateEntryShouldThrowException() {
        Entry entry = new Entry(1, "Private Entry");
        entry.setSecurity(Security.PRIVATE);
        when(entryRepository.findById(1)).thenReturn(Optional.of(entry));

        assertThrows(ServiceException.class, () -> {
            entryService.getPublicEntry(1, "differentUser");
        });
    }

    @Test
    void getNonExistentEntryShouldReturnNull() throws ServiceException {
        when(entryRepository.findById(999)).thenReturn(Optional.empty());

        Entry result = entryService.getPublicEntry(999, TEST_USER);
        Assertions.assertNull(result);
    }

    @Test
    void addTagToEntryWhenTagDoesNotExist() {
        Entry entry = new Entry(1, "Test Entry");
        String tagName = "NewTag";
        Tag newTag = new Tag(tagName);

        when(tagDao.findByName(tagName)).thenReturn(null);
        when(tagDao.save(any(Tag.class))).thenReturn(newTag);
        when(entryTagsRepository.findByEntryAndTag(entry, newTag)).thenReturn(null);

        entryService.addTagToEntry(entry, tagName);

        Mockito.verify(tagDao).save(any(Tag.class));
        Mockito.verify(entryTagsRepository).save(any(EntryTag.class));
    }

    @Test
    void addTagToEntryWhenTagAlreadyExists() {
        Entry entry = new Entry(1, "Test Entry");
        String tagName = "ExistingTag";
        Tag existingTag = new Tag(tagName);
        EntryTag existingEntryTag = new EntryTag();
        existingEntryTag.setTag(existingTag);
        existingEntryTag.setEntry(entry);

        when(tagDao.findByName(tagName)).thenReturn(existingTag);
        when(entryTagsRepository.findByEntryAndTag(entry, existingTag)).thenReturn(existingEntryTag);

        entryService.addTagToEntry(entry, tagName);

        Mockito.verify(tagDao, never()).save(any(Tag.class));
        Mockito.verify(entryTagsRepository, never()).save(any(EntryTag.class));
    }

    @Test
    void addTagToEntryWhenTagExistsButNotAssociatedWithEntry() {
        Entry entry = new Entry(1, "Test Entry");
        String tagName = "ExistingTag";
        Tag existingTag = new Tag(tagName);

        when(tagDao.findByName(tagName)).thenReturn(existingTag);
        when(entryTagsRepository.findByEntryAndTag(entry, existingTag)).thenReturn(null);

        entryService.addTagToEntry(entry, tagName);

        Mockito.verify(tagDao, never()).save(any(Tag.class));
        Mockito.verify(entryTagsRepository).save(any(EntryTag.class));
    }

    @Test
    void addTagToEntryWithNullEntryShouldSave() {
        String tagName = "TestTag";

        Assertions.assertDoesNotThrow(() -> {
            entryService.addTagToEntry(null, tagName);
        });

        Mockito.verify(tagDao, times(1)).findByName(any());
        Mockito.verify(tagDao, times(1)).save(any());
        Mockito.verify(entryTagsRepository, times(1)).findByEntryAndTag(any(), any());
        Mockito.verify(entryTagsRepository, times(1)).save(any());
    }

    @Test
    void addTagToEntryWithEmptyStringTag() {
        Entry entry = new Entry(1, "Test Entry");
        String emptyTag = "";

        when(tagDao.findByName(emptyTag)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> {
            entryService.addTagToEntry(entry, emptyTag);
        });

        verify(tagDao).findByName(emptyTag);
        verify(tagDao, never()).save(any());
        verify(entryTagsRepository, never()).save(any());
    }

    @Test
    void applyTagsIgnoresNullTags() {
        entryService.applyTags(new Entry(), null);
        verifyNoInteractions(entryTagsRepository, tagDao);
    }

    @Test
    void addTagToEntryIgnoresStopWords() {
        entryService.addTagToEntry(new Entry(1, "Test Entry"), "The");
        verifyNoInteractions(entryTagsRepository, tagDao);
    }

    @Test
    void applyTagsDropsStopWordsAndRemovesExistingOnes() {
        Entry entry = new Entry(1, "Test Entry");
        Tag to = new Tag("to");
        to.setId(1);
        EntryTag existing = new EntryTag();
        existing.setTag(to);
        existing.setEntry(entry);
        Tag java = new Tag("java");
        java.setId(2);

        when(entryTagsRepository.findByEntry(entry)).thenReturn(List.of(existing));
        when(tagDao.findByName("java")).thenReturn(java);
        when(tagDao.findByName("to")).thenReturn(to);
        when(entryTagsRepository.findByEntryAndTag(entry, java)).thenReturn(null);
        when(entryTagsRepository.findByEntryAndTag(entry, to)).thenReturn(existing);

        entryService.applyTags(entry, Set.of("java", "to", "and"));

        verify(tagDao, never()).findByName("and");
        verify(entryTagsRepository).save(argThat(et -> et.getTag() == java));
        verify(entryTagsRepository).delete(existing);
    }

    private User user(final String username) {
        final User user = new User();
        user.setId(7);
        user.setUsername(username);
        when(userRepository.findByUsername(username)).thenReturn(user);
        return user;
    }

    @Test
    void getRecentEntries_unknownUserIsNull_andFailuresAreWrapped() throws ServiceException {
        when(userRepository.findByUsername("nobody")).thenReturn(null);
        Assertions.assertNull(entryService.getRecentEntries("nobody"));

        final User user = user(TEST_USER);
        when(entryRepository.findByUserOrderByDateDesc(eq(user), any(Pageable.class)))
                .thenThrow(new IllegalStateException("db"));
        assertThrows(ServiceException.class, () -> entryService.getRecentEntries(TEST_USER));
    }

    @Test
    void getRecentEntriesPublic_wrapsFailures() {
        when(entryRepository.findByUserAndSecurityAndDraftWithSubjectOnly(
                        eq(TEST_USER), eq(Security.PUBLIC), eq(PrefBool.N), any(Pageable.class)))
                .thenThrow(new IllegalStateException("db"));

        assertThrows(ServiceException.class, () -> entryService.getRecentEntriesPublic(TEST_USER));
    }

    @Test
    void getPublicEntries_pagedAndListed_forKnownAndUnknownUsers() throws ServiceException {
        final User user = user(TEST_USER);
        final Pageable page = PageRequest.of(0, 10);
        final Page<Entry> paged = new PageImpl<>(List.of(new Entry()));
        when(entryRepository.findByUserAndSecurityAndDraft(user, Security.PUBLIC, PrefBool.N, page)).thenReturn(paged);
        when(userRepository.findByUsername("nobody")).thenReturn(null);

        Assertions.assertSame(paged, entryService.getPublicEntries(TEST_USER, page));
        Assertions.assertNull(entryService.getPublicEntries("nobody", page));
        Assertions.assertTrue(entryService.getPublicEntries("nobody").isEmpty());
    }

    @Test
    void getEntries_returnsEveryEntryForTheUser_orNullForUnknown() throws ServiceException {
        final User user = user(TEST_USER);
        final Pageable page = PageRequest.of(1, 5);
        final Page<Entry> paged = new PageImpl<>(List.of(new Entry(), new Entry()));
        when(entryRepository.findByUserOrderByDateDesc(user, page)).thenReturn(paged);
        when(userRepository.findByUsername("nobody")).thenReturn(null);

        Assertions.assertSame(paged, entryService.getEntries(TEST_USER, page));
        Assertions.assertNull(entryService.getEntries("nobody", page));
    }

    @Test
    void entryLookups_wrapRepositoryFailures() {
        when(userRepository.findByUsername(TEST_USER)).thenThrow(new IllegalStateException("db"));
        final Pageable page = PageRequest.of(0, 5);

        assertThrows(ServiceException.class, () -> entryService.getPublicEntries(TEST_USER));
        assertThrows(ServiceException.class, () -> entryService.getPublicEntries(TEST_USER, page));
        assertThrows(ServiceException.class, () -> entryService.getEntries(TEST_USER, page));
    }

    @Test
    void getPublicEntry_skipsDrafts_andWrapsFailures() throws ServiceException {
        final User user = new User();
        user.setUsername(TEST_USER);
        final Entry draft = new Entry();
        draft.setId(5);
        draft.setUser(user);
        draft.setSecurity(Security.PUBLIC);
        draft.setDraft(PrefBool.Y);
        when(entryRepository.findById(5)).thenReturn(Optional.of(draft));
        when(entryRepository.findById(6)).thenThrow(new IllegalStateException("db"));

        Assertions.assertNull(entryService.getPublicEntry(5, TEST_USER));
        assertThrows(ServiceException.class, () -> entryService.getPublicEntry(6, TEST_USER));
    }

    @Test
    void getFriendsEntries_returnsThePageContent_orWrapsFailures() throws ServiceException {
        final List<Entry> entries = List.of(new Entry());
        when(entryRepository.findByUserFriends(eq(TEST_USER), eq(PrefBool.N), any(Pageable.class)))
                .thenReturn(new PageImpl<>(entries));
        when(entryRepository.findByUserFriends(eq("broken"), eq(PrefBool.N), any(Pageable.class)))
                .thenThrow(new IllegalStateException("db"));

        Assertions.assertEquals(entries, entryService.getFriendsEntries(TEST_USER));
        assertThrows(ServiceException.class, () -> entryService.getFriendsEntries("broken"));
    }

    @Test
    void getEntryTags_countsTheEntriesCarryingEachTag_andSortsByCount() throws ServiceException {
        // one row per entry carrying the tag; a count already on a row must not leak into the total
        final Tag java = new Tag("java");
        java.setCount(4);
        final Tag javaAgain = new Tag("java");
        javaAgain.setCount(4);
        final Tag javaOnceMore = new Tag("java");
        javaOnceMore.setCount(4);
        when(tagDao.findByUsername(TEST_USER))
                .thenReturn(List.of(java, new Tag("bsd"), javaAgain, javaOnceMore));
        when(tagDao.findByUsername("broken")).thenThrow(new IllegalStateException("db"));

        final List<Tag> tags = entryService.getEntryTags(TEST_USER).collectList().block();

        Assertions.assertEquals(List.of("bsd", "java"), tags.stream().map(Tag::getName).toList());
        Assertions.assertEquals(1, tags.get(0).getCount());
        Assertions.assertEquals(3, tags.get(1).getCount());
        assertThrows(ServiceException.class, () -> entryService.getEntryTags("broken"));
    }

    @Test
    void removeTagFromEntry_deletesTheLinkOnlyWhenPresent() {
        final Entry entry = new Entry();
        final Tag java = new Tag("java");
        final EntryTag link = new EntryTag();
        when(tagDao.findByName("java")).thenReturn(java);
        when(entryTagsRepository.findByEntryAndTag(same(entry), same(java))).thenReturn(link);
        final Tag loose = new Tag("loose");
        when(tagDao.findByName("loose")).thenReturn(loose);
        when(entryTagsRepository.findByEntryAndTag(same(entry), same(loose))).thenReturn(null);
        when(tagDao.findByName("none")).thenReturn(null);

        entryService.removeTagFromEntry(entry, "java");
        entryService.removeTagFromEntry(entry, "loose");
        entryService.removeTagFromEntry(entry, "none");

        verify(entryTagsRepository).delete(link);
        verify(entryTagsRepository, times(1)).delete(any());
    }

    @Test
    void convertBody_dependsOnTheFormat() {
        final MarkdownService markdownService = mock(MarkdownService.class);
        ReflectionTestUtils.setField(entryService, "markdownService", markdownService);
        when(markdownService.convertToText("*md*")).thenReturn("md");

        Assertions.assertEquals("md", entryService.convertBody(FormatType.MARKDOWN, "*md*"));
        final String text = entryService.convertBody(FormatType.HTML, "<b>bold</b> text");
        Assertions.assertTrue(text.contains("bold"), text);
        Assertions.assertFalse(text.contains("<b>"), text);
        Assertions.assertEquals("plain", entryService.convertBody(FormatType.TEXT, "plain"));

        Assertions.assertEquals("md", entryService.convertBodyToHtml(FormatType.MARKDOWN, "*md*"));
        Assertions.assertEquals("a<br/>b", entryService.convertBodyToHtml(FormatType.TEXT, "a\nb"));
        Assertions.assertEquals("<p>x</p>", entryService.convertBodyToHtml(FormatType.HTML, "<p>x</p>"));
    }
}
