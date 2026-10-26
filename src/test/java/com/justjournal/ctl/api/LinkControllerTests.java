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
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.api.UserLinkTo;
import com.justjournal.services.UserLinkService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
class LinkControllerTests {

  @Mock private UserLinkService userLinkService;

  @InjectMocks private LinkController linkController;

  private MockHttpSession session;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    response = new MockHttpServletResponse();
  }

  private void login() {
    session.setAttribute(LOGIN_ATTRNAME, "laffer1");
    session.setAttribute(LOGIN_ATTRID, 1);
  }

  @Test
  void createRequiresLogin() {
    linkController.create(new UserLinkTo(), session, response);
    assertEquals(403, response.getStatus());
    verify(userLinkService, never()).create(any());
  }

  @Test
  void createUsesSessionUser() {
    login();
    final UserLinkTo link = UserLinkTo.builder().title("a").uri("https://a.com").userId(99).build();
    when(userLinkService.create(link))
        .thenReturn(UserLinkTo.builder().id(7).title("a").uri("https://a.com").userId(1).build());

    final Map<String, String> result = linkController.create(link, session, response);

    assertEquals("7", result.get("id"));
    assertEquals(1, link.getUserId());
  }

  @Test
  void createInvalidReturnsMessage() {
    login();
    when(userLinkService.create(any())).thenThrow(new IllegalArgumentException("nope"));

    final Map<String, String> result = linkController.create(new UserLinkTo(), session, response);

    assertEquals(400, response.getStatus());
    assertEquals("nope", result.get("error"));
  }

  @Test
  void updateNotOwned() {
    login();
    when(userLinkService.update(anyInt(), anyInt(), any())).thenReturn(Optional.empty());

    linkController.update(3, new UserLinkTo(), session, response);

    assertEquals(404, response.getStatus());
    verify(userLinkService).update(eq(1), eq(3), any());
  }

  @Test
  void updateRequiresLogin() {
    linkController.update(3, new UserLinkTo(), session, response);
    assertEquals(403, response.getStatus());
    verify(userLinkService, never()).update(anyInt(), anyInt(), any());
  }

  @Test
  void deleteOwnLink() {
    login();
    when(userLinkService.get(3))
        .thenReturn(Optional.of(UserLinkTo.builder().id(3).title("a").uri("u").userId(1).build()));

    final Map<String, String> result = linkController.deleteById(3, session, response);

    assertEquals(200, response.getStatus());
    assertEquals("3", result.get("id"));
    verify(userLinkService).delete(3);
  }

  @Test
  void deleteOtherUsersLink() {
    login();
    when(userLinkService.get(3))
        .thenReturn(Optional.of(UserLinkTo.builder().id(3).title("a").uri("u").userId(2).build()));

    linkController.deleteById(3, session, response);

    assertEquals(404, response.getStatus());
    verify(userLinkService, never()).delete(anyInt());
  }
}
