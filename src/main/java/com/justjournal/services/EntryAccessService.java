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
package com.justjournal.services;

import com.justjournal.model.Entry;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import org.springframework.stereotype.Service;

/**
 * Who may see a blog post: the rule the journal pages apply, for what is shown alongside a post,
 * such as its picture and link preview.
 *
 * <p>Nobody sees the posts of an account an administrator disabled. The owner sees all their
 * posts. Everyone else sees a post only if it isn't a draft and the journal isn't owner view only,
 * and then public posts, or friends only posts when the owner has them on their friends list.
 *
 * @author Lucas Holt
 */
@Service
public class EntryAccessService {

  /**
   * @param viewerId the logged in user, or 0
   */
  public boolean canView(final Entry entry, final int viewerId) {
    if (entry == null || entry.getUser() == null) return false;
    final User owner = entry.getUser();

    // a disabled account's posts are hidden from everyone, the owner included
    if (owner.isDisabled()) return false;

    if (viewerId > 0 && owner.getId() == viewerId) return true;

    if (entry.getDraft() == PrefBool.Y || ownerViewOnly(owner)) return false;

    if (entry.getSecurity() == Security.PUBLIC) return true;

    // anonymous visitors only see public entries
    if (viewerId < 1) return false;

    if (entry.getSecurity() == Security.FRIENDS) {
      return owner.getFriends().stream()
          .anyMatch(f -> f.getFriend() != null && f.getFriend().getId() == viewerId);
    }
    return false;
  }

  /**
   * Whether anyone at all may see the post, so what belongs to it may be cached by shared caches
   * and go in feeds.
   */
  public boolean isPubliclyVisible(final Entry entry) {
    return canView(entry, 0);
  }

  /** A user has one journal; any that is owner view only hides the posts. */
  private static boolean ownerViewOnly(final User owner) {
    return owner.getJournals().stream().anyMatch(Journal::isOwnerViewOnly);
  }
}
