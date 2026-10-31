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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.api.UserLinkTo;
import com.justjournal.services.UserLinkService;
import java.util.List;
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

  @Test
  void getById_returnsTheLinkOrNull() {
    final UserLinkTo link = UserLinkTo.builder().id(3).title("a").uri("https://a.com").userId(1).build();
    when(userLinkService.get(3)).thenReturn(Optional.of(link));
    when(userLinkService.get(4)).thenReturn(Optional.empty());

    assertSame(link, linkController.getById(3));
    assertNull(linkController.getById(4));
  }

  @Test
  void getByUser_listsTheLinks_andRejectsBadNames() {
    final List<UserLinkTo> links =
        List.of(UserLinkTo.builder().id(3).title("a").uri("https://a.com").userId(1).build());
    when(userLinkService.getByUser("laffer1")).thenReturn(links);

    assertEquals(links, linkController.getByUser("laffer1").getBody());
    assertThrows(NotFoundException.class, () -> linkController.getByUser("no!"));
  }

  @Test
  void createFailureIsABadRequest() {
    login();
    when(userLinkService.create(any())).thenThrow(new IllegalStateException("db"));

    final Map<String, String> result = linkController.create(new UserLinkTo(), session, response);

    assertEquals(400, response.getStatus());
    assertEquals("Error adding link.", result.get("error"));
  }

  @Test
  void updateOwnLink() {
    login();
    final UserLinkTo link = UserLinkTo.builder().title("b").uri("https://b.com").build();
    when(userLinkService.update(1, 7, link)).thenReturn(Optional.of(link));

    assertEquals(Map.of("id", "7"), linkController.update(7, link, session, response));
    assertEquals(200, response.getStatus());
  }

  @Test
  void updateInvalidOrFailing() {
    login();
    final UserLinkTo link = new UserLinkTo();
    when(userLinkService.update(1, 7, link)).thenThrow(new IllegalArgumentException("Title is required"));
    assertEquals("Title is required", linkController.update(7, link, session, response).get("error"));
    assertEquals(400, response.getStatus());

    response = new MockHttpServletResponse();
    when(userLinkService.update(1, 8, link)).thenThrow(new IllegalStateException("db"));
    assertEquals("Error updating link.", linkController.update(8, link, session, response).get("error"));
    assertEquals(400, response.getStatus());
  }

  @Test
  void deleteById_deletesOwnLink_andRejectsBadIds() {
    login();
    when(userLinkService.get(7))
        .thenReturn(Optional.of(UserLinkTo.builder().id(7).title("a").uri("https://a.com").userId(1).build()));

    assertEquals(Map.of("id", "7"), linkController.deleteById(7, session, response));
    verify(userLinkService).delete(7);

    response = new MockHttpServletResponse();
    assertEquals(
        "Error deleting your link. Bad link id.", linkController.deleteById(0, session, response).get("error"));
    assertEquals(400, response.getStatus());
  }

  @Test
  void deleteRequiresLogin() {
    final Map<String, String> result = linkController.deleteById(7, session, response);

    assertEquals(403, response.getStatus());
    assertEquals(true, result.containsKey("error"));
    verify(userLinkService, never()).delete(anyInt());
  }
}
