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
package com.justjournal.services;


import com.justjournal.Login;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Journal;
import com.justjournal.model.PasswordType;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Style;
import com.justjournal.model.User;
import com.justjournal.model.UserBio;
import com.justjournal.model.UserContact;
import com.justjournal.model.UserPref;
import com.justjournal.model.api.NewUser;
import com.justjournal.repository.JournalRepository;
import com.justjournal.repository.UserBioRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserPrefRepository;
import com.justjournal.repository.UserRepository;
import java.util.Calendar;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** @author Lucas Holt */
@Slf4j
@Service
public class AccountService {

  private final UserRepository userRepository;

  private final UserBioRepository userBioDao;

  private final UserContactRepository userContactRepository;

  private final UserPrefRepository userPrefRepository;

  private final JournalRepository journalRepository;

  private final StyleService styleService;

  public AccountService(UserRepository userRepository, UserBioRepository userBioDao, UserContactRepository userContactRepository, UserPrefRepository userPrefRepository, JournalRepository journalRepository, StyleService styleService) {
    this.userRepository = userRepository;
    this.userBioDao = userBioDao;
    this.userContactRepository = userContactRepository;
    this.userPrefRepository = userPrefRepository;
    this.journalRepository = journalRepository;
    this.styleService = styleService;
  }

  public User signup(final NewUser newUser) throws ServiceException {
    final Style style = styleService.getDefaultStyle();

    if (userRepository.findByUsername(newUser.getUsername())!= null) {
      throw new ServiceException("Unable to create user");
    }

    User user = new User();
    user.setName(newUser.getFirstName());
    user.setLastName(newUser.getLastName());
    user.setUsername(newUser.getUsername());
    user.setPassword(Login.getHashedPassword(newUser.getUsername(), newUser.getPassword()));
    user.setPasswordType(PasswordType.SHA256);
    user.setType(0);
    user.setSince(Calendar.getInstance().get(Calendar.YEAR));
    user.setLastLogin(new Date());
    try {
      user = userRepository.saveAndFlush(user);
    } catch (final Exception e) {
      log.error("Unable to save user", e);
      throw new ServiceException("Unable to save user", e);
    }

    final Journal journal = new Journal();
    journal.setStyle(style);
    journal.setUser(user);
    journal.setSlug(newUser.getUsername());
    journal.setAllowSpider(true);
    journal.setOwnerViewOnly(false);
    journal.setPingServices(true);
    journal.setName(user.getName() + "\'s Journal");
    journal.setSince(Calendar.getInstance().getTime());
    journal.setModified(Calendar.getInstance().getTime());
    try {
      journalRepository.saveAndFlush(journal);
    } catch (final Exception e) {
      log.error("Could not save journal", e);
      throw new ServiceException("Unable to save journal");
    }

    final UserPref userPref = new UserPref();
    userPref.setShowAvatar(PrefBool.N);
    userPref.setUser(user);
    try {
      userPrefRepository.save(userPref);
    } catch (final Exception e) {
      log.error("Could not save user pref", e);
      throw new ServiceException("Unable to save user preferences");
    }

    final UserContact userContact = new UserContact();
    userContact.setEmail(newUser.getEmail());
    userContact.setUser(user);
    try {
      userContactRepository.save(userContact);
    } catch (final Exception e) {
      log.error("Could not save user contact", e);
      throw new ServiceException("Unable to save user contact");
    }

    final UserBio userBio = new UserBio();
    userBio.setBio("");
    userBio.setUser(user);
    try {
      userBioDao.save(userBio);
    } catch (final Exception e) {
      log.error("Could not save user bio", e);
      throw new ServiceException("Unable to save user bio");
    }
    return user;
  }
}
