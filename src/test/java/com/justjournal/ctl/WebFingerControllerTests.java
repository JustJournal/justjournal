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
package com.justjournal.ctl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.model.UserPref;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** WebFinger answers account and entry lookups for public journals only. */
class WebFingerControllerTests {

  private static final String BASE = "https://www.justjournal.com/";

  private final ObjectMapper mapper = new ObjectMapper();
  private UserRepository userRepository;
  private EntryRepository entryRepository;
  private WebFingerController controller;
  private User user;
  private Journal journal;
  private UserPref pref;

  @BeforeEach
  void setUp() {
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    userRepository = mock(UserRepository.class);
    entryRepository = mock(EntryRepository.class);
    controller = new WebFingerController(mapper, settings, userRepository, entryRepository);
    controller.url();

    user = new User();
    user.setId(4);
    user.setUsername("ada");
    user.setName("Ada");
    user.setLastName("Lovelace");
    journal = new Journal();
    journal.setName("Ada's Journal");
    journal.setUser(user);
    user.getJournals().add(journal);
    pref = new UserPref();
    pref.setShowAvatar(PrefBool.N);
    ReflectionTestUtils.setField(user, "userPref", pref);
    when(userRepository.findByUsername("ada")).thenReturn(user);
  }

  private JsonNode get(final String resource) throws Exception {
    return mapper.readTree(controller.get(resource));
  }

  @Test
  void url_upgradesTheBaseUriToHttps() {
    assertEquals(BASE, controller.baseUri);
  }

  @Test
  void account_describesAPublicJournal() throws Exception {
    final JsonNode json = get("acct:ada@justjournal.com");

    assertEquals("acct:ada@justjournal.com", json.get("subject").asText());
    assertEquals(BASE + "users/ada", json.get("aliases").get(0).asText());
    assertEquals(2, json.get("links").size());
    assertEquals("http://webfinger.net/rel/profile-page", json.get("links").get(0).get("rel").asText());
    assertEquals(BASE + "profile/ada", json.get("links").get(0).get("href").asText());
    assertEquals("text/html", json.get("links").get(0).get("type").asText());
    assertEquals("self", json.get("links").get(1).get("rel").asText());
    assertEquals(BASE + "users/ada", json.get("links").get(1).get("href").asText());
    assertEquals("application/activity+json", json.get("links").get(1).get("type").asText());
    assertEquals("Ada Lovelace", json.get("properties").get(BASE + "ns/name").asText());
  }

  @Test
  void account_addsAnAvatarLinkWhenTheUserShowsOne() throws Exception {
    pref.setShowAvatar(PrefBool.Y);

    when(userRepository.findByUsername("Ada")).thenReturn(user);

    final JsonNode json = get("ACCT:Ada@JustJournal.com");

    assertEquals(3, json.get("links").size());
    assertEquals("http://webfinger.net/rel/avatar", json.get("links").get(2).get("rel").asText());
    assertEquals(BASE + "Avatar/4", json.get("links").get(2).get("href").asText());
  }

  @Test
  void account_saysNothingAboutOwnerOnlyOrUnknownUsers() throws Exception {
    journal.setOwnerViewOnly(true);
    JsonNode json = get("acct:ada@justjournal.com");
    assertTrue(json.get("aliases").isEmpty());
    assertTrue(json.get("links").isEmpty());

    when(userRepository.findByUsername("nobody")).thenReturn(null);
    json = get("acct:nobody@justjournal.com");
    assertEquals("acct:nobody@justjournal.com", json.get("subject").asText());
    assertTrue(json.get("links").isEmpty());
  }

  @Test
  void entry_linksToTheAuthorOfAPublicEntry() throws Exception {
    final Entry entry = new Entry();
    entry.setId(12);
    entry.setUser(user);
    entry.setSecurity(Security.PUBLIC);
    when(entryRepository.findById(12)).thenReturn(Optional.of(entry));

    final JsonNode json = get(BASE + "users/ada/entry/12");

    assertEquals(1, json.get("links").size());
    assertEquals("author", json.get("links").get(0).get("rel").asText());
    assertEquals(BASE + "users/ada", json.get("links").get(0).get("href").asText());
    assertEquals("Ada's Journal", json.get("links").get(0).get("titles").get("en-us").asText());
  }

  @Test
  void entry_ignoresPrivateOrMissingEntries_andOtherUrls() throws Exception {
    final Entry entry = new Entry();
    entry.setId(12);
    entry.setUser(user);
    entry.setSecurity(Security.PRIVATE);
    when(entryRepository.findById(12)).thenReturn(Optional.of(entry));
    when(entryRepository.findById(13)).thenReturn(Optional.empty());

    assertTrue(get(BASE + "users/ada/entry/12").get("links").isEmpty());
    assertTrue(get(BASE + "users/ada/entry/13").get("links").isEmpty());
    assertTrue(get(BASE + "users/ada").get("links").isEmpty());
    final JsonNode other = get("https://elsewhere.example.com/x");
    assertEquals("https://elsewhere.example.com/x", other.get("subject").asText());
    assertFalse(other.has("missing"));
  }
}
