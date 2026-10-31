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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.User;
import com.justjournal.model.UserBio;
import com.justjournal.repository.UserBioRepository;
import com.justjournal.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Biographies are public to read and need a login to change. */
class BiographyControllerTests {

  private UserBioRepository bioRepository;
  private UserRepository userRepository;
  private BiographyController controller;
  private MockHttpSession session;
  private MockHttpServletResponse response;
  private UserBio bio;

  @BeforeEach
  void setUp() {
    bioRepository = mock(UserBioRepository.class);
    userRepository = mock(UserRepository.class);
    controller = new BiographyController();
    controller.setBioDao(bioRepository);
    controller.setUserDao(userRepository);
    session = new MockHttpSession();
    response = new MockHttpServletResponse();

    final User user = new User();
    user.setId(4);
    user.setUsername("ada");
    when(userRepository.findByUsername("ada")).thenReturn(user);
    bio = new UserBio();
    bio.setBio("Old biography text");
    bio.setUser(user);
  }

  private void login() {
    session.setAttribute(LOGIN_ATTRID, 4);
    session.setAttribute(LOGIN_ATTRNAME, "ada");
  }

  @Test
  void get_returnsTheUsersBiography() {
    when(bioRepository.findByUserId(4)).thenReturn(bio);

    assertSame(bio, controller.get("ada", response));
  }

  @Test
  void get_unknownOrInvalidUser_isNotFound() {
    assertThrows(NotFoundException.class, () -> controller.get("no!", response));
    when(userRepository.findByUsername("nobody")).thenReturn(null);
    assertThrows(NotFoundException.class, () -> controller.get("nobody", response));
  }

  @Test
  void post_savesTheNewBiography() {
    login();
    when(bioRepository.findByUserId(4)).thenReturn(bio);

    final Map<String, String> result = controller.post("A brand new biography.", response, session);

    assertEquals(Map.of("status", "success"), result);
    assertEquals("A brand new biography.", bio.getBio());
    verify(bioRepository).save(bio);
  }

  @Test
  void post_rejectsShortOrLongText() {
    login();
    assertEquals("Biography must be > 5 characters", controller.post("tiny", response, session).get("error"));
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());

    response = new MockHttpServletResponse();
    assertEquals(
        "Biography must be > 5 characters",
        controller.post("x".repeat(BiographyController.BIO_MAX_LENGTH + 1), response, session).get("error"));
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    verify(bioRepository, never()).save(any());
  }

  @Test
  void post_requiresALogin() {
    final Map<String, String> result = controller.post("A fine biography.", response, session);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertEquals("Authentication Error", result.get("error"));
  }

  @Test
  void post_withoutAnExistingRow_isABadRequest() {
    login();
    when(bioRepository.findByUserId(4)).thenReturn(null);

    final Map<String, String> result = controller.post("A fine biography.", response, session);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Unable to save biography", result.get("error"));
  }
}
