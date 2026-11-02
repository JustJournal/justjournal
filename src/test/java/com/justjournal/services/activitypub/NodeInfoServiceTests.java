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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.Security;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeInfoServiceTests {

  private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

  private UserRepository userRepository;
  private EntryRepository entryRepository;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    entryRepository = mock(EntryRepository.class);
    when(userRepository.count()).thenReturn(120L);
    when(userRepository.countByLastLoginAfter(Date.from(NOW.minus(Duration.ofDays(30)))))
        .thenReturn(7L);
    when(userRepository.countByLastLoginAfter(Date.from(NOW.minus(Duration.ofDays(180)))))
        .thenReturn(19L);
    when(entryRepository.countBySecurity(Security.PUBLIC)).thenReturn(4321L);
  }

  private NodeInfoService at(final Instant instant) {
    return new NodeInfoService(
        userRepository, entryRepository, Clock.fixed(instant, ZoneOffset.UTC));
  }

  @Test
  void countsEveryUserTheActiveOnesAndPublicEntries() {
    assertEquals(new NodeInfoService.Usage(120, 7, 19, 4321), at(NOW).usage());
  }

  @Test
  void countsAgainOnlyOnceTheFiguresAreAnHourOld() {
    final Clock clock = mock(Clock.class);
    when(clock.instant())
        .thenReturn(
            NOW, NOW.plus(Duration.ofMinutes(59)), NOW.plus(NodeInfoService.KEEP));
    final NodeInfoService service = new NodeInfoService(userRepository, entryRepository, clock);

    service.usage();
    service.usage();
    verify(userRepository, times(1)).count();
    verify(entryRepository, times(1)).countBySecurity(Security.PUBLIC);

    service.usage();
    verify(userRepository, times(2)).count();
    verify(userRepository, times(4)).countByLastLoginAfter(any());
  }
}
