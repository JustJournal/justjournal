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

import static com.justjournal.core.Constants.PARAM_ID;
import static com.justjournal.core.Constants.PARAM_USERNAME;

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.UnauthorizedException;
import com.justjournal.model.RssSubscription;
import com.justjournal.model.User;
import com.justjournal.repository.RssSubscriptionsRepository;
import com.justjournal.repository.UserRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** @author Lucas Holt */
@Slf4j
@RestController
@RequestMapping("/api/rssreader")
public class RssReaderController {
  public static final int RSS_URL_MAX_LENGTH = 1024;
  public static final int RSS_URL_MIN_LENGTH = 10;

  private final RssSubscriptionsRepository rssSubscriptionsDAO;

  private final UserRepository userRepository;

  @Autowired
  public RssReaderController(
      final RssSubscriptionsRepository rssSubscriptionsDAO, final UserRepository userRepository) {
    this.rssSubscriptionsDAO = rssSubscriptionsDAO;
    this.userRepository = userRepository;
  }

  @GetMapping(value = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RssSubscription> getById(@PathVariable(PARAM_ID) Integer id) {
    return rssSubscriptionsDAO.findById(id)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new NotFoundException("RSS subscription not found"));
  }

  //@Cacheable(value = "rsssubscription", key = "#username")
  @GetMapping(value = "user/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Collection<RssSubscription>> getByUser(@PathVariable(PARAM_USERNAME) String username) {
    User user = userRepository.findByUsername(username);
    if (user == null) {
      throw new NotFoundException("User not found");
    }
    Collection<RssSubscription> subscriptions = rssSubscriptionsDAO.findByUser(user);
    return ResponseEntity.ok(subscriptions);
  }

  @PutMapping
  public ResponseEntity<Map<String, String>> create(@RequestBody final String uri, final HttpSession session) {
    if (uri == null || uri.length() < RSS_URL_MIN_LENGTH || uri.length() > RSS_URL_MAX_LENGTH) {
      throw new BadRequestException("Invalid URI length");
    }

    User user = userRepository.findById(Login.currentLoginId(session))
            .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

    RssSubscription subscription = new RssSubscription();
    subscription.setUser(user);
    subscription.setUri(uri);

    RssSubscription savedSubscription = rssSubscriptionsDAO.save(subscription);

    return ResponseEntity.ok(Collections.singletonMap("id", String.valueOf(savedSubscription.getSubscriptionId())));
  }

  @DeleteMapping("/{subId}")
  public ResponseEntity<Map<String, String>> delete(@PathVariable final int subId, final HttpSession session) {

    if (!Login.isAuthenticated(session)) {
      throw new UnauthorizedException(Constants.ERR_INVALID_LOGIN);
    }

    if (subId <= 0) {
      throw new BadRequestException("Invalid subscription ID");
    }

    User user = userRepository.findById(Login.currentLoginId(session))
            .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

    RssSubscription subscription = rssSubscriptionsDAO.findById(subId)
            .orElseThrow(() -> new NotFoundException("Subscription not found"));

    if (user.getId() != (subscription.getUser().getId())) {
      throw new NotFoundException("Subscription not found");
    }

    rssSubscriptionsDAO.delete(subscription);
    return ResponseEntity.ok(Collections.singletonMap("id", Integer.toString(subId)));

  }
}
