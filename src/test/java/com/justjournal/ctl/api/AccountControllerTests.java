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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.FavoriteRepository;
import com.justjournal.repository.FriendsRepository;
import com.justjournal.repository.RssSubscriptionsRepository;
import com.justjournal.repository.UserBioRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserLinkRepository;
import com.justjournal.repository.UserLocationRepository;
import com.justjournal.repository.UserPrefRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.AlbumImageService;
import com.justjournal.services.ImageStorageService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
class AccountControllerTests {

  @Mock private UserRepository userRepository;

  /** An unknown username is a plain 404, not an exception. */
  @Test
  void unknownUserIsNotFound() {
    final AccountController controller =
        new AccountController(
            mock(Login.class),
            userRepository,
            mock(CommentRepository.class),
            mock(EntryRepository.class),
            mock(JdbcTemplate.class),
            mock(FriendsRepository.class),
            mock(UserBioRepository.class),
            mock(UserContactRepository.class),
            mock(UserLinkRepository.class),
            mock(UserLocationRepository.class),
            mock(UserPrefRepository.class),
            mock(RssSubscriptionsRepository.class),
            mock(AlbumImageService.class),
            mock(FavoriteRepository.class),
            mock(ImageStorageService.class));
    when(userRepository.findByUsername("nobody")).thenReturn(null);
    final MockHttpServletResponse response = new MockHttpServletResponse();

    assertNull(controller.getByUsername("nobody", new MockHttpSession(), response));
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }
}
