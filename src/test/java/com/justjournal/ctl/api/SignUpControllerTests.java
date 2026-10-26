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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.User;
import com.justjournal.model.api.NewUser;
import com.justjournal.services.AccountService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
class SignUpControllerTests {

  @Mock private AccountService accountService;

  @Mock private Settings settings;

  @Mock private Login login;

  @InjectMocks private SignUpController signUpController;

  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    response = new MockHttpServletResponse();
    when(settings.isUserAllowNew()).thenReturn(true);
  }

  private NewUser newUser(final String username) {
    final NewUser user = new NewUser();
    user.setUsername(username);
    user.setPassword("secret123");
    user.setEmail("test@justjournal.com");
    user.setFirstName("Test");
    return user;
  }

  @Test
  void newUsernameIsCreated() throws Exception {
    final User created = new User();
    created.setId(42);
    when(login.exists("technews")).thenReturn(false);
    when(accountService.signup(any())).thenReturn(created);

    final Map<String, String> result = signUpController.post(newUser("technews"), response);

    assertEquals(201, response.getStatus());
    assertEquals("42", result.get("id"));
  }

  @Test
  void existingUsernameIsRejected() throws Exception {
    when(login.exists("technews")).thenReturn(true);

    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> signUpController.post(newUser("technews"), response));
    assertEquals("Username is already taken", e.getMessage());
    verify(accountService, never()).signup(any());
  }

  @Test
  void invalidUsernameIsRejected() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> signUpController.post(newUser("bad name!"), response));
    verify(accountService, never()).signup(any());
  }
}
