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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.core.Settings;
import com.justjournal.model.ActorKey;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.FormatType;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.Tag;
import com.justjournal.model.User;
import com.justjournal.model.UserBio;
import com.justjournal.model.UserPref;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.AvatarService;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.EntryService;
import com.justjournal.services.activitypub.ActivityPublisher.Change;
import com.justjournal.services.activitypub.ActivityPublisher.EntryChanged;
import com.justjournal.services.activitypub.ActivityPublisher.EntryState;
import com.justjournal.services.activitypub.ActivityPublisher.ProfileChanged;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.RestrictedTransactionalEventListenerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * Which activity each change to a journal sends its followers, what the activity says, and that it
 * goes out only once the change is committed.
 */
class ActivityPublishListenerTests {

  private static final String ACTOR = "https://www.justjournal.com/users/bob";
  private static final String OBJECT = ACTOR + "/entry/4";
  private static final Date AT = new Date(1_800_000_000_000L);
  private static final String PEM =
      "-----BEGIN PUBLIC KEY-----\nMIIBIjANBg...\n-----END PUBLIC KEY-----\n";

  private final ObjectMapper mapper = new ObjectMapper();
  private FederationPolicy policy;
  private UserRepository userRepository;
  private EntryRepository entryRepository;
  private EntryAttachmentService entryAttachmentService;
  private AvatarService avatarService;
  private DeliveryService deliveryService;
  private ActivityStreamsObjects objects;
  private ActivityPublisher publisher;
  private ActivityPublishListener listener;
  private User bob;
  private UserPref pref;
  private Journal journal;
  private Entry entry;

  @BeforeEach
  void setUp() {
    policy = new FederationPolicy();
    policy.setEnabled(true);
    userRepository = mock(UserRepository.class);
    entryRepository = mock(EntryRepository.class);
    entryAttachmentService = mock(EntryAttachmentService.class);
    avatarService = mock(AvatarService.class);
    deliveryService = mock(DeliveryService.class);
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("http://www.justjournal.com/");
    final EntryService entryService = mock(EntryService.class);
    when(entryService.convertBodyToHtml(any(), anyString()))
        .thenAnswer(inv -> "<p>" + inv.getArgument(1) + "</p>");
    final ActorKeyService actorKeyService = mock(ActorKeyService.class);
    final ActorKey key = new ActorKey();
    key.setPublicKeyPem(PEM);
    when(actorKeyService.forUser(any())).thenReturn(key);
    when(entryAttachmentService.forFeed(anyCollection())).thenReturn(Map.of());

    final EntryAccessService access = new EntryAccessService();
    objects =
        new ActivityStreamsObjects(
            settings, entryService, avatarService, mock(UserPicRepository.class), actorKeyService);
    listener =
        new ActivityPublishListener(
            policy,
            userRepository,
            entryRepository,
            access,
            entryAttachmentService,
            objects,
            deliveryService);
    publisher = new ActivityPublisher(policy, access, mock(ApplicationEventPublisher.class));

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    pref = new UserPref();
    pref.setFederate(PrefBool.Y);
    ReflectionTestUtils.setField(bob, "userPref", pref);
    journal = new Journal();
    journal.setName("Bob's Blog");
    journal.setUser(bob);
    bob.getJournals().add(journal);
    when(userRepository.findById(7)).thenReturn(Optional.of(bob));

    entry = new Entry();
    entry.setId(4);
    entry.setUser(bob);
    entry.setSecurity(Security.PUBLIC);
    entry.setDraft(PrefBool.N);
    entry.setDate(new Date(1_700_000_000_000L));
    entry.setModified(new Date(1_700_000_000_000L));
    entry.setSubject("Hello");
    entry.setBody("Body");
    entry.setFormat(FormatType.HTML);
    tag("java");
    when(entryRepository.findById(4)).thenReturn(Optional.of(entry));
  }

  @AfterEach
  void clearTransaction() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  private void tag(final String... names) {
    final Set<EntryTag> tags = new LinkedHashSet<>();
    for (final String name : names) {
      final EntryTag entryTag = new EntryTag();
      entryTag.setTag(new Tag(name));
      tags.add(entryTag);
    }
    entry.setTags(tags);
  }

  /** The stored entry as it is now, as the publisher notes it before a change. */
  private EntryState before() {
    return publisher.capture(entry);
  }

  private static EntryChanged saved(final EntryState before) {
    return new EntryChanged(Change.SAVED, 4, 7, before, false, AT);
  }

