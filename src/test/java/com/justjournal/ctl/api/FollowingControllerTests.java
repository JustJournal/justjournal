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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.model.FollowingState;
import com.justjournal.model.RemoteFollowing;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.FollowingService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The fediverse accounts a writer follows, listed, followed and unfollowed through the API. */
class FollowingControllerTests {

  private static final int USER_ID = 7;
  private static final String PATH = "/api/federation/following";
  private static final String ALICE = "https://remote.social/users/alice";

  private UserRepository userRepository;
  private FederationPolicy policy;
  private FollowingService followingService;
  private User bob;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    policy = new FederationPolicy();
    policy.setEnabled(true);
    followingService = mock(FollowingService.class);
    bob = new User();
    bob.setId(USER_ID);
    bob.setUsername("bob");
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(bob));

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new FollowingController(userRepository, policy, followingService))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private static MockHttpSession loggedIn() {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, USER_ID);
    return session;
  }

  private static MockHttpServletRequestBuilder follow(final String account) {
    return post(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"account\": \"" + account + "\"}");
  }

  private static RemoteFollowing alice(final FollowingState state) {
    final RemoteFollowing following = new RemoteFollowing();
    following.setActorId(ALICE);
    following.setHandle("@alice@remote.social");
    following.setDisplayName("Alice");
    following.setProfileUrl("https://remote.social/@alice");
    following.setFollowId("https://www.justjournal.com/users/bob#follows/1");
    following.setInbox(ALICE + "/inbox");
    following.setState(state);
    return following;
  }

  @Test
  void everything_needsALogin() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
    mockMvc.perform(follow("@alice@remote.social")).andExpect(status().isForbidden());
    mockMvc.perform(delete(PATH).param("actor", ALICE)).andExpect(status().isForbidden());
    verifyNoInteractions(followingService);
  }

  @Test
  void list_showsTheWritersFollows_withoutWhatOnlyTheServerNeeds() throws Exception {
    when(followingService.list(bob)).thenReturn(List.of(alice(FollowingState.PENDING)));

    mockMvc
        .perform(get(PATH).session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].actorId").value(ALICE))
        .andExpect(jsonPath("$[0].handle").value("@alice@remote.social"))
        .andExpect(jsonPath("$[0].displayName").value("Alice"))
        .andExpect(jsonPath("$[0].profileUrl").value("https://remote.social/@alice"))
        .andExpect(jsonPath("$[0].state").value("PENDING"))
        .andExpect(jsonPath("$[0].followId").doesNotExist())
        .andExpect(jsonPath("$[0].inbox").doesNotExist());
  }

  @Test
  void list_whileTheSiteDoesNotFederate_isEmpty() throws Exception {
    policy.setEnabled(false);

    mockMvc
        .perform(get(PATH).session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    verifyNoInteractions(followingService);
  }

  @Test
  void follow_isCreated() throws Exception {
    when(followingService.follow(bob, "@alice@remote.social"))
        .thenReturn(alice(FollowingState.PENDING));

    mockMvc
        .perform(follow("@alice@remote.social").session(loggedIn()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.actorId").value(ALICE))
        .andExpect(jsonPath("$.state").value("PENDING"));
  }

  @Test
  void follow_refused_isABadRequestWithTheReason() throws Exception {
    when(followingService.follow(bob, "@alice@remote.social"))
        .thenThrow(new FollowingService.FollowRefusedException("You already follow that account."));
    when(followingService.follow(bob, "@alice@down.example"))
        .thenThrow(new IOException("timed out"));

    mockMvc
        .perform(follow("@alice@remote.social").session(loggedIn()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("You already follow that account."));
    mockMvc
        .perform(follow("@alice@down.example").session(loggedIn()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("That server could not be reached. Try again later."));
    mockMvc.perform(follow(" ").session(loggedIn())).andExpect(status().isBadRequest());
  }

  @Test
  void follow_whileTheSiteDoesNotFederate_isRefused() throws Exception {
    policy.setEnabled(false);

    mockMvc
        .perform(follow("@alice@remote.social").session(loggedIn()))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(followingService);
  }

  @Test
  void unfollow_removesIt_orIsNotFound() throws Exception {
    when(followingService.unfollow(bob, ALICE)).thenReturn(true);

    mockMvc
        .perform(delete(PATH).param("actor", ALICE).session(loggedIn()))
        .andExpect(status().isNoContent());
    verify(followingService).unfollow(bob, ALICE);

    mockMvc
        .perform(delete(PATH).param("actor", "https://other.example/users/x").session(loggedIn()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unfollow_worksEvenWhileTheSiteDoesNotFederate() throws Exception {
    policy.setEnabled(false);
    when(followingService.unfollow(any(), any())).thenReturn(true);

    mockMvc
        .perform(delete(PATH).param("actor", ALICE).session(loggedIn()))
        .andExpect(status().isNoContent());
  }
}
