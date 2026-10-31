/*
 * Copyright (c) 2003-2021 Lucas Holt
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Friend;
import com.justjournal.model.User;
import com.justjournal.repository.FriendsRepository;
import com.justjournal.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Friend lists are public; adding and removing friends needs a login. */
class FriendControllerTests {

  private UserRepository userRepository;
  private FriendsRepository friendsDao;
  private FriendController controller;

  private MockHttpSession session;
  private MockHttpServletResponse response;
  private User me;
  private User pal;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    friendsDao = mock(FriendsRepository.class);
    controller = new FriendController(userRepository, friendsDao);
    session = new MockHttpSession();
    response = new MockHttpServletResponse();

    me = user(1, "mia");
    pal = user(2, "pal");
    final Friend friend = new Friend();
    friend.setUser(me);
    friend.setFriend(pal);
    me.getFriends().add(friend);
    when(userRepository.findByUsername("mia")).thenReturn(me);
    when(userRepository.findByUsername("pal")).thenReturn(pal);
  }

  private static User user(final int id, final String username) {
    final User user = new User();
    user.setId(id);
    user.setUsername(username);
    return user;
  }

  private void login() {
    session.setAttribute(LOGIN_ATTRID, me.getId());
    session.setAttribute(LOGIN_ATTRNAME, me.getUsername());
    when(userRepository.findById(me.getId())).thenReturn(Optional.of(me));
  }

  @Test
  void areWeFriends_saysWhetherTheOtherUserIsOnTheList() {
    assertEquals(true, controller.areWeFriends("mia", "PAL").getBody());
    assertEquals(false, controller.areWeFriends("mia", "stranger").getBody());
    assertEquals(false, controller.areWeFriends("pal", "mia").getBody());
  }

  @Test
  void areWeFriends_unknownOrInvalidNames() {
    assertThrows(NotFoundException.class, () -> controller.areWeFriends("no!", "pal"));
    assertThrows(NotFoundException.class, () -> controller.areWeFriends("mia", "no!"));

    when(userRepository.findByUsername("nobody")).thenReturn(null);
    assertEquals(HttpStatus.NOT_FOUND, controller.areWeFriends("nobody", "pal").getStatusCode());

    when(userRepository.findByUsername("broken")).thenThrow(new IllegalStateException("db"));
    final ResponseEntity<Boolean> result = controller.areWeFriends("broken", "pal");
    assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    assertEquals(false, result.getBody());
  }

  @Test
  void getByUsername_listsTheFriends() {
    final Collection<User> friends = controller.getByUsername("mia", response);

    assertEquals(List.of(pal), List.copyOf(friends));
    assertEquals(200, response.getStatus());
  }

  @Test
  void getByUsername_unknownUser_isNotFoundAndEmpty() {
    when(userRepository.findByUsername("nobody")).thenReturn(null);

    assertTrue(controller.getByUsername("nobody", response).isEmpty());
    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    assertThrows(NotFoundException.class, () -> controller.getByUsername("no!", response));
  }

  @Test
  void put_requiresALogin() {
    final Map<String, String> result = controller.put("pal", session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(friendsDao, never()).save(any());
  }

  @Test
  void put_addsAFriendForTheLoggedInUser() {
    login();

    final Map<String, String> result = controller.put("pal", session, response);

    assertEquals(Map.of("status", "success"), result);
    final ArgumentCaptor<Friend> captor = ArgumentCaptor.forClass(Friend.class);
    verify(friendsDao).save(captor.capture());
    assertEquals(me, captor.getValue().getUser());
    assertEquals(pal, captor.getValue().getFriend());
    assertEquals(me.getId(), captor.getValue().getPk());
  }

  @Test
  void put_unknownFriendOrAccount_isAnError() {
    login();
    assertThrows(NotFoundException.class, () -> controller.put("no!", session, response));

    when(userRepository.findByUsername("nobody")).thenReturn(null);
    assertEquals("Could not find friend's username", controller.put("nobody", session, response).get("error"));

    when(userRepository.findById(me.getId())).thenReturn(Optional.empty());
    assertEquals(
        "Could not find logged in user account.", controller.put("pal", session, response).get("error"));

    when(userRepository.findByUsername("pal")).thenThrow(new IllegalStateException("db"));
    assertEquals("Could not find friend's username", controller.put("pal", session, response).get("error"));
    verify(friendsDao, never()).save(any());
  }

  @Test
  void delete_requiresALogin() {
    final Map<String, String> result = controller.delete("pal", session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertTrue(result.containsKey("error"), result.toString());
    verify(friendsDao, never()).delete(any());
  }

  @Test
  void delete_removesTheFriendship() {
    login();
    final Friend friendship = me.getFriends().iterator().next();
    when(friendsDao.findOneByUserAndFriend(me, pal)).thenReturn(friendship);

    final Map<String, String> result = controller.delete("pal", session, response);

    assertEquals(Map.of("status", "success"), result);
    verify(friendsDao).delete(friendship);
  }

  @Test
  void delete_whenNotFriends_isABadRequest() {
    login();
    when(friendsDao.findOneByUserAndFriend(me, pal)).thenReturn(null);

    final Map<String, String> result = controller.delete("pal", session, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertEquals("Error deleting friend", result.get("error"));
  }

  @Test
  void delete_repositoryFailure_isAServerError() {
    login();
    when(friendsDao.findOneByUserAndFriend(me, pal)).thenThrow(new IllegalStateException("db"));

    final Map<String, String> result = controller.delete("pal", session, response);

    assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
    assertEquals("Error deleting friend", result.get("error"));
  }
}
