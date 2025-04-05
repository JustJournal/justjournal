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

import static com.justjournal.core.Constants.PARAM_ID;

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.api.TrackbackTo;
import com.justjournal.repository.EntryRepository;
import com.justjournal.services.TrackbackService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Return trackback responses for a given entry, similar to comments controller
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/trackback")
public class TrackbackController {
  TrackbackService trackbackService;

  EntryRepository entryRepository;

  @Autowired
  public TrackbackController(EntryRepository entryRepository, TrackbackService trackbackService) {
    this.entryRepository = entryRepository;
    this.trackbackService = trackbackService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public List<TrackbackTo> getTrackbacks(
      @RequestParam(Constants.PARAM_ENTRY_ID) final Integer entryId,
      final HttpServletResponse response) {
    final Entry entry = entryRepository.findById(entryId).orElse(null);

    if (entry == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return Collections.emptyList();
    }

    try {
      if (new ArrayList<>(entry.getUser().getJournals()).get(0).isOwnerViewOnly()
          || entry.getAllowComments() == PrefBool.N
          || entry.getSecurity() == Security.PRIVATE) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      }
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    return trackbackService.getByEntry(entryId);
  }

  @DeleteMapping(value = "{id}")
  public Map<String, String> delete(
      @PathVariable(PARAM_ID) final int id,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    try {
      final Optional<TrackbackTo> trackbackTo = trackbackService.getById(id);
      if (trackbackTo.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return ErrorHandler.modelError("Trackback not found.");
      }

      final Entry entry = entryRepository.findById(trackbackTo.get().getEntryId()).orElse(null);
      if (entry == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return ErrorHandler.modelError("Trackback entry not found.");
      }

      if (entry.getUser().getId() == Login.currentLoginId(session)) trackbackService.deleteById(id);

      return java.util.Collections.singletonMap(PARAM_ID, Integer.toString(id));
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Could not delete the trackback.");
    }
  }
}
