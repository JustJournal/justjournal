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
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Statistics;
import com.justjournal.model.UserStatistics;
import com.justjournal.services.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Generate Global and per user statistics on blogging entries and comments.
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

  private final StatisticsService statisticsService;

  @Autowired
  public StatisticsController(final StatisticsService statisticsService) {
    this.statisticsService = statisticsService;
  }

  /**
   * Get Site statistics
   *
   * @return statistics
   */
  @Transactional
  //   @Cacheable("statistics")
  @GetMapping(headers = Constants.HEADER_ACCEPT_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Statistics> get() {

    try {
      final Statistics s = statisticsService.getStatistics();
      return ResponseEntity.ok().eTag(Integer.toString(s.hashCode())).body(s);
    } catch (final ServiceException e) {
      log.warn("Statistics not available", e);
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  /**
   * Get statistics for a specific user
   *
   * @param username username
   * @return stats
   */
  @Transactional
  //   @Cacheable(value = "userstatistics", key = "username")
  @GetMapping(
      value = "{username}",
      headers = Constants.HEADER_ACCEPT_ALL,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserStatistics> getById(
      @PathVariable(Constants.PARAM_USERNAME) final String username) {

    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    try {
      final UserStatistics us = statisticsService.getUserStatistics(username);
      if (us == null) {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
      }

      return ResponseEntity.ok().eTag(Integer.toString(us.hashCode())).body(us);

    } catch (final ServiceException e) {
      log.warn("User Statistics error: (" + username + "), " + e.getMessage(), e);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
