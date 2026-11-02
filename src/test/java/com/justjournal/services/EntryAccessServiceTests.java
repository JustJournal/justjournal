package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.model.Entry;
import com.justjournal.model.Friend;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EntryAccessServiceTests {

  static final int OWNER = 5;
  static final int FRIEND = 6;
  static final int STRANGER = 7;

  private final EntryAccessService service = new EntryAccessService();

  /** A post by user 5, who has user 6 on their friends list. */
  static Entry entry(final Security security, final PrefBool draft, final boolean ownerViewOnly) {
    final User friend = mock(User.class);
    when(friend.getId()).thenReturn(FRIEND);
    final Friend link = new Friend();
    link.setFriend(friend);

    final Journal journal = new Journal();
    journal.setOwnerViewOnly(ownerViewOnly);

    final User owner = mock(User.class);
    when(owner.getId()).thenReturn(OWNER);
    when(owner.getFriends()).thenReturn(Set.of(link));
    when(owner.getJournals()).thenReturn(Set.of(journal));

    final Entry entry = new Entry();
    entry.setId(30);
    entry.setUser(owner);
    entry.setSecurity(security);
    entry.setDraft(draft);
    return entry;
  }

  static Entry entry(final Security security) {
    return entry(security, PrefBool.N, false);
  }

  @Test
  void owner_seesEverything() {
    assertTrue(service.canView(entry(Security.PUBLIC), OWNER));
    assertTrue(service.canView(entry(Security.FRIENDS), OWNER));
    assertTrue(service.canView(entry(Security.PRIVATE), OWNER));
    assertTrue(service.canView(entry(Security.PRIVATE, PrefBool.Y, true), OWNER));
  }

  @Test
  void anonymous_seesOnlyPublic() {
    assertTrue(service.canView(entry(Security.PUBLIC), 0));
    assertFalse(service.canView(entry(Security.FRIENDS), 0));
    assertFalse(service.canView(entry(Security.PRIVATE), 0));
  }

  @Test
  void friend_seesPublicAndFriendsOnly() {
    assertTrue(service.canView(entry(Security.PUBLIC), FRIEND));
    assertTrue(service.canView(entry(Security.FRIENDS), FRIEND));
    assertFalse(service.canView(entry(Security.PRIVATE), FRIEND));
  }

  @Test
  void stranger_seesOnlyPublic() {
    assertTrue(service.canView(entry(Security.PUBLIC), STRANGER));
    assertFalse(service.canView(entry(Security.FRIENDS), STRANGER));
    assertFalse(service.canView(entry(Security.PRIVATE), STRANGER));
  }

  @Test
  void draft_onlyOwner() {
    final Entry draft = entry(Security.PUBLIC, PrefBool.Y, false);

    assertTrue(service.canView(draft, OWNER));
    assertFalse(service.canView(draft, 0));
    assertFalse(service.canView(draft, FRIEND));
    assertFalse(service.isPubliclyVisible(draft));
  }

  @Test
  void ownerViewOnlyJournal_onlyOwner() {
    final Entry hidden = entry(Security.PUBLIC, PrefBool.N, true);

    assertTrue(service.canView(hidden, OWNER));
    assertFalse(service.canView(hidden, 0));
    assertFalse(service.canView(hidden, FRIEND));
    assertFalse(service.isPubliclyVisible(hidden));
  }

  @Test
  void publiclyVisible_onlyPublicPosts() {
    assertTrue(service.isPubliclyVisible(entry(Security.PUBLIC)));
    assertFalse(service.isPubliclyVisible(entry(Security.FRIENDS)));
    assertFalse(service.isPubliclyVisible(entry(Security.PRIVATE)));
  }

  @Test
  void noPost_nobody() {
    assertFalse(service.canView(null, OWNER));
    assertFalse(service.canView(new Entry(), OWNER));
  }
}
