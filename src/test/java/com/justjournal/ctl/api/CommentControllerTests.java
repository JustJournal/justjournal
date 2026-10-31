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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.Comment;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.Friend;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.model.api.CommentTo;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.SettingsRepository;
import com.justjournal.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
class CommentControllerTests {

  private static final int ENTRY_ID = 10;
  private static final int COMMENT_ID = 20;
  private static final int OWNER_ID = 1;
  private static final int OTHER_ID = 2;

  @Mock private CommentRepository commentRepository;
  @Mock private EntryRepository entryRepository;
  @Mock private UserRepository userRepository;
  @Mock private SettingsRepository settingsRepository;
  @Mock private QueueMailRepository queueMailRepository;

  @InjectMocks private CommentController commentController;

  private MockHttpSession session;
  private MockHttpServletResponse response;
  private User owner;
  private Journal journal;
  private Entry entry;
  private Comment comment;
  private User commenter;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    response = new MockHttpServletResponse();

    owner = new User();
    owner.setId(OWNER_ID);
    owner.setUsername("owner");
    journal = Journal.builder().ownerViewOnly(false).build();
    owner.setJournals(Set.of(journal));

    entry = new Entry();
    entry.setId(ENTRY_ID);
    entry.setUser(owner);
    entry.setSecurity(Security.PUBLIC);
    entry.setAllowComments(PrefBool.Y);

    commenter = new User();
    commenter.setId(3);
    commenter.setUsername("commenter");

    comment =
        Comment.builder()
            .id(COMMENT_ID)
            .subject("hi")
            .body("secret body")
            .format(FormatType.TEXT)
            .entry(entry)
            .user(commenter)
            .build();

