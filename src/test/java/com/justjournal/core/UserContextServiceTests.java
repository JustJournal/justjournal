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

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** The user context pairs the blog owner with whoever is logged in. */
class UserContextServiceTests {

  private UserRepository userRepository;
  private UserContextService service;
  private MockHttpSession session;
  private User ada;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    service = new UserContextService(userRepository);
    session = new MockHttpSession();
    ada = new User();
    ada.setId(1);
    ada.setUsername("ada");
    when(userRepository.findByUsername("ada")).thenReturn(ada);
  }

  @Test
  void anonymousVisitor_getsTheBlogOwnerOnly() {
    final UserContext context = service.getUserContext("ada", session);

    assertSame(ada, context.getBlogUser());
    assertNull(context.getAuthenticatedUser());
    assertFalse(context.isAuthBlog());
  }

  @Test
  void theOwner_isRecognised() {
    session.setAttribute(LOGIN_ATTRID, 1);
    session.setAttribute(LOGIN_ATTRNAME, "ada");

    final UserContext context = service.getUserContext("ada", session);

    assertSame(ada, context.getAuthenticatedUser());
    assertTrue(context.isAuthBlog());
  }

  @Test
  void anotherLoggedInUser_isNotTheOwner() {
    final User mia = new User();
    mia.setId(2);
    mia.setUsername("mia");
    when(userRepository.findByUsername("mia")).thenReturn(mia);
    session.setAttribute(LOGIN_ATTRID, 2);
    session.setAttribute(LOGIN_ATTRNAME, "mia");

    final UserContext context = service.getUserContext("ada", session);

    assertSame(ada, context.getBlogUser());
    assertSame(mia, context.getAuthenticatedUser());
    assertFalse(context.isAuthBlog());
  }

  @Test
  void aFailedLookupOfTheVisitor_stillGivesTheBlog() {
    when(userRepository.findByUsername("mia")).thenThrow(new IllegalStateException("db"));
    session.setAttribute(LOGIN_ATTRNAME, "mia");

    final UserContext context = service.getUserContext("ada", session);

    assertSame(ada, context.getBlogUser());
    assertNull(context.getAuthenticatedUser());
  }

  @Test
  void unknownInvalidOrBrokenBlogs_haveNoContext() {
    assertNull(service.getUserContext("no!", session));

    when(userRepository.findByUsername("nobody")).thenReturn(null);
    assertNull(service.getUserContext("nobody", session));

    when(userRepository.findByUsername("unsaved")).thenReturn(new User());
    assertNull(service.getUserContext("unsaved", session));

    when(userRepository.findByUsername("broken")).thenThrow(new IllegalStateException("db"));
    assertNull(service.getUserContext("broken", session));
  }
}
