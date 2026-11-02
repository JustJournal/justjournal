/*
 * Copyright (c) 2003-2026 Lucas Holt
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

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import com.justjournal.model.UserPref;
import com.justjournal.repository.UserPrefRepository;
import com.justjournal.services.activitypub.ActorDeletion;
import com.justjournal.services.activitypub.FederationPolicy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The fediverse opt-in on the journal preferences page, saved and read through the API. */
class FederationControllerTests {

  private static final int USER_ID = 7;

  private UserPrefRepository userPrefRepository;
  private FederationPolicy policy;
  private UserPref pref;
  private User user;
  private ActorDeletion actorDeletion;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    userPrefRepository = mock(UserPrefRepository.class);
    policy = new FederationPolicy();
    policy.setEnabled(true);
    pref = new UserPref();
    when(userPrefRepository.findByUserId(USER_ID)).thenReturn(Optional.of(pref));
    // a user with a public journal, so only the opt-in decides whether it federates
    user = new User();
    user.setId(USER_ID);
    user.setUsername("bob");
    ReflectionTestUtils.setField(user, "userPref", pref);
    pref.setUser(user);
    final Journal journal = new Journal();
    journal.setUser(user);
    user.getJournals().add(journal);
    actorDeletion = mock(ActorDeletion.class);

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new FederationController(userPrefRepository, policy, actorDeletion))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private static MockHttpSession loggedIn() {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, USER_ID);
    return session;
  }

  private void putFederate(final boolean federate, final int expectedStatus) throws Exception {
    mockMvc
        .perform(
            put("/api/federation")
                .session(loggedIn())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"federate\": " + federate + "}"))
        .andExpect(status().is(expectedStatus));
  }

  @Test
  void isOffUntilTurnedOn() throws Exception {
    mockMvc
        .perform(get("/api/federation").session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.federate").value(false));
  }

  @Test
  void turningItOn_isSavedAndReadBack() throws Exception {
    putFederate(true, 200);

    assertEquals(PrefBool.Y, pref.getFederate());
    verify(userPrefRepository).save(pref);
    verify(actorDeletion, never()).federationStopped(any());
    mockMvc
        .perform(get("/api/federation").session(loggedIn()))
        .andExpect(jsonPath("$.federate").value(true));
  }

  @Test
  void turningItOff_isSaved_andTellsFollowersTheActorIsGone() throws Exception {
    pref.setFederate(PrefBool.Y);

    putFederate(false, 200);

    assertEquals(PrefBool.N, pref.getFederate());
    verify(userPrefRepository).save(pref);
    verify(actorDeletion).federationStopped(user);
  }

  @Test
  void turningItOffWhenItWasOff_sendsNothing() throws Exception {
    putFederate(false, 200);

    verify(actorDeletion, never()).federationStopped(any());
  }

  @Test
  void turningItOffForAnOwnerOnlyJournal_sendsNothing_itWasNotFederating() throws Exception {
    pref.setFederate(PrefBool.Y);
    user.getJournals().forEach(journal -> journal.setOwnerViewOnly(true));

    putFederate(false, 200);

    verify(actorDeletion, never()).federationStopped(any());
  }

  @Test
  void whileTheSiteDoesNotFederate_itIsUnavailableAndCannotBeTurnedOn() throws Exception {
    policy.setEnabled(false);

    mockMvc
        .perform(get("/api/federation").session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));
    putFederate(true, 400);

    assertEquals(PrefBool.N, pref.getFederate());
    verify(userPrefRepository, never()).save(any());
  }

  @Test
  void whileTheSiteDoesNotFederate_itCanStillBeTurnedOff() throws Exception {
    policy.setEnabled(false);
    pref.setFederate(PrefBool.Y);

    putFederate(false, 200);

    assertEquals(PrefBool.N, pref.getFederate());
    // nothing is sent while the site doesn't federate
    verify(actorDeletion, never()).federationStopped(any());
  }

  @Test
  void needsALogin() throws Exception {
    mockMvc.perform(get("/api/federation")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/federation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"federate\": true}"))
        .andExpect(status().isForbidden());
    verify(userPrefRepository, never()).save(any());
  }
}