  private JsonNode sent() throws Exception {
    final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(deliveryService).enqueueToFollowers(eq(bob), json.capture());
    return mapper.readTree(json.getValue());
  }

  private void nothingSent() {
    verify(deliveryService, never()).enqueueToFollowers(any(), any());
  }

  // ---- becoming visible: Create -----------------------------------------------------------

  @Test
  void newPublicEntry_create() throws Exception {
    assertNotNull(listener.publish(saved(null)));

    final JsonNode create = sent();
    assertEquals(ActivityStreamsObjects.AS_CONTEXT, create.get("@context").asText());
    assertEquals(OBJECT + "#create", create.get("id").asText());
    assertEquals("Create", create.get("type").asText());
    assertEquals(ACTOR, create.get("actor").asText());
    assertEquals(ActivityStreamsObjects.PUBLIC, create.get("to").get(0).asText());
    assertEquals(ACTOR + "/followers", create.get("cc").get(0).asText());
    final JsonNode object = create.get("object");
    assertEquals(OBJECT, object.get("id").asText());
    assertEquals("Article", object.get("type").asText());
    assertEquals("Hello", object.get("name").asText());
    assertEquals("<p>Body</p>", object.get("content").asText());
    assertEquals("#java", object.get("tag").get(0).get("name").asText());
  }

  @Test
  void createMatchesTheOutbox_sameIdAndObject() throws Exception {
    final ObjectNode outbox = objects.create(entry, null);

    listener.publish(saved(null));

    final JsonNode create = sent();
    assertEquals(outbox.get("id"), create.get("id"));
    assertEquals(outbox.get("object"), create.get("object"));
  }

  @Test
  void draftPublished_create() throws Exception {
    entry.setDraft(PrefBool.Y);
    final EntryState before = before();
    entry.setDraft(PrefBool.N);

    listener.publish(saved(before));

    assertEquals("Create", sent().get("type").asText());
  }

  @Test
  void friendsOnlyOrPrivateMadePublic_create() throws Exception {
    entry.setSecurity(Security.FRIENDS);
    final EntryState friends = before();
    entry.setSecurity(Security.PRIVATE);
    final EntryState hidden = before();
    entry.setSecurity(Security.PUBLIC);

    listener.publish(saved(friends));
    listener.publish(saved(hidden));

    final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(deliveryService, times(2)).enqueueToFollowers(eq(bob), json.capture());
    for (final String activity : json.getAllValues()) {
      assertEquals("Create", mapper.readTree(activity).get("type").asText());
    }
  }

  @Test
  void createCarriesTheAttachmentsAnyoneMaySee() throws Exception {
    when(entryAttachmentService.forFeed(List.of(entry)))
        .thenReturn(
            Map.of(
                4,
                new FeedAttachments(
                    "https://www.justjournal.com/EntryImage/3",
                    null,
                    "image/png",
                    10,
                    1,
                    1,
                    "A cat",
                    null,
                    null,
                    null,
                    null,
                    0,
                    0)));

    listener.publish(saved(null));

    final JsonNode image = sent().get("object").get("attachment").get(0);
    assertEquals("Image", image.get("type").asText());
    assertEquals("A cat", image.get("name").asText());
  }

  // ---- visible before and after: Update or nothing -----------------------------------------

  @Test
  void publicEntryEdited_update() throws Exception {
    final EntryState before = before();
    entry.setBody("New body");

    listener.publish(saved(before));

    final JsonNode update = sent();
    assertEquals(ActivityStreamsObjects.AS_CONTEXT, update.get("@context").asText());
    assertEquals(OBJECT + "#update/" + AT.getTime(), update.get("id").asText());
    assertEquals("Update", update.get("type").asText());
    assertEquals(ACTOR, update.get("actor").asText());
    assertEquals(ActivityStreamsObjects.PUBLIC, update.get("to").get(0).asText());
    assertEquals(ACTOR + "/followers", update.get("cc").get(0).asText());
    final JsonNode object = update.get("object");
    assertEquals(OBJECT, object.get("id").asText());
    assertEquals("<p>New body</p>", object.get("content").asText());
    // servers need an edit time later than the last one they saw
    assertEquals(update.get("published").asText(), object.get("updated").asText());
    assertTrue(object.get("updated").asText().startsWith("2027-01-15"), object.toString());
  }

