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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.justjournal.services.EntryAccessService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** WebFinger answers account and entry lookups for public journals only, and 404s the rest. */
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
    controller =
        new WebFingerController(
            mapper, settings, userRepository, entryRepository, new EntryAccessService());
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

  private JsonNode jrd(final String resource) throws Exception {
    final ResponseEntity<String> response = controller.get(resource);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(WebFingerController.JRD_JSON, response.getHeaders().getContentType());
    return mapper.readTree(response.getBody());
  }

  private HttpStatus statusOf(final String resource) throws Exception {
    return HttpStatus.valueOf(controller.get(resource).getStatusCode().value());
  }

  private Entry entry(final int id, final Security security) {
    final Entry entry = new Entry();
    entry.setId(id);
    entry.setUser(user);
    entry.setSecurity(security);
    when(entryRepository.findById(id)).thenReturn(Optional.of(entry));
    return entry;
  }

  @Test
  void url_upgradesTheBaseUriToHttps_andDropsWwwForAccounts() {
    assertEquals(BASE, controller.baseUri);
    assertEquals("justjournal.com", controller.accountHost);
  }

  @Test
  void account_describesAPublicJournal() throws Exception {
    final JsonNode json = jrd("acct:ada@justjournal.com");

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
  void account_addsAnAvatarLinkWhenTheUserShowsOne_andAnswersWithTheCanonicalSubject()
      throws Exception {
    pref.setShowAvatar(PrefBool.Y);

    when(userRepository.findByUsername("Ada")).thenReturn(user);

    final JsonNode json = jrd("ACCT:Ada@JustJournal.com");

    assertEquals("acct:ada@justjournal.com", json.get("subject").asText());
    assertEquals(3, json.get("links").size());
    assertEquals("http://webfinger.net/rel/avatar", json.get("links").get(2).get("rel").asText());
    assertEquals(BASE + "Avatar/4", json.get("links").get(2).get("href").asText());
  }

  @Test
  void account_acceptsTheWwwHost() throws Exception {
    assertEquals("acct:ada@justjournal.com", jrd("acct:ada@www.justjournal.com").get("subject").asText());
  }

  @Test
  void account_isNotFoundForOwnerOnlyUnknownOrJournallessUsers() throws Exception {
    journal.setOwnerViewOnly(true);
    assertEquals(HttpStatus.NOT_FOUND, statusOf("acct:ada@justjournal.com"));

    when(userRepository.findByUsername("nobody")).thenReturn(null);
    assertEquals(HttpStatus.NOT_FOUND, statusOf("acct:nobody@justjournal.com"));

    final User empty = new User();
    empty.setUsername("empty");
    when(userRepository.findByUsername("empty")).thenReturn(empty);
    assertEquals(HttpStatus.NOT_FOUND, statusOf("acct:empty@justjournal.com"));
  }

  @Test
  void account_isNotFoundForOtherHostsOrInvalidNames() throws Exception {
    assertEquals(HttpStatus.NOT_FOUND, statusOf("acct:ada@elsewhere.example.com"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf("acct:ada@justjournal.com.evil.example"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf("xacct:ada@justjournal.com"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf("acct:a-b@justjournal.com"));
    verify(userRepository, never()).findByUsername("a-b");
  }

  @Test
  void blankResource_isABadRequest() throws Exception {
    assertEquals(HttpStatus.BAD_REQUEST, statusOf(" "));
    verify(userRepository, never()).findByUsername(anyString());
  }

  @Test
  void entry_linksToTheAuthorOfAPublicEntry() throws Exception {
    entry(12, Security.PUBLIC);

    final JsonNode json = jrd(BASE + "users/ada/entry/12");

    assertEquals(BASE + "users/ada/entry/12", json.get("subject").asText());
    assertEquals(1, json.get("links").size());
    assertEquals("author", json.get("links").get(0).get("rel").asText());
    assertEquals(BASE + "users/ada", json.get("links").get(0).get("href").asText());
    assertEquals("Ada's Journal", json.get("links").get(0).get("titles").get("en-us").asText());
  }

  @Test
  void entry_isNotFoundUnlessAnyoneMayReadIt() throws Exception {
    entry(12, Security.PRIVATE);
    entry(14, Security.PUBLIC).setDraft(PrefBool.Y);
    entry(15, Security.PUBLIC);
    when(entryRepository.findById(13)).thenReturn(Optional.empty());

    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/ada/entry/12"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/ada/entry/13"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/ada/entry/14"));

    journal.setOwnerViewOnly(true);
    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/ada/entry/15"));
  }

  @Test
  void entry_isNotFoundForTheWrongAuthorOrOtherUrls() throws Exception {
    entry(12, Security.PUBLIC);

    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/bob/entry/12"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/ada/entry/99999999999"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/ada/entry/12/extra"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf(BASE + "users/ada"));
    assertEquals(HttpStatus.NOT_FOUND, statusOf("https://elsewhere.example.com/x"));
  }

  @Test
  void clientsThatOnlyAcceptJson_stillGetAJrd() throws Exception {
    MockMvcBuilders.standaloneSetup(controller)
        .build()
        .perform(
            get("/.well-known/webfinger")
                .param("resource", "acct:ada@justjournal.com")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(WebFingerController.JRD_JSON))
        .andExpect(jsonPath("$.subject").value("acct:ada@justjournal.com"));
  }
}
