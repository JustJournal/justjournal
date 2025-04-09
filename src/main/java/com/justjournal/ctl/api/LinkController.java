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

import static com.justjournal.core.Constants.ERR_INVALID_LOGIN;
import static com.justjournal.core.Constants.PARAM_USERNAME;

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.api.UserLinkTo;
import com.justjournal.services.UserLinkService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Links that appear in their blog
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/link")
public class LinkController {

  private final UserLinkService userLinkService;

  public LinkController(final UserLinkService userLinkService) {
    this.userLinkService = userLinkService;
  }

  @GetMapping(value = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public UserLinkTo getById(@PathVariable("id") final Integer id) {
    return userLinkService.get(id).orElse(null);
  }

  //   @Cacheable(value = "userlink", key = "#username")
  @GetMapping(value = "user/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UserLinkTo>> getByUser(
      @PathVariable(PARAM_USERNAME) final String username) {

    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    final List<UserLinkTo> links = userLinkService.getByUser(username);

    return ResponseEntity.ok().body(links);
  }

  @PutMapping
  public Map<String, String> create(
      @RequestBody final UserLinkTo link,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(ERR_INVALID_LOGIN);
    }

    try {
      link.setUserId(Login.currentLoginId(session));
      final UserLinkTo l = userLinkService.create(link);
      return java.util.Collections.singletonMap("id", Integer.toString(l.getId()));
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Error adding link.");
    }
  }

  @DeleteMapping
  public Map<String, String> delete(
      @RequestBody final int linkId,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    if (linkId > 0) {
      /* valid link id */
      final Optional<UserLinkTo> link = userLinkService.get(linkId);

      if (link.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return Collections.emptyMap();
      }

      if (link.get().getUserId() == Login.currentLoginId(session)) {
        userLinkService.delete(linkId);

        return java.util.Collections.singletonMap("id", Integer.toString(linkId));
      }
    }
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    return ErrorHandler.modelError("Error deleting your link. Bad link id.");
  }
}
