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


import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Friend;
import com.justjournal.model.User;
import com.justjournal.repository.FriendsRepository;
import com.justjournal.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage Friends
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/api/friend")
public class FriendController {

  private final UserRepository userRepository;

  private final FriendsRepository friendsDao;

  @Autowired
  public FriendController(final UserRepository userRepository, final FriendsRepository friendsDao) {
    this.userRepository = userRepository;
    this.friendsDao = friendsDao;
  }

  // TODO: refactor to return user objects?

  @GetMapping(value = "{username}/friendswith/{other}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Boolean> areWeFriends(
      @PathVariable(Constants.PARAM_USERNAME) final String username,
      @PathVariable("other") final String otherUsername) {
    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    if (!Login.isUserName(otherUsername)) {
      throw new NotFoundException();
    }

    try {
      final User user = userRepository.findByUsername(username);
      if (user == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

      for (final Friend friend : user.getFriends()) {
        if (otherUsername.equalsIgnoreCase(friend.getFriend().getUsername()))
          return ResponseEntity.ok(true);
      }
      return ResponseEntity.ok(false);
    } catch (final Exception e) {
      return ResponseEntity.badRequest().body(false);
    }
  }

  /**
   * @param username username
   * @param response http response
   * @return List of usernames as strings
   */
  @Cacheable(value = "friends", key = "#p0")
  @GetMapping(value = "{username}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Collection<User> getByUsername(
      @PathVariable(Constants.PARAM_USERNAME) String username, HttpServletResponse response) {
    if (!Login.isUserName(username)) {
      throw new NotFoundException();
    }

    try {
      final ArrayList<User> friends = new ArrayList<>();

      final User user = userRepository.findByUsername(username);
      for (final Friend friend : user.getFriends()) {
        friends.add(friend.getFriend());
      }
      return friends;
    } catch (final Exception e) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return Collections.emptyList();
    }
  }

  @CacheEvict(value = "friends", key = "#friend")
  @PutMapping(value = "{friend}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> put(
      @PathVariable("friend") final String friend,
      final HttpSession session,
      final HttpServletResponse response) {
    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    if (!Login.isUserName(friend)) {
      throw new NotFoundException();
    }

    try {
      final User friendUser = userRepository.findByUsername(friend);
      final User owner = userRepository.findById(Login.currentLoginId(session)).orElse(null);

      if (friendUser == null) return ErrorHandler.modelError("Could not find friend's username");

      if (owner == null) return ErrorHandler.modelError("Could not find logged in user account.");

      final Friend f = new Friend();
      f.setFriend(friendUser);
      f.setUser(owner);
      f.setPk(owner.getId());
      friendsDao.save(f);
      return java.util.Collections.singletonMap("status", "success");
    } catch (final Exception e) {
      log.error(e.getMessage());
      return ErrorHandler.modelError("Could not find friend's username");
    }
  }

  @CacheEvict(value = "friends", allEntries = true)
  @DeleteMapping(value = "{friend}")
  public Map<String, String> delete(
      @PathVariable("friend") final String friend,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    try {
      final User friendUser = userRepository.findByUsername(friend);
      final User user = userRepository.findById(Login.currentLoginId(session)).orElse(null);

      final Friend f = friendsDao.findOneByUserAndFriend(user, friendUser);
      if (f != null) {
        friendsDao.delete(f);
        return java.util.Collections.singletonMap("status", "success");
      } else {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return ErrorHandler.modelError("Error deleting friend");
      }
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }

    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    return ErrorHandler.modelError("Error deleting friend");
  }
}