  @Test
  void subjectFormatOrTagsChanged_update() throws Exception {
    EntryState before = before();
    entry.setSubject("");
    listener.publish(saved(before));

    before = before();
    entry.setFormat(FormatType.MARKDOWN);
    listener.publish(saved(before));

    before = before();
    tag("java", "bsd");
    listener.publish(saved(before));

    final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(deliveryService, times(3)).enqueueToFollowers(eq(bob), json.capture());
    for (final String activity : json.getAllValues()) {
      assertEquals("Update", mapper.readTree(activity).get("type").asText());
    }
  }

  @Test
  void unrelatedSave_nothingSent() {
    final EntryState before = before();
    entry.setMusic("Something else");
    entry.setAllowComments(PrefBool.N);
    entry.setModified(new Date());

    assertNull(listener.publish(saved(before)));

    nothingSent();
  }

  @Test
  void attachmentsChangedInTheSave_update() throws Exception {
    listener.publish(new EntryChanged(Change.SAVED, 4, 7, before(), true, AT));

    assertEquals("Update", sent().get("type").asText());
  }

  @Test
  void attachmentsChangedOnTheirOwn_update() throws Exception {
    listener.publish(new EntryChanged(Change.ATTACHMENTS, 4, 0, null, true, AT));

    assertEquals("Update", sent().get("type").asText());
  }

  @Test
  void attachmentsChangedOnAHiddenEntry_nothingSent() {
    entry.setSecurity(Security.FRIENDS);

    assertNull(listener.publish(new EntryChanged(Change.ATTACHMENTS, 4, 0, null, true, AT)));

    nothingSent();
  }

  // ---- no longer visible: Delete ----------------------------------------------------------

  @Test
  void publicEntryDeleted_deleteWithATombstone() throws Exception {
    final EntryState before = before();
    when(entryRepository.findById(4)).thenReturn(Optional.empty());

    listener.publish(new EntryChanged(Change.DELETED, 4, 7, before, false, AT));

    final JsonNode delete = sent();
    assertEquals(ActivityStreamsObjects.AS_CONTEXT, delete.get("@context").asText());
    assertEquals(OBJECT + "#delete/" + AT.getTime(), delete.get("id").asText());
    assertEquals("Delete", delete.get("type").asText());
    assertEquals(ACTOR, delete.get("actor").asText());
    assertEquals(ActivityStreamsObjects.PUBLIC, delete.get("to").get(0).asText());
    assertEquals(ACTOR + "/followers", delete.get("cc").get(0).asText());
    assertEquals(OBJECT, delete.get("object").get("id").asText());
    assertEquals("Tombstone", delete.get("object").get("type").asText());
  }

  @Test
  void madePrivateFriendsOnlyOrDraft_delete() throws Exception {
    for (final Runnable hide :
        List.<Runnable>of(
            () -> entry.setSecurity(Security.PRIVATE),
            () -> entry.setSecurity(Security.FRIENDS),
            () -> entry.setDraft(PrefBool.Y))) {
      entry.setSecurity(Security.PUBLIC);
      entry.setDraft(PrefBool.N);
      final EntryState before = before();
      hide.run();
      listener.publish(saved(before));
    }

    final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(deliveryService, times(3)).enqueueToFollowers(eq(bob), json.capture());
    for (final String activity : json.getAllValues()) {
      assertEquals("Delete", mapper.readTree(activity).get("type").asText());
    }
  }

  @Test
  void hiddenBeforeAndAfter_nothingSent() {
    entry.setSecurity(Security.PRIVATE);
    final EntryState before = before();
    entry.setBody("Changed");
    listener.publish(saved(before));
    listener.publish(saved(null));

    nothingSent();
  }

  // ---- who federates ------------------------------------------------------------------------

  @Test
  void siteOff_notOptedIn_orOwnerOnly_nothingSent() {
    policy.setEnabled(false);
    assertNull(listener.publish(saved(null)));
    policy.setEnabled(true);

    pref.setFederate(PrefBool.N);
    assertNull(listener.publish(saved(null)));
    assertNull(listener.publish(new ProfileChanged(7, AT)));
    pref.setFederate(PrefBool.Y);

    journal.setOwnerViewOnly(true);
    assertNull(listener.publish(saved(null)));
    assertNull(listener.publish(new EntryChanged(Change.DELETED, 4, 7, before(), false, AT)));
    assertNull(listener.publish(new ProfileChanged(7, AT)));

    nothingSent();
  }

