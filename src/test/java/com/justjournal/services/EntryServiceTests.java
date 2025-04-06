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
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
}
