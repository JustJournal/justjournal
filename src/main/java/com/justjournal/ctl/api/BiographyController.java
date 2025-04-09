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

import static com.justjournal.core.Constants.PARAM_USERNAME;

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.User;
import com.justjournal.model.UserBio;
import com.justjournal.repository.UserBioRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.utility.StringUtil;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Retrieve and manage user biography content.
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/biography")
public class BiographyController {
  public static final int BIO_MAX_LENGTH = 150;

  private UserBioRepository bioDao = null;
  private UserRepository userDao;

  @Autowired
  public void setBioDao(final UserBioRepository bioDao) {
    this.bioDao = bioDao;
  }

  @Autowired
  public void setUserDao(final UserRepository userDao) {
    this.userDao = userDao;
  }

  @Cacheable(value = "biography", key = "username")
  @GetMapping(
      value = "{username}",
      headers = Constants.HEADER_ACCEPT_ALL,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public UserBio get(
      @PathVariable(PARAM_USERNAME) String username, final HttpServletResponse response) {

    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    final User user = userDao.findByUsername(username);
    if (user == null) {
      throw new NotFoundException();
    }
    return bioDao.findByUserId(user.getId());
  }

  // TODO: API is bad for caching.
  @CacheEvict(value = "biography", allEntries = true)
  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> post(
      @RequestBody final String bio,
      final HttpServletResponse response,
      final HttpSession session) {

    final int userID = Login.currentLoginId(session);

    if (!StringUtil.lengthCheck(bio, 5, BIO_MAX_LENGTH)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Biography must be > 5 characters");
    }

    try {
      if (userID > 0) {
        final UserBio biography = bioDao.findByUserId(userID);
        biography.setBio(bio);
        bioDao.save(biography);

        return java.util.Collections.singletonMap("status", "success");
      } else {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return ErrorHandler.modelError("Authentication Error");
      }
    } catch (final Exception e3) {
      log.error(e3.getMessage());
    }

    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    return ErrorHandler.modelError("Unable to save biography");
  }
}
