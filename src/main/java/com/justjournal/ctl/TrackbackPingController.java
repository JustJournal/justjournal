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
package com.justjournal.ctl;


import com.justjournal.model.Entry;
import com.justjournal.model.Security;
import com.justjournal.model.Trackback;
import com.justjournal.model.TrackbackType;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.cache.TrackBackIpRepository;
import com.justjournal.services.TrackbackService;
import com.justjournal.utility.DNSUtil;
import com.justjournal.utility.StringUtil;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * Trackback and Post-IT Pings inbound http://wellformedweb.org/story/9
 * http://archive.cweiske.de/trackback/trackback-1.2.html
 *
 * @author Lucas Holt
 */
@Slf4j
@Controller
@RequestMapping("/trackback")
public class TrackbackPingController {

  private final TrackbackService trackbackService;

  TrackBackIpRepository trackBackIpRepository;

  final EntryRepository entryRepository;

  @Autowired
  public TrackbackPingController(
          final TrackbackService trackbackService, TrackBackIpRepository trackBackIpRepository, EntryRepository entryRepository) {
    this.trackbackService = trackbackService;
    this.trackBackIpRepository = trackBackIpRepository;
    this.entryRepository = entryRepository;
  }

  @PostMapping(
          produces = MediaType.TEXT_XML_VALUE + ";charset=UTF-8",
      consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
  @ResponseBody
  public ResponseEntity<String> post(
      @RequestParam("entryID") int entryId, @ModelAttribute TrackbackPingRequest trackbackPingRequest) {
    log.info("received trackback request: " + trackbackPingRequest);
    try {
      boolean istrackback = true;

      if (entryId < 1) {
        if (trackbackPingRequest.getEntryID() > 0) {
          entryId = trackbackPingRequest.getEntryID();
        } else {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                  .body(trackbackService.generateResponse(1, "Missing required parameter \"entryID\"."));
        }
      }

      if (StringUtils.isEmpty(trackbackPingRequest.getUrl())
          || !DNSUtil.isUrlDomainValid(trackbackPingRequest.getUrl())) {
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(trackbackService.generateResponse(1, "Missing required parameter \"url\"."));
      }

      final String ip = com.justjournal.utility.RequestUtil.getRemoteIP();
      if (trackBackIpRepository.getIpAddress(ip).blockOptional(Duration.ofMinutes(1)).isPresent()) {
        log.warn("Multiple requests during timeout period from IP ADDRESS {} for TrackBack.", ip);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(trackbackService.generateResponse(1, "Too many requests. Try again later."));
      }
      trackBackIpRepository.saveIpAddreess(ip).block(Duration.ofMinutes(1));

      final Optional<Entry> entry = entryRepository.findById(entryId);
      if (entry.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(trackbackService.generateResponse(1, "Entry not found."));
      }

      if (entry.get().getSecurity() != Security.PUBLIC) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(trackbackService.generateResponse(1, "Entry does not support TrackBack"));
      }

      // todo ... validate trackback.
      // TODO: add pingback support which looks xmlrpc-ish

      final Trackback tb = new Trackback();
      if (StringUtils.isNotEmpty(trackbackPingRequest.getTitle())) {
        tb.setSubject(trackbackPingRequest.getTitle()); // trackback
      } else if (StringUtils.isNotEmpty(trackbackPingRequest.getName())) { // post it
        tb.setSubject(trackbackPingRequest.getName());
        istrackback = false;
      }

      if (StringUtils.isNotEmpty(trackbackPingRequest.getExcerpt()))
        tb.setBody(trackbackPingRequest.getExcerpt());
      else if (StringUtils.isNotEmpty(trackbackPingRequest.getComment())) {
        tb.setBody(trackbackPingRequest.getComment());
        istrackback = false;
      }

      if (StringUtil.isEmailValid(trackbackPingRequest.getEmail())
          && DNSUtil.isEmailDomainValid(trackbackPingRequest.getEmail())) {
        tb.setAuthorEmail(trackbackPingRequest.getEmail());
      }

      tb.setUrl(trackbackPingRequest.getUrl());

      if (istrackback) {
        tb.setType(TrackbackType.trackback);
      } else {
        tb.setType(TrackbackType.postit);
      }
      // don't do pingbacks yet.

      tb.setBlogName(trackbackPingRequest.getBlog_name());
      if (entryId > 0)
        tb.setEntryId(entryId);
      else
        tb.setEntryId(trackbackPingRequest.getEntryID());
      tb.setUrl(trackbackPingRequest.getUrl());

      if (trackbackService.save(tb) == null) {
        return ResponseEntity.status(HttpStatus.ALREADY_REPORTED)
            .body(trackbackService.generateResponse(1, "TrackBack entry exists"));
      }

      return ResponseEntity.ok(trackbackService.generateResponse(0, null));
    } catch (final Exception e) {
      log.error("TrackBack ping failed ", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
          .body(trackbackService.generateResponse(1, e.getMessage()));
    }
  }
}
