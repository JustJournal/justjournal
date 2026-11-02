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

import com.justjournal.model.RemoteFollower;
import com.justjournal.model.User;
import com.justjournal.repository.RemoteFollowerRepository;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who on other servers follows a journal, and where activities for them go. The list is private:
 * only {@link #count} is meant for anything public.
 */
@Service
public class FollowerService {

  private final RemoteFollowerRepository repository;

  public FollowerService(final RemoteFollowerRepository repository) {
    this.repository = repository;
  }

  /**
   * Add a follower, or update the inboxes of one already there.
   *
   * @param user the followed journal's owner
   * @param actorId the remote actor's id
   * @param inbox the remote actor's inbox
   * @param sharedInbox the remote server's shared inbox, or null
   * @return the stored follower
   * @throws IllegalArgumentException if a URL isn't an absolute http(s) URL of a sensible length
   */
  @Transactional
  public RemoteFollower add(
      final User user, final URI actorId, final URI inbox, final URI sharedInbox) {
    final String actor = url(actorId, "actor id");
    final String inboxUrl = url(inbox, "inbox");
    final String shared = sharedInbox == null ? null : url(sharedInbox, "shared inbox");

    final RemoteFollower follower =
        repository
            .findByUserIdAndActorId(user.getId(), actor)
            .orElseGet(
                () -> {
                  final RemoteFollower created = new RemoteFollower();
                  created.setUserId(user.getId());
                  created.setActorId(actor);
                  return created;
                });
    follower.setInbox(inboxUrl);
    follower.setSharedInbox(shared);
    return repository.save(follower);
  }

  /**
   * Remove a follower.
   *
   * @return true if the actor was following
   */
  @Transactional
  public boolean remove(final User user, final URI actorId) {
    if (actorId == null) return false;
    return repository.deleteByUserIdAndActorId(user.getId(), actorId.toASCIIString()) > 0;
  }

  /** Everyone following the user's journal, oldest first. Never show this publicly. */
  @Transactional(readOnly = true)
  public List<RemoteFollower> list(final User user) {
    return repository.findByUserIdOrderByIdAsc(user.getId());
  }

  /** How many accounts follow the user's journal. */
  @Transactional(readOnly = true)
  public long count(final User user) {
    return repository.countByUserId(user.getId());
  }

  /**
   * The inboxes an activity for all of a user's followers goes to: each server's shared inbox when
   * it has one, otherwise the follower's own inbox, each only once.
   */
  @Transactional(readOnly = true)
  public Set<URI> deliveryInboxes(final User user) {
    final Set<URI> inboxes = new LinkedHashSet<>();
    for (final RemoteFollower follower : list(user)) {
      final String target =
          follower.getSharedInbox() == null || follower.getSharedInbox().isBlank()
              ? follower.getInbox()
              : follower.getSharedInbox();
      try {
        inboxes.add(URI.create(target));
      } catch (final IllegalArgumentException e) {
        // stored values were checked by add(); skip anything that slipped past
      }
    }
    return Collections.unmodifiableSet(inboxes);
  }

  static String url(final URI uri, final String what) {
    if (uri == null || !uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null) {
      throw new IllegalArgumentException("The " + what + " is not an absolute URL");
    }
    final String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !scheme.equals("http")) {
      throw new IllegalArgumentException("The " + what + " is not an http(s) URL");
    }
    final String text = uri.toASCIIString();
    if (text.length() > RemoteFollower.MAX_URL_LENGTH) {
      throw new IllegalArgumentException("The " + what + " is too long");
    }
    return text;
  }
}
