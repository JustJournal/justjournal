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

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Activities the inboxes have already taken, so one sent twice is acted on once. Each is
 * remembered by its sender and its id (or, for one without an id, its Signature header) in the
 * {@link #CACHE} cache, which keeps entries for as long as a signature's Date is accepted (see
 * {@code CacheConfig}); after that a copy of the request no longer verifies anyway.
 *
 * <p>The sender is part of the key, so one server can't use up the ids of another's activities.
 * If the cache can't be reached the activity is taken: everything the inboxes do can safely be
 * done twice.
 */
@Slf4j
@Component
public class SeenActivities {
  public static final String CACHE = "activitypubInboxSeen";

  private final CacheManager cacheManager;

  public SeenActivities(final CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  /**
   * Remember an activity.
   *
   * @param actorId the verified sender
   * @param key the activity's id, or its Signature header when it has none
   * @return true the first time, false when it was seen already
   */
  public boolean firstTime(final String actorId, final String key) {
    final Cache cache = cacheManager.getCache(CACHE);
    if (cache == null) return true;
    try {
      return cache.putIfAbsent(hash(actorId, key), Boolean.TRUE) == null;
    } catch (final RuntimeException e) {
      log.warn("Could not check whether activity {} was seen: {}", key, e.getMessage());
      return true;
    }
  }

  /** Forget an activity that couldn't be acted on, so the sender's retry is taken. */
  public void forget(final String actorId, final String key) {
    final Cache cache = cacheManager.getCache(CACHE);
    if (cache == null) return;
    try {
      cache.evict(hash(actorId, key));
    } catch (final RuntimeException e) {
      log.warn("Could not forget activity {}: {}", key, e.getMessage());
    }
  }

  /** Ids and signatures can be long; a hash keeps the cache keys short. */
  private static String hash(final String actorId, final String key) {
    final byte[] text = (actorId + "\n" + key).getBytes(StandardCharsets.UTF_8);
    return HexFormat.of().formatHex(HttpSignatures.sha256(text));
  }
}
