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

import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import com.justjournal.model.UserPref;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a journal takes part in the fediverse: followed from other servers, and its
 * public entries sent to them. Everything that talks to another server about a user asks here
 * first; the delivery queue itself doesn't, so a caller can still send an Undo or Delete after
 * the user turned federation off.
 */
@Component
public class FederationPolicy {

  /** Off for the whole site unless {@code activitypub.federation.enabled} is set. */
  @Setter
  @Value("${activitypub.federation.enabled:false}")
  private boolean enabled;

  /** Whether federation is on for the site at all. */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Whether this user's journal federates.
   *
   * @param user a user; may be null
   * @return true when the site allows it, the user turned it on, and anyone may read the journal
   */
  public boolean federates(final User user) {
    if (!enabled || user == null) return false;
    if (!optedIn(user.getUserPref())) return false;
    if (user.getJournals() == null || user.getJournals().isEmpty()) return false;
    return user.getJournals().stream().noneMatch(Journal::isOwnerViewOnly);
  }

  /** Whether the user's own setting is on, whatever the site says. */
  public static boolean optedIn(final UserPref pref) {
    return pref != null && pref.getFederate() == PrefBool.Y;
  }
}
