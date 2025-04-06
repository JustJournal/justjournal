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
package com.justjournal.rss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.justjournal.Application;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/** @author Lucas Holt */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@ActiveProfiles("it")
class ITRssTests {

  @Autowired private Rss rss;

  @Test
   void testPopulate() {

    final java.util.GregorianCalendar calendar = new java.util.GregorianCalendar();
    calendar.setTime(new java.util.Date());

    final Collection<Entry> entries = new ArrayList<>();

    final User user = new User();
    user.setUsername("testuser");
    user.setSince(2003);

    final Entry entry = new Entry();
    entry.setId(1);
    entry.setFormat(FormatType.TEXT);
    entry.setBody("Foo Bar");
    entry.setDraft(PrefBool.N);
    entry.setAutoFormat(PrefBool.Y);
    entry.setSubject("Test Blog Post");
    entry.setUser(user);
    entries.add(entry);

    rss.populate(entries);

    Assertions.assertTrue(rss.size() > 0);

    final String xml = rss.toXml();
    Assertions.assertTrue(xml.contains("<item"));
  }

  @Test
  void testWebmaster() {
    final String webmaster = "test@test.com (test)";

    rss.setWebMaster(webmaster);
    Assertions.assertEquals(webmaster, rss.getWebMaster());
  }
}
