/*
 * Copyright (c) 2014 Lucas Holt
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

import com.justjournal.Application;
import com.justjournal.model.Entry;
import com.justjournal.model.RecentEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Lucas Holt
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
public class EntryServiceTests {

    private static final String TEST_USER = "testuser";
    private static final int PUBLIC_ENTRY_ID = 33661;

    @Autowired
    private EntryService entryService;

    @Test
    public void entryGetPublicEntry() throws ServiceException {
        final Entry entry = entryService.getPublicEntry(PUBLIC_ENTRY_ID, TEST_USER);
        assertNotNull(entry);
        assertEquals(PUBLIC_ENTRY_ID, entry.getId());
        assertEquals(TEST_USER, entry.getUser().getUsername());
        assertEquals(2, entry.getSecurity().getId());
    }

    @Test
    public void entryGetRecentEntriesPublic() throws ServiceException {
        final List<RecentEntry> entryList = entryService.getRecentEntriesPublic(TEST_USER).toList().blockingGet();
        assertNotNull(entryList);
        assertTrue(entryList.size() > 2);
        final RecentEntry re = entryList.get(0);
        assertNotNull(re);
        assertEquals(33662, re.getId());
    }

    @Test
    public void entryGetRecentEntries() throws ServiceException {
        final List<RecentEntry> entryList = entryService.getRecentEntries(TEST_USER).toList().blockingGet();
        assertNotNull(entryList);
        assertTrue(entryList.size() > 3);
        final RecentEntry re = entryList.get(0);
        assertNotNull(re);
        assertEquals(33662, re.getId());
    }

    @Test
    public void entryGetPublicEntries() throws ServiceException {
        final List<Entry> entryList = entryService.getPublicEntries(TEST_USER);
        assertNotNull(entryList);
        assertTrue(entryList.size() > 0);
    }

}
