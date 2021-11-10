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
package com.justjournal.core;


import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.EntryStatisticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** @author Lucas Holt */
@Slf4j
@Component
@Profile("!test")
public class StatisticsRefresh {

  private final UserRepository userRepository;

  private final EntryStatisticService entryStatisticService;

  @Autowired
  public StatisticsRefresh(
      final UserRepository userRepository, final EntryStatisticService entryStatisticService) {
    this.userRepository = userRepository;
    this.entryStatisticService = entryStatisticService;
  }

  // Every six hours?
  @Scheduled(fixedDelay = 1000 * 60 * 60 * 6, initialDelay = 30000)
  public void run() {
    log.info("Statistics Refresh: init");

    try {
      for (final User user : userRepository.findAll()) {
        entryStatisticService.compute(user).subscribe();
      }
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }

    log.info("Statistics Refresh: Quit");
  }
}
