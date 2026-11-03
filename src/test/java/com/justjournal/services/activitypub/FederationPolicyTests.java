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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import com.justjournal.model.UserPref;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/** A journal federates only when the site, its owner and its privacy setting all allow it. */
class FederationPolicyTests {

  @ParameterizedTest(name = "site {0}, opted in {1}, owner only {2} -> {3}")
  @CsvSource({
    "true,  true,  false, true",
    "true,  true,  true,  false",
    "true,  false, false, false",
    "true,  false, true,  false",
    "false, true,  false, false",
    "false, true,  true,  false",
    "false, false, false, false",
    "false, false, true,  false"
  })
  void federates(
      final boolean site, final boolean optedIn, final boolean ownerOnly, final boolean expected) {
    final FederationPolicy policy = new FederationPolicy();
    policy.setEnabled(site);

    assertEquals(expected, policy.federates(user(optedIn, ownerOnly)));
    assertEquals(site, policy.isEnabled());
  }

  @Test
  void aUserWithoutPreferencesOrJournals_doesNotFederate() {
    final FederationPolicy policy = new FederationPolicy();
    policy.setEnabled(true);

    final User noPref = user(true, false);
    setUserPref(noPref, null);
    assertFalse(policy.federates(noPref));

    final User noJournal = user(true, false);
    noJournal.getJournals().clear();
    assertFalse(policy.federates(noJournal));

    assertFalse(policy.federates(null));
  }

  @Test
  void anyOwnerOnlyJournal_stopsIt() {
    final FederationPolicy policy = new FederationPolicy();
    policy.setEnabled(true);
    final User user = user(true, false);
    final Journal second = new Journal();
    second.setOwnerViewOnly(true);
    user.getJournals().add(second);

    assertFalse(policy.federates(user));
  }

  @Test
  void aDisabledAccount_doesNotFederate() {
    final FederationPolicy policy = new FederationPolicy();
    policy.setEnabled(true);
    final User user = user(true, false);
    user.setDisabled(true);

    assertFalse(policy.federates(user));
  }

  private static User user(final boolean optedIn, final boolean ownerOnly) {
    final User user = new User();
    user.setId(7);
    user.setUsername("bob");
    final UserPref pref = new UserPref();
    pref.setFederate(optedIn ? PrefBool.Y : PrefBool.N);
    setUserPref(user, pref);
    final Journal journal = new Journal();
    journal.setOwnerViewOnly(ownerOnly);
    journal.setUser(user);
    user.getJournals().add(journal);
    return user;
  }

  /** User has a getter but no setter for its preferences. */
  private static void setUserPref(final User user, final UserPref pref) {
    ReflectionTestUtils.setField(user, "userPref", pref);
  }
}
