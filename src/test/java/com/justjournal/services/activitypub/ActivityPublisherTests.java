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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.FormatType;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.Tag;
import com.justjournal.model.User;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.activitypub.ActivityPublisher.Change;
import com.justjournal.services.activitypub.ActivityPublisher.EntryChanged;
import com.justjournal.services.activitypub.ActivityPublisher.EntryState;
import com.justjournal.services.activitypub.ActivityPublisher.ProfileChanged;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** What the publisher notes about a change, before it is handed on to be sent after commit. */
class ActivityPublisherTests {

  private FederationPolicy policy;
  private ApplicationEventPublisher events;
  private ActivityPublisher publisher;
  private User bob;
  private Journal journal;

  @BeforeEach
  void setUp() {
    policy = new FederationPolicy();
    policy.setEnabled(true);
    events = mock(ApplicationEventPublisher.class);
    publisher = new ActivityPublisher(policy, new EntryAccessService(), events);

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    journal = new Journal();
    journal.setUser(bob);
    bob.getJournals().add(journal);
  }

  private Entry entry(final Security security, final PrefBool draft) {
    final Entry entry = new Entry();
    entry.setId(4);
    entry.setUser(bob);
    entry.setSecurity(security);
    entry.setDraft(draft);
    entry.setSubject("Hello");
    entry.setBody("Body");
    entry.setFormat(FormatType.HTML);
    final Set<EntryTag> tags = new LinkedHashSet<>();
    for (final String name : new String[] {"java", "bsd"}) {
      final EntryTag tag = new EntryTag();
      tag.setTag(new Tag(name));
      tags.add(tag);
    }
    entry.setTags(tags);
    return entry;
  }

  private <T> T published(final Class<T> type) {
    final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(captor.capture());
    return type.cast(captor.getValue());
  }

  // ---- capture ----------------------------------------------------------------------------

  @Test
  void capture_notesVisibilityAndWhatTheObjectShows() {
    final EntryState state = publisher.capture(entry(Security.PUBLIC, PrefBool.N));

    assertEquals(4, state.entryId());
    assertTrue(state.visible());
    assertEquals("Hello", state.subject());
    assertEquals("Body", state.body());
    assertEquals(FormatType.HTML, state.format());
    assertEquals(Security.PUBLIC, state.security());
    assertEquals(Set.of("java", "bsd"), state.tags());
  }

  @Test
  void capture_hiddenEntries_notVisible() {
    assertFalse(publisher.capture(entry(Security.FRIENDS, PrefBool.N)).visible());
    assertFalse(publisher.capture(entry(Security.PRIVATE, PrefBool.N)).visible());
    assertFalse(publisher.capture(entry(Security.PUBLIC, PrefBool.Y)).visible());
    journal.setOwnerViewOnly(true);
    assertFalse(publisher.capture(entry(Security.PUBLIC, PrefBool.N)).visible());
  }

  @Test
  void capture_isACopy_laterChangesDoNotShow() {
    final Entry entry = entry(Security.PUBLIC, PrefBool.N);
    final EntryState state = publisher.capture(entry);

    entry.setBody("Changed");
    entry.setSecurity(Security.PRIVATE);

    assertEquals("Body", state.body());
    assertTrue(state.visible());
    assertTrue(state.differsFrom(entry));
  }

  @Test
  void capture_nothingWhenTheSiteDoesNotFederateOrThereIsNoEntry() {
    assertNull(publisher.capture(null));
    policy.setEnabled(false);
    assertNull(publisher.capture(entry(Security.PUBLIC, PrefBool.N)));
  }

  @Test
  void differsFrom_onlyWhatTheObjectShows() {
    final Entry entry = entry(Security.PUBLIC, PrefBool.N);
    final EntryState state = publisher.capture(entry);

    // mood, music, comments and the like aren't in the object
    entry.setMusic("Something");
    entry.setAllowComments(PrefBool.N);
    assertFalse(state.differsFrom(entry));

    entry.setSubject("Other");
    assertTrue(state.differsFrom(entry));
    entry.setSubject("Hello");

    entry.setFormat(FormatType.MARKDOWN);
    assertTrue(state.differsFrom(entry));
    entry.setFormat(FormatType.HTML);

    entry.getTags().clear();
    assertTrue(state.differsFrom(entry));
  }

