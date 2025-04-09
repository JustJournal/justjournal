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


import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Journal;
import com.justjournal.model.Style;
import com.justjournal.model.api.JournalTo;
import com.justjournal.repository.JournalRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.StyleService;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage individual journals
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/journal")
public class JournalController {

  private final JournalRepository journalRepository;
  private final UserRepository userRepository;
  private final StyleService styleService;

  @Autowired
  public JournalController(
      final JournalRepository journalRepository,
      final UserRepository userRepository,
      final StyleService styleService) {
    this.journalRepository = journalRepository;
    this.userRepository = userRepository;
    this.styleService = styleService;
  }

  @GetMapping(value = "user/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Journal>> listByUser(
      @PathVariable(Constants.PARAM_USERNAME) final String username) {
    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    try {
      return ResponseEntity.ok(journalRepository.findByUsername(username));
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @GetMapping(value = "{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Journal> get(@PathVariable("slug") final String slug) {
    try {
      return ResponseEntity.ok(journalRepository.findOneBySlug(slug));
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @PostMapping(
      value = "",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> post(
      @RequestBody final JournalTo journal,
      final HttpSession session,
      final HttpServletResponse response) {
    return put(journal.getSlug(), journal, session, response);
  }

  @PutMapping(
      value = "{slug}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> put(
      @PathVariable("slug") final String slug,
      @RequestBody JournalTo journalTo,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    try {
      Journal j = journalRepository.findOneBySlug(slug);
      if (j == null) {
        Journal journal = new Journal();
        journal.setId(journalTo.getId());
        journal.setAllowSpider(journalTo.isAllowSpider());
        journal.setSlug(journalTo.getSlug());
        journal.setName(journalTo.getName());
        journal.setStyleId(journalTo.getStyleId());
        final Style s = styleService.get(journal.getStyleId());
        journal.setStyle(s);
        journal.setOwnerViewOnly(journalTo.isOwnerViewOnly());
        journal.setPingServices(journalTo.isPingServices());
        journal.setUser(userRepository.findById(Login.currentLoginId(session)).orElse(null));
        journal.setSince(Calendar.getInstance().getTime());
        journal.setModified(Calendar.getInstance().getTime());
        journal = journalRepository.saveAndFlush(journal);
        response.setStatus(HttpServletResponse.SC_CREATED);
        return java.util.Collections.singletonMap("slug", journal.getSlug());
      }

      if (j.getUser().getId() != Login.currentLoginId(session)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return ErrorHandler.modelError("You do not have permission to update this journal.");
      }

      j.setAllowSpider(journalTo.isAllowSpider());
      j.setName(journalTo.getName());
      j.setOwnerViewOnly(journalTo.isOwnerViewOnly());
      j.setPingServices(journalTo.isPingServices());
      if (journalTo.getStyleId() > 0) {
        final Style s = styleService.get(journalTo.getStyleId());
        j.setStyle(s);
      } else {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return ErrorHandler.modelError("Missing style id.");
      }

      j.setModified(Calendar.getInstance().getTime());
      j = journalRepository.saveAndFlush(j);

      return java.util.Collections.singletonMap("slug", j.getSlug());
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Error adding journal.");
    }
  }

  @DeleteMapping(value = "{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> delete(
      @PathVariable("slug") final String slug,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    if (slug == null || slug.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Error deleting your journal. Slug is missing.");
    }

    final Journal journal = journalRepository.findOneBySlug(slug);
    if (journal == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return ErrorHandler.modelError("Error deleting your journal. Slug not found.");
    }

    if (journal.getUser().getId() != Login.currentLoginId(session)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError("You do not have permission to delete this journal.");
    }

    journalRepository.delete(journal);
    journalRepository.flush();
    return java.util.Collections.singletonMap("slug", journal.getSlug());
  }
}
