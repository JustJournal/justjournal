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

import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.RemotePostService;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes stored fediverse posts once an hour: those older than {@code
 * activitypub.remote-posts.keep-days}, and those by accounts nobody here follows any more. Nothing
 * runs while federation is off for the site.
 */
@Slf4j
@Component
public class RemotePostCleanup {

  private final RemotePostService remotePostService;
  private final FederationPolicy federationPolicy;

  public RemotePostCleanup(
      final RemotePostService remotePostService, final FederationPolicy federationPolicy) {
    this.remotePostService = remotePostService;
    this.federationPolicy = federationPolicy;
  }

  @Scheduled(fixedDelay = 1000 * 60 * 60, initialDelay = 1000 * 60 * 15)
  public void purge() {
    if (!federationPolicy.isEnabled()) return;
    final int removed = remotePostService.purge(new Date());
    if (removed > 0) log.info("Removed {} stored fediverse posts", removed);
  }
}
