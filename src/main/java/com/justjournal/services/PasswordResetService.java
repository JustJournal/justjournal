/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
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
package com.justjournal.services;

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.QueueMail;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.cache.PasswordResetRepository;
import com.justjournal.utility.StringUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Forgotten password handling. A reset link with a random single use token is mailed to the
 * address on file when both the username and e-mail address match the account.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class PasswordResetService {

  static final Duration TOKEN_TTL = Duration.ofMinutes(30);

  /** Minimum time between reset mails to the same account. */
  static final Duration USER_THROTTLE = Duration.ofMinutes(5);

  private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(10);

  private static final int TOKEN_BYTES = 32;

  /** 32 bytes, base64url without padding. */
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

  private final SecureRandom secureRandom = new SecureRandom();

  private final UserRepository userRepository;

  private final UserContactRepository userContactRepository;

  private final QueueMailRepository queueMailRepository;

  private final PasswordResetRepository passwordResetRepository;

  private final Settings settings;

  private final Login login;

  public PasswordResetService(
      UserRepository userRepository,
      UserContactRepository userContactRepository,
      QueueMailRepository queueMailRepository,
      PasswordResetRepository passwordResetRepository,
      Settings settings,
      Login login) {
    this.userRepository = userRepository;
    this.userContactRepository = userContactRepository;
    this.queueMailRepository = queueMailRepository;
    this.passwordResetRepository = passwordResetRepository;
    this.settings = settings;
    this.login = login;
  }

  /**
   * Mail a reset link if the username and e-mail address belong to the same account. Runs
   * asynchronously and never reports the outcome, so callers respond the same way whether or not
   * the account exists.
   *
   * @param username username on the account
   * @param email e-mail address on the account
   */
  @Async
  public void requestReset(final String username, final String email) {
    try {
      if (!Login.isUserName(username) || email == null) return;

      final String address = email.trim().toLowerCase(Locale.ROOT);
      if (!StringUtil.isEmailValid(address)) return;

      final User user = userRepository.findByUsername(username);
      if (user == null) {
        log.info("Password reset requested for unknown user {}", username);
        return;
      }

      final UserContact contact = userContactRepository.findByUser(user);
      if (contact == null
          || contact.getEmail() == null
          || !contact.getEmail().trim().equalsIgnoreCase(address)) {
        log.info("Password reset requested for {} with an e-mail address not on file", username);
        return;
      }

      if (!Boolean.TRUE.equals(
          passwordResetRepository
              .tryAcquireUser(user.getId(), USER_THROTTLE)
              .block(REDIS_TIMEOUT))) {
        log.info("Password reset for {} requested again too soon", username);
        return;
      }

      final String token = newToken();
      passwordResetRepository.saveToken(hash(token), user.getId(), TOKEN_TTL).block(REDIS_TIMEOUT);

      queueMail(
          contact.getEmail().trim(),
          "JustJournal: Password Reset",
          resetMailBody(user.getUsername(), token),
          "password_reset");
    } catch (final Exception e) {
      log.error("Could not process password reset request", e);
    }
  }

  /**
   * Set a new password using a token from a reset link. The token is used up even if the
   * password can't be saved.
   *
   * @param token token from the reset link
   * @param newPassword new clear text password
   * @return true if the password was changed, false if the token is invalid or expired
   */
  public boolean resetPassword(final String token, final String newPassword) {
    if (token == null || !TOKEN_PATTERN.matcher(token).matches() || !Login.isNewPassword(newPassword))
      return false;

    final Integer userId =
        passwordResetRepository.consumeToken(hash(token)).block(REDIS_TIMEOUT);
    if (userId == null || !login.resetPass(userId, newPassword)) return false;

    try {
      userRepository
          .findById(userId)
          .ifPresent(
              user -> {
                final UserContact contact = userContactRepository.findByUser(user);
                if (contact != null
                    && contact.getEmail() != null
                    && !contact.getEmail().isBlank()) {
                  queueMail(
                      contact.getEmail().trim(),
                      "JustJournal: Password Changed",
                      changedMailBody(user.getUsername()),
                      "password_changed");
                }
              });
    } catch (final Exception e) {
      log.error("Could not queue password changed notice", e);
    }
    return true;
  }

  private String newToken() {
    final byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(final String token) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String baseUri() {
    final String base = settings.getBaseUri();
    return base.endsWith("/") ? base : base + "/";
  }

  private String resetMailBody(final String username, final String token) {
    // token goes in the fragment so it isn't sent to the server or leaked in referrers
    return "Someone, hopefully you, asked to reset the password for the JustJournal account "
        + username
        + ".\n\n"
        + "To choose a new password, visit the link below within "
        + TOKEN_TTL.toMinutes()
        + " minutes:\n\n"
        + baseUri()
        + "password/reset#"
        + token
        + "\n\n"
        + "If you didn't ask for this, you can ignore this message. Your password has not been"
        + " changed.\n";
  }

  private String changedMailBody(final String username) {
    return "The password for the JustJournal account "
        + username
        + " was just changed using a password reset link.\n\n"
        + "If you didn't do this, reset your password again at "
        + baseUri()
        + "password/forgot and contact the site administrator.\n";
  }

  private void queueMail(
      final String to, final String subject, final String body, final String purpose) {
    final String from = settings.getMailFrom();
    final QueueMail mail = new QueueMail();
    mail.setFrom(from == null || from.isBlank() ? "root@localhost" : from);
    mail.setTo(to);
    mail.setSubject(subject);
    mail.setBody(body);
    mail.setPurpose(purpose);
    queueMailRepository.save(mail);
  }
}
