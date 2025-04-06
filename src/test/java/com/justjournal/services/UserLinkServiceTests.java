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
package com.justjournal.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.User;
import com.justjournal.model.UserLink;
import com.justjournal.model.api.UserLinkTo;
import com.justjournal.repository.UserLinkRepository;
import com.justjournal.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UserLinkServiceTests {

  @Mock private UserLinkRepository userLinkRepository;

  @Mock private UserRepository userRepository;

  @InjectMocks private UserLinkService userLinkService;

  @Test
  void testCreate() {
    UserLinkTo link =
        UserLinkTo.builder().id(1).title("foo").uri("http://mysite.com").userId(1).build();

    UserLink item =
        UserLink.builder().id(1).title("foo").uri("http://mysite.com").user(new User()).build();

    when(userLinkRepository.save(any())).thenReturn(item);
    UserLinkTo result = userLinkService.create(link);
    Assertions.assertNotNull(result);
    verify(userLinkRepository, times(1)).save(any(UserLink.class));
  }

  @Test
  void testGet() {
    UserLink item =
        UserLink.builder().id(1).title("foo").uri("http://mysite.com").user(new User()).build();
    when(userLinkRepository.findById(anyInt())).thenReturn(Optional.of(item));

    Optional<UserLinkTo> result = userLinkService.get(1);
    Assertions.assertTrue(result.isPresent());
    Assertions.assertEquals(1, result.get().getId());
    Assertions.assertEquals("foo", result.get().getTitle());
    Assertions.assertEquals("http://mysite.com", result.get().getUri());
    verify(userLinkRepository, times(1)).findById(1);
  }

  @Test
  void testGetByUser() {
    UserLink item =
        UserLink.builder().id(1).title("foo").uri("http://mysite.com").user(new User()).build();
    when(userLinkRepository.findByUsernameOrderByTitleTitleAsc(anyString()))
        .thenReturn(Collections.singletonList(item));

    List<UserLinkTo> result = userLinkService.getByUser("user");
    Assertions.assertNotNull(result);
    Assertions.assertEquals(1, result.get(0).getId());
    Assertions.assertEquals("foo", result.get(0).getTitle());
    Assertions.assertEquals("http://mysite.com", result.get(0).getUri());
    verify(userLinkRepository, times(1)).findByUsernameOrderByTitleTitleAsc(anyString());
  }

  @Test
  void testDelete() {
    userLinkService.delete(1);
    verify(userLinkRepository, times(1)).deleteById(anyInt());
  }
}