  @Test
  void ownerGone_nothingSent() {
    when(userRepository.findById(7)).thenReturn(Optional.empty());

    assertNull(listener.publish(saved(null)));

    nothingSent();
  }

  // ---- profile ------------------------------------------------------------------------------

  @Test
  void profileChanged_updateOfTheActor() throws Exception {
    final UserBio bio = new UserBio();
    bio.setBio("Writes about BSD");
    bob.setBio(bio);

    listener.publish(new ProfileChanged(7, AT));

    final JsonNode update = sent();
    assertEquals(ActivityStreamsObjects.AS_CONTEXT, update.get("@context").get(0).asText());
    assertEquals(ActivityStreamsObjects.SECURITY_CONTEXT, update.get("@context").get(1).asText());
    assertEquals(ACTOR + "#update/" + AT.getTime(), update.get("id").asText());
    assertEquals("Update", update.get("type").asText());
    assertEquals(ACTOR, update.get("actor").asText());
    assertEquals(ActivityStreamsObjects.PUBLIC, update.get("to").get(0).asText());
    final JsonNode actor = update.get("object");
    assertFalse(actor.has("@context"));
    assertEquals(ACTOR, actor.get("id").asText());
    assertEquals("Person", actor.get("type").asText());
    assertEquals("Bob's Blog", actor.get("name").asText());
    assertEquals("<p>Writes about BSD</p>", actor.get("summary").asText());
    assertEquals(PEM, actor.get("publicKey").get("publicKeyPem").asText());
  }

  // ---- never breaking the caller ------------------------------------------------------------

  @Test
  void failure_loggedNotThrown() {
    when(entryRepository.findById(4)).thenThrow(new IllegalStateException("db"));
    when(userRepository.findById(7)).thenThrow(new IllegalStateException("db"));

    assertDoesNotThrow(() -> listener.onEntryChanged(saved(null)));
    assertDoesNotThrow(() -> listener.onProfileChanged(new ProfileChanged(7, AT)));
  }

  @Test
  void handlersRunAsyncInTheirOwnTransaction() throws Exception {
    for (final String name : List.of("onEntryChanged", "onProfileChanged")) {
      final Method method =
          List.of(ActivityPublishListener.class.getMethods()).stream()
              .filter(m -> m.getName().equals(name))
              .findFirst()
              .orElseThrow();
      assertNotNull(method.getAnnotation(Async.class), name);
      assertEquals(
          Propagation.REQUIRES_NEW, method.getAnnotation(Transactional.class).propagation(), name);
    }
  }

  // ---- after commit -------------------------------------------------------------------------

  /** A context that wires the publisher to the listener the way the application does. */
  private ActivityPublisher wired(final AnnotationConfigApplicationContext context) {
    context.registerBean(RestrictedTransactionalEventListenerFactory.class);
    context.registerBean(ActivityPublishListener.class, () -> listener);
    context.registerBean(
        ActivityPublisher.class,
        () -> new ActivityPublisher(policy, new EntryAccessService(), context));
    context.refresh();
    return context.getBean(ActivityPublisher.class);
  }

  @Test
  void insideATransaction_sentOnlyOnceItCommits() throws Exception {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      final ActivityPublisher wired = wired(context);
      TransactionSynchronizationManager.initSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(true);

      wired.entrySaved(null, entry);
      nothingSent();

      TransactionSynchronizationUtils.invokeAfterCompletion(
          TransactionSynchronizationManager.getSynchronizations(),
          TransactionSynchronization.STATUS_COMMITTED);
      verify(deliveryService).enqueueToFollowers(eq(bob), contains("\"type\":\"Create\""));
    }
  }

  @Test
  void insideATransaction_rolledBack_nothingSent() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      final ActivityPublisher wired = wired(context);
      TransactionSynchronizationManager.initSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(true);

      wired.entrySaved(null, entry);
      wired.profileChanged(bob);
      TransactionSynchronizationUtils.invokeAfterCompletion(
          TransactionSynchronizationManager.getSynchronizations(),
          TransactionSynchronization.STATUS_ROLLED_BACK);

      nothingSent();
    }
  }

  @Test
  void withoutATransaction_sentStraightAway() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      final ActivityPublisher wired = wired(context);

      wired.entrySaved(null, entry);
      wired.profileChanged(bob);

      verify(deliveryService).enqueueToFollowers(eq(bob), contains("\"type\":\"Create\""));
      verify(deliveryService).enqueueToFollowers(eq(bob), contains("\"type\":\"Person\""));
    }
  }
}
