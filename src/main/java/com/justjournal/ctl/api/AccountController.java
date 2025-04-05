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

import static com.justjournal.core.Constants.PARAM_USERNAME;
import static com.justjournal.core.Constants.PASSWORD_MAX_LENGTH;
import static com.justjournal.core.Constants.PASSWORD_MIN_LENGTH;

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Journal;
import com.justjournal.model.User;
import com.justjournal.model.api.PasswordChange;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.FavoriteRepository;
import com.justjournal.repository.FriendsRepository;
import com.justjournal.repository.RssSubscriptionsRepository;
import com.justjournal.repository.UserBioRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserImageRepository;
import com.justjournal.repository.UserLinkRepository;
import com.justjournal.repository.UserLocationRepository;
import com.justjournal.repository.UserPrefRepository;
import com.justjournal.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** @author Lucas Holt */
@Slf4j
@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final UserRepository userDao;

  private final Login webLogin;

  private final CommentRepository commentRepository;

  private final EntryRepository entryRepository;

  private final FriendsRepository friendsDao;

  private final UserBioRepository userBioDao;

  private final UserContactRepository userContactRepository;

  private final UserLinkRepository userLinkRepository;

  private final UserLocationRepository userLocationRepository;

  private final RssSubscriptionsRepository rssSubscriptionsDAO;

  private final UserImageRepository userImageRepository;

  private final UserPrefRepository userPrefRepository;

  private final FavoriteRepository favoriteRepository;

  private final JdbcTemplate jdbcTemplate;

  public AccountController(Login webLogin, UserRepository userDao, CommentRepository commentRepository, EntryRepository entryRepository, JdbcTemplate jdbcTemplate, FriendsRepository friendsDao, UserBioRepository userBioDao, UserContactRepository userContactRepository, UserLinkRepository userLinkRepository, UserLocationRepository userLocationRepository, UserPrefRepository userPrefRepository, RssSubscriptionsRepository rssSubscriptionsDAO, UserImageRepository userImageRepository, FavoriteRepository favoriteRepository) {
    this.webLogin = webLogin;
    this.userDao = userDao;
    this.commentRepository = commentRepository;
    this.entryRepository = entryRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.friendsDao = friendsDao;
    this.userBioDao = userBioDao;
    this.userContactRepository = userContactRepository;
    this.userLinkRepository = userLinkRepository;
    this.userLocationRepository = userLocationRepository;
    this.userPrefRepository = userPrefRepository;
    this.rssSubscriptionsDAO = rssSubscriptionsDAO;
    this.userImageRepository = userImageRepository;
    this.favoriteRepository = favoriteRepository;
  }

  private Map<String, String> changePassword(
      final PasswordChange passwordChange,
      final HttpSession session,
      final HttpServletResponse response) {
    final String passCurrent = passwordChange.getPassCurrent();
    final String passNew = passwordChange.getPassNew();

    if (passCurrent == null
        || passCurrent.length() < PASSWORD_MIN_LENGTH
        || passCurrent.length() > PASSWORD_MAX_LENGTH) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("The current password is invalid.");
    }
    if (passNew == null
        || passNew.length() < PASSWORD_MIN_LENGTH
        || passNew.length() > PASSWORD_MAX_LENGTH
        || !Login.isPassword(passNew)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("The new password is invalid.");
    }

    // TODO: Refactor change pass
    final boolean result =
        webLogin.changePass(Login.currentLoginName(session), passCurrent, passNew);

    if (!result) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(
          "Error changing password.  Did you type in your old password correctly?");
    }
    return java.util.Collections.singletonMap("status", "Password Changed.");
  }

  private Map<String, String> updateUser(
      final User user, final HttpSession session, final HttpServletResponse response) {
    if (Login.currentLoginId(session) == user.getId()
        && Login.currentLoginName(session).equals(user.getUsername())) {

      userDao.save(user);
      return java.util.Collections.singletonMap("id", Integer.toString(user.getId()));
    }
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return ErrorHandler.modelError("Could not edit account.");
  }

  @DeleteMapping
  public Map<String, String> delete(final HttpServletResponse response, final HttpSession session) {
    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    final int userID = Login.currentLoginId(session);

    try {
      final Optional<User> user = userDao.findById(userID);
      if (user.isEmpty()) {
        throw new NotFoundException("User should always exist at this point");
      }
      commentRepository.deleteAll(commentRepository.findByUser(user.get()));
      entryRepository.deleteAllInBatch(entryRepository.findByUser(user.get()));
      entryRepository.flush();

      favoriteRepository.deleteAllInBatch(favoriteRepository.findByUser(user.get()));
      favoriteRepository.flush();

      jdbcTemplate.execute("DELETE from friends where id = " + userID + ";");

      rssSubscriptionsDAO.deleteAll(rssSubscriptionsDAO.findByUser(user.get()));

      jdbcTemplate.execute("DELETE FROM user_files WHERE ownerid=" + userID + ";");

      userImageRepository.deleteAll(userImageRepository.findByUsername(user.get().getUsername()));
      jdbcTemplate.execute("DELETE FROM user_images_album WHERE owner=" + userID + ";");
      jdbcTemplate.execute("DELETE FROM user_images_album_map WHERE owner=" + userID + ";");
      jdbcTemplate.execute("DELETE FROM user_pic WHERE id=" + userID + ";");

      jdbcTemplate.execute("DELETE FROM user_pref WHERE user_id=" + userID + ";");

      jdbcTemplate.execute("DELETE FROM journal WHERE user_id=" + userID + ";");

      userLinkRepository.deleteByUser(user.get());
      userLocationRepository.deleteByUser(user.get());

      jdbcTemplate.execute("DELETE FROM user_bio WHERE user_id=" + userID + ";");

      jdbcTemplate.execute("DELETE FROM user_contact WHERE user_id=" + userID + ";");

      userDao.deleteById(userID);
    } catch (final Exception e) {
      log.error("Could not delete account", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return ErrorHandler.modelError("Error deleting account");
    }
    return java.util.Collections.singletonMap("status", "Account Deleted");
  }

  @PostMapping(value = "/password", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> post(
      @RequestBody final PasswordChange passwordChange,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    return changePassword(passwordChange, session, response);
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> post(
      @RequestBody final User user, final HttpSession session, final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    return updateUser(user, session, response);
  }

  @GetMapping(value = "{username}", produces = MediaType.APPLICATION_JSON_VALUE)
  public User getByUsername(
      @PathVariable(PARAM_USERNAME) final String username,
      final HttpSession session,
      final HttpServletResponse response) {
    try {
      final User user = userDao.findByUsername(username);

      // TODO: we should handle this per journal rather than globally if one is there. Be
      // conservative for now
      for (final Journal journal : user.getJournals()) {
        if (journal.isOwnerViewOnly()
            && (!Login.isAuthenticated(session)
                || user.getUsername().equals(Login.currentLoginName(session)))) {
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          return null;
        }
      }

      return user;
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return null;
    }
  }
}
