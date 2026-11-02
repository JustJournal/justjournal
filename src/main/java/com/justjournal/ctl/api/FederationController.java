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

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.PrefBool;
import com.justjournal.model.UserPref;
import com.justjournal.repository.UserPrefRepository;
import com.justjournal.services.activitypub.FederationPolicy;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The logged in user's choice to let people on Mastodon and other fediverse servers follow their
 * journal. Turning it on is refused while federation is off for the site; turning it off always
 * works.
 *
 * @author Lucas Holt
 */
@RestController
@RequestMapping("/api/federation")
public class FederationController {

  /** Whether the site federates at all, and whether this user turned it on. */
  public record FederationPreference(boolean available, boolean federate) {}

  private final UserPrefRepository userPrefRepository;
  private final FederationPolicy federationPolicy;

  public FederationController(
      final UserPrefRepository userPrefRepository, final FederationPolicy federationPolicy) {
    this.userPrefRepository = userPrefRepository;
    this.federationPolicy = federationPolicy;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FederationPreference> get(final HttpSession session) {
    final UserPref pref = pref(session);
    return ResponseEntity.ok(
        new FederationPreference(federationPolicy.isEnabled(), FederationPolicy.optedIn(pref)));
  }

  @PutMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FederationPreference> put(
      @RequestBody final FederationPreference preference, final HttpSession session) {
    final UserPref pref = pref(session);
    if (preference.federate() && !federationPolicy.isEnabled()) {
      throw new BadRequestException("Following from other servers is not available on this site.");
    }

    pref.setFederate(preference.federate() ? PrefBool.Y : PrefBool.N);
    userPrefRepository.save(pref);
    return ResponseEntity.ok(
        new FederationPreference(federationPolicy.isEnabled(), preference.federate()));
  }

  private UserPref pref(final HttpSession session) {
    final Integer userId = (Integer) session.getAttribute(LOGIN_ATTRID);
    if (userId == null || userId < 1) {
      throw new ForbiddenException("You must be logged in to change this setting");
    }
    return userPrefRepository.findByUserId(userId).orElseThrow(NotFoundException::new);
  }
}
