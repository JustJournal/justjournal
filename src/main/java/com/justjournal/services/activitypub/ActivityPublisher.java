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

import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.services.EntryAccessService;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Tells followers when a journal changes. Whatever saves or deletes an entry, changes what is
 * attached to one, or changes the profile calls in here; this notes what changed and hands it to
 * {@link ActivityPublishListener}, which decides what to send once the change is committed:
 *
 * <ul>
 *   <li>an entry that becomes publicly visible, new or not: Create
 *   <li>a publicly visible entry whose subject, body, format, tags, security or attachments
 *       changed: Update
 *   <li>a publicly visible entry that is deleted or hidden again: Delete
 *   <li>a new journal name, biography or avatar: Update of the actor
 * </ul>
 *
 * <p>An entry hidden and later made public again is sent as a Create under its old id. Mastodon
 * keeps a tombstone for an id it was told to delete and ignores the Create, so the entry doesn't
 * come back there; that is accepted rather than giving the entry a new id.
 *
 * <p>Only journals that federate ({@link FederationPolicy#federates}) at the time of the change
 * send anything. Nothing here throws: a problem is logged and the user's save goes ahead.
 */
@Slf4j
@Service
public class ActivityPublisher {

  /** What kind of change to an entry. */
  public enum Change {
    /** Saved, new or not. */
    SAVED,
    /** Only its picture or link changed, or how either may be shown. */
    ATTACHMENTS,
    /** Deleted. */
    DELETED
  }

  /**
   * What an entry looked like before a change, taken before the save: Hibernate updates a managed
   * entry in place, so it can't be looked at afterwards.
   *
   * @param visible whether anyone could see it
   * @param tags its tag names
   */
  public record EntryState(
      int entryId,
      boolean visible,
      String subject,
      String body,
      FormatType format,
      Security security,
      Set<String> tags) {

    static EntryState of(final Entry entry, final boolean visible) {
      return new EntryState(
          entry.getId(),
          visible,
          entry.getSubject(),
          entry.getBody(),
          entry.getFormat(),
          entry.getSecurity(),
          tagNames(entry));
    }

    /** Whether something the published object shows differs in the entry as it is now. */
    boolean differsFrom(final Entry entry) {
      return !Objects.equals(text(subject), text(entry.getSubject()))
          || !Objects.equals(text(body), text(entry.getBody()))
          || format != entry.getFormat()
          || security != entry.getSecurity()
          || !tags.equals(tagNames(entry));
    }

    private static String text(final String value) {
      return value == null ? "" : value;
    }

    private static Set<String> tagNames(final Entry entry) {
      if (entry.getTags() == null) return Set.of();
      return Set.copyOf(
          entry.getTags().stream()
              .filter(t -> t.getTag() != null)
              .map(t -> t.getTag().getName())
              .collect(Collectors.toCollection(TreeSet::new)));
    }
  }

  /**
   * An entry changed. Handled once the transaction it happened in commits.
   *
   * @param userId the owner, or 0 to take the owner from the entry
   * @param before the entry before the change, or null when it is new or only its attachments
   *     changed
   * @param attachmentsChanged whether its picture or link changed too
   * @param at when it changed
   */
  public record EntryChanged(
      Change change,
      int entryId,
      int userId,
      EntryState before,
      boolean attachmentsChanged,
      Date at) {}

  /**
   * The journal name, biography or avatar changed. Handled once the transaction it happened in
   * commits.
   */
  public record ProfileChanged(int userId, Date at) {}

  private final FederationPolicy federationPolicy;
  private final EntryAccessService entryAccessService;
  private final ApplicationEventPublisher events;

  public ActivityPublisher(
      final FederationPolicy federationPolicy,
      final EntryAccessService entryAccessService,
      final ApplicationEventPublisher events) {
    this.federationPolicy = federationPolicy;
    this.entryAccessService = entryAccessService;
    this.events = events;
  }

  /**
   * Note what an entry looks like before it is changed. Call before the entry is modified or saved.
   *
   * @param entry the stored entry, or null
   * @return its state, or null when there is no entry or the site doesn't federate
   */
  public EntryState capture(final Entry entry) {
    if (entry == null || !federationPolicy.isEnabled()) return null;
    try {
      return EntryState.of(entry, visible(entry));
    } catch (final RuntimeException e) {
      log.warn("Could not note entry {} before a change", entry.getId(), e);
      return null;
    }
  }

  /** An entry was saved, with no change to its picture or link. */
  public void entrySaved(final EntryState before, final Entry after) {
    entrySaved(before, after, false);
  }

  /**
   * An entry was saved.
   *
   * @param before from {@link #capture} before the save, or null for a new entry
   * @param after the saved entry
   * @param attachmentsChanged whether its picture or link changed in the same save
   */
  public void entrySaved(
      final EntryState before, final Entry after, final boolean attachmentsChanged) {
    if (after == null || !federationPolicy.isEnabled()) return;
    try {
      if (after.getId() < 1 || after.getUser() == null) return;
      publish(
          new EntryChanged(
              Change.SAVED,
              after.getId(),
              after.getUser().getId(),
              before,
              attachmentsChanged,
              new Date()));
    } catch (final RuntimeException e) {
      log.warn("Could not publish the save of entry {}", after.getId(), e);
    }
  }

  /**
   * An entry was deleted. May be called just before or after the delete; only the entry's own
   * fields are read.
   */
  public void entryDeleted(final Entry entry) {
    if (entry == null || !federationPolicy.isEnabled()) return;
    try {
      if (entry.getId() < 1 || entry.getUser() == null) return;
      // nobody else could see it, so nobody else has it
      if (!visible(entry)) return;
      publish(
          new EntryChanged(
              Change.DELETED,
              entry.getId(),
              entry.getUser().getId(),
              EntryState.of(entry, true),
              false,
              new Date()));
    } catch (final RuntimeException e) {
      log.warn("Could not publish the delete of entry {}", entry.getId(), e);
    }
  }

  /**
   * What may be shown of an entry's picture or link changed outside a save of the entry: a picture
   * was approved or deleted, or a link's preview finished or was blocked.
   */
  public void entryAttachmentsChanged(final int entryId) {
    if (entryId < 1 || !federationPolicy.isEnabled()) return;
    try {
      publish(new EntryChanged(Change.ATTACHMENTS, entryId, 0, null, true, new Date()));
    } catch (final RuntimeException e) {
      log.warn("Could not publish the attachments of entry {}", entryId, e);
    }
  }

  /** The user's journal name, biography or avatar changed. */
  public void profileChanged(final User user) {
    if (user != null) profileChanged(user.getId());
  }

  /** The user's journal name, biography or avatar changed. */
  public void profileChanged(final int userId) {
    if (userId < 1 || !federationPolicy.isEnabled()) return;
    try {
      publish(new ProfileChanged(userId, new Date()));
    } catch (final RuntimeException e) {
      log.warn("Could not publish the profile of user {}", userId, e);
    }
  }

  private void publish(final Object event) {
    events.publishEvent(event);
  }

  /**
   * {@link EntryAccessService#isPubliclyVisible}. Outside a session, such as an XML-RPC call, the
   * owner's journals may not load; then only the entry's own part is checked, since the journal's
   * part (not owner view only) is checked again by {@link FederationPolicy#federates} when the
   * change is published.
   */
  private boolean visible(final Entry entry) {
    try {
      return entryAccessService.isPubliclyVisible(entry);
    } catch (final RuntimeException e) {
      log.debug("Checking only entry {} itself: {}", entry.getId(), e.getMessage());
      return entry.getSecurity() == Security.PUBLIC && entry.getDraft() == PrefBool.N;
    }
  }
}
