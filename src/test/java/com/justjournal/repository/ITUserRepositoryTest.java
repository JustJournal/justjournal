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
package com.justjournal.repository;

import com.justjournal.Application;
import com.justjournal.model.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/** @author Lucas Holt */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@ActiveProfiles("it")
class ITUserRepositoryTest {
  @Autowired private UserRepository userRepository;

  @Test
   void list() {
    final Iterable<User> list = userRepository.findAll();
    Assertions.assertNotNull(list);
    Assertions.assertTrue(userRepository.count() > 0);
  }

  @Test
   void getById() {
    final User user = userRepository.findById(1).orElse(null);
    Assertions.assertNotNull(user);
    Assertions.assertEquals(1, user.getId());
  }

  @Test
   void getByUsername() {
    final User user = userRepository.findByUsername("testuser");
    Assertions.assertNotNull(user);
    Assertions.assertEquals(2908, user.getId());
  }

  @Test
  public void getByUsernameInvalid() throws Exception {
    final User user = userRepository.findByUsername("iamnotareal");
    Assertions.assertNull(user);
  }

  @Test
  public void getByUserAndPasswordInvalid() {
    final User user = userRepository.findByUsernameAndPassword("testuser", "wrongpassword");
    Assertions.assertNull(user);
  }

  @Test
  public void exists() {
    Assertions.assertTrue(userRepository.existsById(1));
  }
}
