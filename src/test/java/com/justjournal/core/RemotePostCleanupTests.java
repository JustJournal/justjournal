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
package com.justjournal.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.RemotePostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The hourly purge of stored fediverse posts. */
class RemotePostCleanupTests {

  private RemotePostService remotePostService;
  private FederationPolicy policy;
  private RemotePostCleanup cleanup;

  @BeforeEach
  void setUp() {
    remotePostService = mock(RemotePostService.class);
    policy = new FederationPolicy();
    cleanup = new RemotePostCleanup(remotePostService, policy);
  }

  @Test
  void purges_whileTheSiteFederates() {
    policy.setEnabled(true);
    cleanup.purge();
    verify(remotePostService).purge(any());
  }

  @Test
  void doesNothing_whileTheSiteDoesNotFederate() {
    cleanup.purge();
    verifyNoInteractions(remotePostService);
  }
}