    lenient().when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));
    lenient().when(commentRepository.findByEntryId(ENTRY_ID)).thenReturn(List.of(comment));
    lenient().when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
  }

  private void login(final int id, final String username) {
    session.setAttribute(LOGIN_ATTRNAME, username);
    session.setAttribute(LOGIN_ATTRID, id);
  }

  @Test
  void publicEntryReturnsComments() {
    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(200, response.getStatus());
    assertEquals(1, result.size());
    assertEquals("secret body", result.get(0).getBody());
  }

  @Test
  void privateEntryReturnsForbiddenAndEmpty() {
    entry.setSecurity(Security.PRIVATE);

    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(403, response.getStatus());
    assertTrue(result.isEmpty());
  }

  @Test
  void privateEntryHiddenFromOtherLoggedInUser() {
    login(OTHER_ID, "other");
    entry.setSecurity(Security.PRIVATE);

    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(403, response.getStatus());
    assertTrue(result.isEmpty());
  }

  @Test
  void commentsDisabledReturnsEmpty() {
    entry.setAllowComments(PrefBool.N);

    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(403, response.getStatus());
    assertTrue(result.isEmpty());
  }

  @Test
  void ownerViewOnlyJournalReturnsEmpty() {
    journal.setOwnerViewOnly(true);

    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(403, response.getStatus());
    assertTrue(result.isEmpty());
  }

  @Test
  void ownerSeesCommentsOnOwnPrivateEntry() {
    login(OWNER_ID, "owner");
    entry.setSecurity(Security.PRIVATE);
    journal.setOwnerViewOnly(true);

    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(200, response.getStatus());
    assertEquals(1, result.size());
  }

  @Test
  void friendsEntryHiddenFromAnonymous() {
    entry.setSecurity(Security.FRIENDS);

    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(403, response.getStatus());
    assertTrue(result.isEmpty());
  }

  @Test
  void friendsEntryVisibleToFriend() {
    login(OTHER_ID, "other");
    entry.setSecurity(Security.FRIENDS);
    final User other = new User();
    other.setId(OTHER_ID);
    final Friend friend = new Friend();
    friend.setUser(owner);
    friend.setFriend(other);
    owner.setFriends(Set.of(friend));

    final List<CommentTo> result = commentController.getComments(ENTRY_ID, session, response);

    assertEquals(200, response.getStatus());
    assertEquals(1, result.size());
  }

  @Test
  void missingEntryReturnsNotFound() {
    when(entryRepository.findById(99)).thenReturn(Optional.empty());

    final List<CommentTo> result = commentController.getComments(99, session, response);

    assertEquals(404, response.getStatus());
    assertTrue(result.isEmpty());
  }

  @Test
  void getByIdPublicEntry() {
    final ResponseEntity<CommentTo> result = commentController.getById(COMMENT_ID, session);

    assertEquals(200, result.getStatusCode().value());
    assertEquals("secret body", result.getBody().getBody());
  }

  @Test
  void getByIdPrivateEntryForbidden() {
    entry.setSecurity(Security.PRIVATE);

    final ResponseEntity<CommentTo> result = commentController.getById(COMMENT_ID, session);

    assertEquals(403, result.getStatusCode().value());
    assertNull(result.getBody());
  }

  @Test
  void getByIdAuthorCanLoadOwnComment() {
    login(3, "commenter");
    entry.setSecurity(Security.PRIVATE);

    final ResponseEntity<CommentTo> result = commentController.getById(COMMENT_ID, session);

    assertEquals(200, result.getStatusCode().value());
    assertEquals("secret body", result.getBody().getBody());
  }

  @Test
  void getByIdNotFound() {
    when(commentRepository.findById(99)).thenReturn(Optional.empty());

    assertEquals(404, commentController.getById(99, session).getStatusCode().value());
  }
  // ---- deleting -----------------------------------------------------------------------------

  @Test
  void deleteRequiresALogin() {
    final Map<String, String> result = commentController.delete(COMMENT_ID, session, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(commentRepository, never()).deleteById(anyInt());
  }

  @Test
  void authorCanDeleteOwnComment() {
    login(3, "commenter");

    final Map<String, String> result = commentController.delete(COMMENT_ID, session, response);

    assertEquals(200, response.getStatus());
    assertEquals(Map.of("id", "20"), result);
    verify(commentRepository).deleteById(COMMENT_ID);
  }

  @Test
  void someoneElsesCommentIsLeftAlone() {
    login(OTHER_ID, "other");

    final Map<String, String> result = commentController.delete(COMMENT_ID, session, response);

    assertEquals(200, response.getStatus());
    assertEquals(Map.of("id", "20"), result);
    verify(commentRepository, never()).deleteById(anyInt());
  }

  @Test
  void deletingAMissingCommentIsABadRequest() {
    login(3, "commenter");
    when(commentRepository.findById(99)).thenReturn(Optional.empty());

    final Map<String, String> result = commentController.delete(99, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Could not delete the comment.", result.get("error"));
  }

  // ---- posting ------------------------------------------------------------------------------

  private static CommentTo commentTo(final int entryId) {
    final CommentTo to = new CommentTo();
    to.setEid(entryId);
    to.setSubject("hello");
    to.setBody("a comment body");
    return to;
  }

  @Test
  void postRequiresALogin() {
    final Map<String, String> result = commentController.post(commentTo(ENTRY_ID), session, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(commentRepository, never()).save(any());
  }

  @Test
  void postToAMissingEntryIsNotFound() {
    login(3, "commenter");
    when(entryRepository.findById(99)).thenReturn(Optional.empty());

    final Map<String, String> result = commentController.post(commentTo(99), session, response);

    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    assertEquals("No entry for this comment", result.get("error"));
    verify(commentRepository, never()).save(any());
  }

  @Test
  void postIsRefusedWhenTheEntryBlocksComments() {
    login(3, "commenter");
    entry.setAllowComments(PrefBool.N);

    final Map<String, String> result = commentController.put(commentTo(ENTRY_ID), session, response);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertEquals("Comments blocked by owner of this blog entry.", result.get("error"));
    verify(commentRepository, never()).save(any());
    verify(queueMailRepository, never()).save(any());
  }

  @Test
  void postSavesANewCommentAndReturnsItsId() {
    login(3, "commenter");
    when(userRepository.findById(3)).thenReturn(Optional.of(commenter));
    when(commentRepository.save(any(Comment.class)))
        .thenAnswer(
            inv -> {
              final Comment saved = inv.getArgument(0);
              saved.setId(21); // what the identity column does on insert
              return saved;
            });
    final CommentTo to = commentTo(ENTRY_ID);
    to.setSubject("hel\u0007lo");

    final Map<String, String> result = commentController.post(to, session, response);

    assertEquals(200, response.getStatus());
    assertEquals(Map.of("id", "21"), result);
    final ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(commentRepository).save(captor.capture());
    final Comment saved = captor.getValue();
    assertSame(commenter, saved.getUser());
    assertSame(entry, saved.getEntry());
    assertEquals(ENTRY_ID, saved.getEid());
    assertEquals("hello", saved.getSubject());
    assertEquals("a comment body", saved.getBody());
    assertEquals(FormatType.TEXT, saved.getFormat());
    assertNotNull(saved.getDate());
  }

  @Test
  void putEditsTheExistingComment() {
    login(3, "commenter");
    when(userRepository.findById(3)).thenReturn(Optional.of(commenter));
    final CommentTo to = commentTo(ENTRY_ID);
    to.setId(COMMENT_ID);
    to.setSubject("edited");
    to.setBody("edited body");

    final Map<String, String> result = commentController.put(to, session, response);

    assertEquals(200, response.getStatus());
    assertEquals(Map.of("id", "20"), result);
    verify(commentRepository).save(comment);
    assertEquals("edited", comment.getSubject());
    assertEquals("edited body", comment.getBody());
    assertSame(commenter, comment.getUser());
  }

  @Test
  void putToACommentOnAnotherEntryIsABadRequest() {
    login(3, "commenter");
    final Entry other = new Entry();
    other.setId(11);
    other.setUser(owner);
    other.setAllowComments(PrefBool.Y);
    when(entryRepository.findById(11)).thenReturn(Optional.of(other));
    final CommentTo to = commentTo(11);
    to.setId(COMMENT_ID);

    final Map<String, String> result = commentController.put(to, session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals(
        "Error saving comment. Entry id does not match original on comment.", result.get("error"));
    verify(commentRepository, never()).save(any());
  }
}
