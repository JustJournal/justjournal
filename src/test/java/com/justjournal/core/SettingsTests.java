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
package com.justjournal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.repository.SettingsRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/** The site settings are read once from the settings table, with defaults for anything unset. */
class SettingsTests {

  private static com.justjournal.model.Settings row(final String name, final String value) {
    final com.justjournal.model.Settings row = new com.justjournal.model.Settings();
    row.setName(name);
    row.setValue(value);
    return row;
  }

  private static Settings load(final Map<String, String> values) {
    final List<com.justjournal.model.Settings> rows = new ArrayList<>();
    values.forEach((name, value) -> rows.add(row(name, value)));
    final SettingsRepository repository = mock(SettingsRepository.class);
    when(repository.findAll(any(Sort.class))).thenReturn(rows);
    return new Settings(repository);
  }

  @Test
  void defaults_whenTheTableIsEmpty() {
    final Settings settings = load(Map.of());

    assertEquals("http://localhost:8080/", settings.getBaseUri());
    assertEquals("", settings.getFsPath());
    assertEquals("", settings.getContextPath());
    assertTrue(settings.isSiteEnable());
    assertEquals("", settings.getSiteName());
    assertEquals("", settings.getSiteAdmin());
    assertEquals("", settings.getSiteAdminEmail());
    assertEquals("", settings.getSiteBlog());
    assertTrue(settings.isSiteSearch());
    assertTrue(settings.isSiteDirectory());
    assertTrue(settings.isSiteRss());
    assertTrue(settings.isMailEnable());
    assertEquals("localhost", settings.getMailHost());
    assertEquals(25, settings.getMailPort());
    assertEquals("", settings.getMailUser());
    assertEquals("", settings.getMailPass());
    assertEquals("", settings.getMailFrom());
    assertEquals(" Notification", settings.getMailSubject());
    assertTrue(settings.isCommentEnable());
    assertTrue(settings.isCommentMailEnable());
    assertFalse(settings.isCommentEnableAnonymous());
    assertEquals(-5, settings.getTzOffset());
    assertEquals("EST", settings.getTzName());
    assertTrue(settings.isTzLocalize());
    assertTrue(settings.isTzUseGMT());
    assertTrue(settings.isUserAllowNew());
  }

  @Test
  void everySettingIsReadFromItsRow_caseInsensitively() {
    final Map<String, String> values = new java.util.LinkedHashMap<>();
    values.put("BaseURI", "https://www.example.com/");
    values.put("fspath", "/var/jj");
    values.put("contextpath", "/jj");
    values.put("siteenable", "false");
    values.put("sitename", "Example Journal");
    values.put("siteadmin", "Ada");
    values.put("siteadminemail", "ada@example.com");
    values.put("siteblog", "news");
    values.put("sitesearch", "FALSE");
    values.put("sitedirectory", "no");
    values.put("siterss", "false");
    values.put("mailenable", "false");
    values.put("mailhost", "smtp.example.com");
    values.put("mailport", "587");
    values.put("mailuser", "mailer");
    values.put("mailpass", "hunter2");
    values.put("mailfrom", "noreply@example.com");
    values.put("mailsubject", "Hello");
    values.put("commentenable", "false");
    values.put("commentmailenable", "false");
    values.put("tzoffset", "3");
    values.put("tzname", "CET");
    values.put("tzlocalize", "false");
    values.put("tzusegmt", "false");
    values.put("userallownew", "false");

    final Settings settings = load(values);

    assertEquals("https://www.example.com/", settings.getBaseUri());
    assertEquals("/var/jj", settings.getFsPath());
    assertEquals("/jj", settings.getContextPath());
    assertFalse(settings.isSiteEnable());
    assertEquals("Example Journal", settings.getSiteName());
    assertEquals("Ada", settings.getSiteAdmin());
    assertEquals("ada@example.com", settings.getSiteAdminEmail());
    assertEquals("news", settings.getSiteBlog());
    assertFalse(settings.isSiteSearch());
    assertFalse(settings.isSiteDirectory());
    assertFalse(settings.isSiteRss());
    assertFalse(settings.isMailEnable());
    assertEquals("smtp.example.com", settings.getMailHost());
    assertEquals(587, settings.getMailPort());
    assertEquals("mailer", settings.getMailUser());
    assertEquals("hunter2", settings.getMailPass());
    assertEquals("noreply@example.com", settings.getMailFrom());
    assertEquals("Hello", settings.getMailSubject());
    assertFalse(settings.isCommentEnable());
    assertFalse(settings.isCommentMailEnable());
    assertEquals(3, settings.getTzOffset());
    assertEquals("CET", settings.getTzName());
    assertFalse(settings.isTzLocalize());
    assertFalse(settings.isTzUseGMT());
    assertFalse(settings.isUserAllowNew());
  }

  @Test
  void blogUrls_areBuiltFromTheBaseUri() {
    final Settings settings =
        load(
            Map.of(
                "baseuri", "https://www.example.com/",
                "siteadmin", "Ada",
                "siteadminemail", "ada@example.com"));

    assertEquals("https://www.example.com/users/ada", settings.getBlogBaseUrl("ada"));
    assertEquals("ada@example.com (Ada)", settings.getWebmaster());
  }

  @Test
  void rowsWithoutANameOrValue_areSkipped_andUnknownNamesIgnored() {
    final List<com.justjournal.model.Settings> rows =
        List.of(row(null, "x"), row("sitename", null), row("nosuchsetting", "1"), row("sitename", "Kept"));
    final SettingsRepository repository = mock(SettingsRepository.class);
    when(repository.findAll(any(Sort.class))).thenReturn(rows);

    assertEquals("Kept", new Settings(repository).getSiteName());
  }

  @Test
  void aBrokenTable_stillLeavesUsableDefaults() {
    final SettingsRepository repository = mock(SettingsRepository.class);
    when(repository.findAll(any(Sort.class))).thenThrow(new IllegalStateException("no database"));

    final Settings settings = new Settings(repository);

    assertEquals("http://localhost:8080/", settings.getBaseUri());
    assertEquals(25, settings.getMailPort());
  }

  @Test
  void aBadNumber_stopsTheReadButKeepsWhatWasLoaded() {
    final Map<String, String> values = new java.util.LinkedHashMap<>();
    values.put("mailhost", "smtp.example.com");
    values.put("mailport", "lots");
    values.put("sitename", "Never reached");

    final Settings settings = load(values);

    assertEquals("smtp.example.com", settings.getMailHost());
    assertEquals(25, settings.getMailPort());
    assertEquals("", settings.getSiteName());
  }

  @Test
  void timeZoneNameCanBeChangedAtRuntime() {
    final Settings settings = load(Map.of());

    settings.setTzName("PST");

    assertEquals("PST", settings.getTzName());
  }
}
