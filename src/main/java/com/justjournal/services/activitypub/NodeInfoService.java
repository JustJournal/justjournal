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
package com.justjournal.services.activitypub;

import com.justjournal.model.Security;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The usage figures NodeInfo reports: every account on the site, those that logged in within a
 * month and within half a year, and the public entries. They are counted at most once an hour, as
 * fediverse crawlers ask often and the figures needn't be fresh; each server keeps its own copy.
 *
 * @author Lucas Holt
 */
@Service
public class NodeInfoService {
  static final Duration KEEP = Duration.ofHours(1);
  private static final Duration MONTH = Duration.ofDays(30);
  private static final Duration HALF_YEAR = Duration.ofDays(180);

  /** What NodeInfo's {@code usage} holds. */
  public record Usage(long users, long activeMonth, long activeHalfyear, long localPosts) {}

  private final UserRepository userRepository;
  private final EntryRepository entryRepository;
  private final Clock clock;

  private Usage usage;
  private Instant countedAt;

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public NodeInfoService(
      final UserRepository userRepository, final EntryRepository entryRepository) {
    this(userRepository, entryRepository, Clock.systemUTC());
  }

  NodeInfoService(
      final UserRepository userRepository,
      final EntryRepository entryRepository,
      final Clock clock) {
    this.userRepository = userRepository;
    this.entryRepository = entryRepository;
    this.clock = clock;
  }

  /** The figures, counted again when the last count is an hour old. */
  public synchronized Usage usage() {
    final Instant now = clock.instant();
    if (usage == null || !now.isBefore(countedAt.plus(KEEP))) {
      usage =
          new Usage(
              userRepository.count(),
              userRepository.countByLastLoginAfter(Date.from(now.minus(MONTH))),
              userRepository.countByLastLoginAfter(Date.from(now.minus(HALF_YEAR))),
              entryRepository.countBySecurity(Security.PUBLIC));
      countedAt = now;
    }
    return usage;
  }
}