  @Test
  void differsFrom_nullAndEmptySubjectAreTheSame() {
    final Entry entry = entry(Security.PUBLIC, PrefBool.N);
    entry.setSubject(null);
    final EntryState state = publisher.capture(entry);
    entry.setSubject("");

    assertFalse(state.differsFrom(entry));
  }

  // ---- saves ------------------------------------------------------------------------------

  @Test
  void entrySaved_handsOnTheChange() {
    final Entry entry = entry(Security.PUBLIC, PrefBool.N);
    final EntryState before = publisher.capture(entry);

    publisher.entrySaved(before, entry, true);

    final EntryChanged change = published(EntryChanged.class);
    assertEquals(Change.SAVED, change.change());
    assertEquals(4, change.entryId());
    assertEquals(7, change.userId());
    assertEquals(before, change.before());
    assertTrue(change.attachmentsChanged());
  }

  @Test
  void entrySaved_newEntry_hasNothingBefore() {
    publisher.entrySaved(null, entry(Security.PUBLIC, PrefBool.N));

    final EntryChanged change = published(EntryChanged.class);
    assertNull(change.before());
    assertFalse(change.attachmentsChanged());
  }

  @Test
  void entrySaved_siteNotFederatingOrNotSaved_nothingHandedOn() {
    final Entry unsaved = entry(Security.PUBLIC, PrefBool.N);
    unsaved.setId(0);
    publisher.entrySaved(null, unsaved);
    publisher.entrySaved(null, null);

    policy.setEnabled(false);
    publisher.entrySaved(null, entry(Security.PUBLIC, PrefBool.N));
    publisher.entryDeleted(entry(Security.PUBLIC, PrefBool.N));
    publisher.entryAttachmentsChanged(4);
    publisher.profileChanged(bob);

    verify(events, never()).publishEvent(any());
  }

  @Test
  void publishingFails_theSaveGoesAhead() {
    doThrow(new IllegalStateException("no executor")).when(events).publishEvent(any());

    assertDoesNotThrow(() -> publisher.entrySaved(null, entry(Security.PUBLIC, PrefBool.N)));
    assertDoesNotThrow(() -> publisher.entryDeleted(entry(Security.PUBLIC, PrefBool.N)));
    assertDoesNotThrow(() -> publisher.entryAttachmentsChanged(4));
    assertDoesNotThrow(() -> publisher.profileChanged(7));
  }

  // ---- deletes ----------------------------------------------------------------------------

  @Test
  void entryDeleted_publicEntry_handedOnWithItsLastState() {
    publisher.entryDeleted(entry(Security.PUBLIC, PrefBool.N));

    final EntryChanged change = published(EntryChanged.class);
    assertEquals(Change.DELETED, change.change());
    assertEquals(4, change.entryId());
    assertEquals(7, change.userId());
    assertTrue(change.before().visible());
  }

  @Test
  void entryDeleted_hiddenEntry_nobodyElseHadIt() {
    publisher.entryDeleted(entry(Security.PRIVATE, PrefBool.N));
    publisher.entryDeleted(entry(Security.PUBLIC, PrefBool.Y));

    verify(events, never()).publishEvent(any());
  }

  @Test
  void entryDeleted_outsideASession_checksTheEntryItself() {
    // XML-RPC runs without an open session, so the owner's journals may not load
    final User detached = mock(User.class);
    when(detached.getId()).thenReturn(7);
    when(detached.getJournals()).thenThrow(new LazyInitializationException("no session"));
    final Entry entry = entry(Security.PUBLIC, PrefBool.N);
    entry.setUser(detached);

    publisher.entryDeleted(entry);

    assertTrue(published(EntryChanged.class).before().visible());
  }

  // ---- attachments and profile ------------------------------------------------------------

  @Test
  void entryAttachmentsChanged_handedOnWithoutABefore() {
    publisher.entryAttachmentsChanged(4);

    final EntryChanged change = published(EntryChanged.class);
    assertEquals(Change.ATTACHMENTS, change.change());
    assertEquals(4, change.entryId());
    assertEquals(0, change.userId());
    assertNull(change.before());
    assertTrue(change.attachmentsChanged());
  }

  @Test
  void profileChanged_handedOn() {
    publisher.profileChanged(bob);

    assertEquals(7, published(ProfileChanged.class).userId());
  }
}
