/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2003-2023 Lucas Holt
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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.justjournal.core.MailSender;
import com.justjournal.core.Settings;
import com.justjournal.exception.BadRequestException;
import com.justjournal.model.QueueMail;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.model.UserTwoFactor;
import com.justjournal.model.UserTwoFactorRecoveryCode;
import com.justjournal.model.UserTwoFactorTrustedDevice;
import com.justjournal.model.api.TotpEnrollment;
import com.justjournal.model.api.TwoFactorStatus;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.UserContactRepository;
import com.justjournal.repository.UserTwoFactorRecoveryCodeRepository;
import com.justjournal.repository.UserTwoFactorRepository;
import com.justjournal.repository.UserTwoFactorTrustedDeviceRepository;
import com.justjournal.repository.cache.TwoFactorRepository;
import com.justjournal.utility.StringUtil;
import com.justjournal.utility.Totp;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optional second login factor: a code mailed to the account's address or a time based code from
 * an authenticator app, plus single use recovery codes and remembered browsers. Everything short
 * lived is in redis; what must survive is in the user_two_factor tables.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class TwoFactorService {

  /** Result of checking a second factor code. */
  public enum VerifyResult {
    OK,
    INVALID,
    /** Too many wrong codes; the login has to start over. */
    LOCKED
  }

  static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(10);

  /** Minimum time between mailed codes to one account. */
  static final Duration EMAIL_THROTTLE = Duration.ofSeconds(60);

  static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

  static final int MAX_ATTEMPTS = 5;

  static final Duration TOTP_ENROLL_TTL = Duration.ofMinutes(10);

  /** A TOTP code stays valid for the whole drift window, so block replays that long. */
  static final Duration TOTP_REPLAY_TTL =
      Duration.ofSeconds((long) (2 * Totp.WINDOW + 1) * Totp.STEP_SECONDS);

  public static final Duration TRUST_TTL = Duration.ofDays(30);

  static final int RECOVERY_CODE_COUNT = 10;

  static final String CODE_NOT_MAILED = "Your code could not be e-mailed. Please try again later.";

  static final String NOTICE_NOT_MAILED =
      "Nothing was changed because the e-mail notice could not be sent. Please try again later.";

  private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(10);

  private static final int EMAIL_CODE_DIGITS = 6;

  private static final int QR_SIZE = 240;

  /** Letters and digits that are hard to misread. */
  private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private static final Pattern RECOVERY_PATTERN =
      Pattern.compile("[A-Za-z0-9]{5}-?[A-Za-z0-9]{5}");

  private final SecureRandom secureRandom = new SecureRandom();

  private final UserContactRepository userContactRepository;

  private final UserTwoFactorRepository userTwoFactorRepository;

  private final UserTwoFactorRecoveryCodeRepository recoveryCodeRepository;

  private final UserTwoFactorTrustedDeviceRepository trustedDeviceRepository;

  private final TwoFactorRepository twoFactorRepository;

  private final QueueMailRepository queueMailRepository;

  private final ObjectProvider<MailSender> mailSender;

  private final Settings settings;

  /** Runs authenticator notices on their own so a failed insert can't roll back the change. */
  private final TransactionTemplate noticeTransaction;

  public TwoFactorService(
      UserContactRepository userContactRepository,
      UserTwoFactorRepository userTwoFactorRepository,
      UserTwoFactorRecoveryCodeRepository recoveryCodeRepository,
      UserTwoFactorTrustedDeviceRepository trustedDeviceRepository,
      TwoFactorRepository twoFactorRepository,
      QueueMailRepository queueMailRepository,
      ObjectProvider<MailSender> mailSender,
      Settings settings,
      PlatformTransactionManager transactionManager) {
    this.userContactRepository = userContactRepository;
    this.userTwoFactorRepository = userTwoFactorRepository;
    this.recoveryCodeRepository = recoveryCodeRepository;
    this.trustedDeviceRepository = trustedDeviceRepository;
    this.twoFactorRepository = twoFactorRepository;
    this.queueMailRepository = queueMailRepository;
    this.mailSender = mailSender;
    this.settings = settings;
    this.noticeTransaction = new TransactionTemplate(transactionManager);
    this.noticeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Optional<UserTwoFactor> find(final int userId) {
    return userTwoFactorRepository.findByUserId(userId);
  }

  public TwoFactorStatus status(final User user) {
    final Optional<UserTwoFactor> twoFactor = find(user.getId());
    return new TwoFactorStatus(
        twoFactor.isPresent(),
        twoFactor.map(UserTwoFactor::getMethod).orElse(null),
        emailFor(user),
        recoveryCodeRepository.countByUserIdAndUsedAtIsNull(user.getId()),
        trustedDeviceRepository.countByUserId(user.getId()));
  }

  /* ---- enrollment: e-mail ---- */

  /**
   * Mail a code so the user can prove the address on file works before relying on it.
   *
   * @throws BadRequestException when already enabled, no usable address, or sent too recently
   */
  public void startEmailEnrollment(final User user) {
    requireNotEnabled(user.getId());
    final String email = emailFor(user);
    if (email == null) {
      throw new BadRequestException(
          "Add a valid e-mail address under Contact preferences before using e-mail codes.");
    }
    if (!mailCode(user, email)) {
      throw new BadRequestException("A code was sent recently. Please wait a minute and try again.");
    }
  }

  /**
   * Turn on e-mail codes once the mailed code comes back.
   *
   * @return true if the code matched and the feature is now on
   */
  @Transactional
  public boolean confirmEmailEnrollment(final User user, final String code) {
    requireNotEnabled(user.getId());
    if (!checkEmailCode(user.getId(), trimmed(code))) return false;

    final UserTwoFactor twoFactor = new UserTwoFactor();
    twoFactor.setUserId(user.getId());
    twoFactor.setMethod(TwoFactorMethod.EMAIL);
    twoFactor.setCreated(new Date());
    userTwoFactorRepository.saveAndFlush(twoFactor);
    twoFactorRepository.clearAttempts(user.getId()).block(REDIS_TIMEOUT);

    notify(user, "JustJournal: Two-Factor Authentication Turned On", enabledMailBody(user, "e-mail"), "two_factor_enabled", true);
    return true;
  }

  /* ---- enrollment: authenticator app ---- */

  /**
   * Make a secret for the user to add to their app. Nothing is saved to the account until a code
   * from the app confirms it.
   */
  public TotpEnrollment startTotpEnrollment(final User user) {
    requireNotEnabled(user.getId());
    final String secret = Totp.generateSecret();
    twoFactorRepository
        .savePendingTotpSecret(user.getId(), secret, TOTP_ENROLL_TTL)
        .block(REDIS_TIMEOUT);
    return new TotpEnrollment(secret, Totp.otpauthUri(issuer(), user.getUsername(), secret));
  }

  /**
   * @return PNG of the QR code for the enrollment in progress, or empty if there isn't one
   */
  public Optional<byte[]> enrollmentQrPng(final User user) {
    final String secret = twoFactorRepository.getPendingTotpSecret(user.getId()).block(REDIS_TIMEOUT);
    if (secret == null) return Optional.empty();

    try {
      final BitMatrix matrix =
          new QRCodeWriter()
              .encode(
                  Totp.otpauthUri(issuer(), user.getUsername(), secret),
                  BarcodeFormat.QR_CODE,
                  QR_SIZE,
                  QR_SIZE);
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", out);
      return Optional.of(out.toByteArray());
    } catch (final WriterException | IOException e) {
      log.error("Could not draw QR code", e);
      return Optional.empty();
    }
  }

  /**
   * Turn on authenticator codes once a code from the app matches the pending secret.
   *
   * @return the user's new recovery codes, or empty if the code didn't match
   */
  @Transactional
  public List<String> confirmTotpEnrollment(final User user, final String code) {
    requireNotEnabled(user.getId());
    final String secret = twoFactorRepository.getPendingTotpSecret(user.getId()).block(REDIS_TIMEOUT);
    if (secret == null) {
      throw new BadRequestException("Authenticator setup timed out. Please start again.");
    }
    if (Totp.matchingStep(secret, trimmed(code), nowSeconds()) < 0) return List.of();

    final UserTwoFactor twoFactor = new UserTwoFactor();
    twoFactor.setUserId(user.getId());
    twoFactor.setMethod(TwoFactorMethod.TOTP);
    twoFactor.setTotpSecret(secret);
    twoFactor.setCreated(new Date());
    userTwoFactorRepository.saveAndFlush(twoFactor);

    twoFactorRepository.deletePendingTotpSecret(user.getId()).block(REDIS_TIMEOUT);
    twoFactorRepository.clearAttempts(user.getId()).block(REDIS_TIMEOUT);

    final List<String> codes = replaceRecoveryCodes(user.getId());
    notify(user, "JustJournal: Two-Factor Authentication Turned On", enabledMailBody(user, "an authenticator app"), "two_factor_enabled", false);
    return codes;
  }

  /* ---- management ---- */

  /** Throw away the old recovery codes and make a fresh set. The caller checks the password. */
  @Transactional
  public List<String> regenerateRecoveryCodes(final User user) {
    requireEnabled(user.getId());
    return replaceRecoveryCodes(user.getId());
  }

  /** Turn the second factor off and forget every recovery code and browser. The caller checks the password. */
  @Transactional
  public void disable(final User user) {
    requireEnabled(user.getId());
    final boolean byEmail =
        find(user.getId()).map(UserTwoFactor::getMethod).orElse(null) == TwoFactorMethod.EMAIL;
    userTwoFactorRepository.deleteByUserId(user.getId());
    recoveryCodeRepository.deleteByUserId(user.getId());
    trustedDeviceRepository.deleteByUserId(user.getId());
    twoFactorRepository.deleteEmailCode(user.getId()).block(REDIS_TIMEOUT);
    notify(user, "JustJournal: Two-Factor Authentication Turned Off", disabledMailBody(user), "two_factor_disabled", byEmail);
  }

  @Transactional
  public void forgetTrustedDevices(final int userId) {
    trustedDeviceRepository.deleteByUserId(userId);
  }

  /* ---- login ---- */

  /**
   * Mail a login code to a user whose method is e-mail.
   *
   * @return false if nothing was sent: wrong method, no address, or sent too recently
   * @throws BadRequestException when the mail could not be queued or sent
   */
  public boolean sendLoginCode(final User user) {
    final Optional<UserTwoFactor> twoFactor = find(user.getId());
    if (twoFactor.isEmpty() || twoFactor.get().getMethod() != TwoFactorMethod.EMAIL) return false;
    final String email = emailFor(user);
    if (email == null) {
      log.error("User {} has e-mail two factor but no usable address", user.getUsername());
      return false;
    }
    return mailCode(user, email);
  }

  /**
   * Check a second factor code of any kind: mailed, from the app, or a recovery code.
   *
   * @param userId user who passed the password check
   * @param code what the user typed
   * @return OK, INVALID, or LOCKED after too many wrong codes
   */
  @Transactional
  public VerifyResult verify(final int userId, final String code) {
    final Long attempts =
        twoFactorRepository.incrementAttempts(userId, ATTEMPT_WINDOW).block(REDIS_TIMEOUT);
    if (attempts != null && attempts > MAX_ATTEMPTS) {
      log.warn("Two factor attempts exhausted for user {}", userId);
      return VerifyResult.LOCKED;
    }

    final Optional<UserTwoFactor> twoFactor = find(userId);
    if (twoFactor.isEmpty()) return VerifyResult.INVALID;

    final String input = trimmed(code);
    boolean ok;
    if (isRecoveryCode(input)) {
      ok = consumeRecoveryCode(userId, input);
    } else if (twoFactor.get().getMethod() == TwoFactorMethod.TOTP) {
      ok = checkTotpCode(userId, twoFactor.get().getTotpSecret(), input);
    } else {
      ok = checkEmailCode(userId, input);
    }
    if (!ok) return VerifyResult.INVALID;

    twoFactorRepository.clearAttempts(userId).block(REDIS_TIMEOUT);
    twoFactor.get().setLastUsed(new Date());
    userTwoFactorRepository.saveAndFlush(twoFactor.get());
    return VerifyResult.OK;
  }

  /* ---- remembered browsers ---- */

  /**
   * Remember this browser so the second factor is skipped for {@link #TRUST_TTL}.
   *
   * @return the cookie value; only its hash is stored
   */
  @Transactional
  public String rememberDevice(final int userId) {
    final byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    final String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    final UserTwoFactorTrustedDevice device = new UserTwoFactorTrustedDevice();
    device.setUserId(userId);
    device.setTokenHash(PasswordResetService.hash(token));
    device.setCreated(new Date());
    device.setExpires(new Date(System.currentTimeMillis() + TRUST_TTL.toMillis()));
    trustedDeviceRepository.saveAndFlush(device);
    return token;
  }

  /**
   * @param userId user who passed the password check
   * @param token cookie value, may be null
   * @return true if the cookie names a remembered browser for this user that hasn't expired
   */
  @Transactional
  public boolean isTrustedDevice(final int userId, @Nullable final String token) {
    if (token == null || token.isBlank()) return false;
    final Optional<UserTwoFactorTrustedDevice> device =
        trustedDeviceRepository.findByTokenHash(PasswordResetService.hash(token));
    if (device.isEmpty() || device.get().getUserId() != userId) return false;
    if (device.get().getExpires().before(new Date())) {
      trustedDeviceRepository.delete(device.get());
      return false;
    }
    return true;
  }

  /* ---- helpers ---- */

  static boolean isRecoveryCode(final String code) {
    return code != null && RECOVERY_PATTERN.matcher(code).matches();
  }

  private boolean mailCode(final User user, final String email) {
    if (!Boolean.TRUE.equals(
        twoFactorRepository.tryAcquireEmail(user.getId(), EMAIL_THROTTLE).block(REDIS_TIMEOUT))) {
      log.info("Two factor code for {} requested again too soon", user.getUsername());
      return false;
    }

    final String code = newEmailCode();
    twoFactorRepository
        .saveEmailCode(user.getId(), PasswordResetService.hash(code), EMAIL_CODE_TTL)
        .block(REDIS_TIMEOUT);
    try {
      queueMail(email, "JustJournal: Your Login Code", codeMailBody(user, code), "two_factor_code", true);
    } catch (final RuntimeException e) {
      log.error("Could not mail a two factor code to {}", user.getUsername(), e);
      // nothing went out, so don't leave a code waiting or make the user wait to ask again
      twoFactorRepository.deleteEmailCode(user.getId()).block(REDIS_TIMEOUT);
      twoFactorRepository.releaseEmail(user.getId()).block(REDIS_TIMEOUT);
      throw new BadRequestException(CODE_NOT_MAILED);
    }
    return true;
  }

  private boolean checkEmailCode(final int userId, final String code) {
    if (code == null || code.length() != EMAIL_CODE_DIGITS) return false;
    final String expected = twoFactorRepository.getEmailCode(userId).block(REDIS_TIMEOUT);
    if (expected == null) return false;
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        PasswordResetService.hash(code).getBytes(StandardCharsets.US_ASCII))) {
      return false;
    }
    twoFactorRepository.deleteEmailCode(userId).block(REDIS_TIMEOUT);
    return true;
  }

  private boolean checkTotpCode(final int userId, final String secret, final String code) {
    final long step = Totp.matchingStep(secret, code, nowSeconds());
    if (step < 0) return false;
    // a code that was already accepted can't be used again inside its window
    return Boolean.TRUE.equals(
        twoFactorRepository.markTotpStepUsed(userId, step, TOTP_REPLAY_TTL).block(REDIS_TIMEOUT));
  }

  private boolean consumeRecoveryCode(final int userId, final String code) {
    final String normalized = code.replace("-", "").toUpperCase(Locale.ROOT);
    final Optional<UserTwoFactorRecoveryCode> match =
        recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(
            userId, PasswordResetService.hash(normalized));
    if (match.isEmpty()) return false;
    match.get().setUsedAt(new Date());
    recoveryCodeRepository.saveAndFlush(match.get());
    log.info("Recovery code used for user {}", userId);
    return true;
  }

  private List<String> replaceRecoveryCodes(final int userId) {
    recoveryCodeRepository.deleteByUserId(userId);
    final List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
    final Date now = new Date();
    for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
      final String code = newRecoveryCode();
      codes.add(code.substring(0, 5) + "-" + code.substring(5));

      final UserTwoFactorRecoveryCode row = new UserTwoFactorRecoveryCode();
      row.setUserId(userId);
      row.setCodeHash(PasswordResetService.hash(code));
      row.setCreated(now);
      recoveryCodeRepository.save(row);
    }
    recoveryCodeRepository.flush();
    return codes;
  }

  private String newEmailCode() {
    return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
  }

  private String newRecoveryCode() {
    final StringBuilder sb = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      sb.append(RECOVERY_ALPHABET.charAt(secureRandom.nextInt(RECOVERY_ALPHABET.length())));
    }
    return sb.toString();
  }

  private void requireNotEnabled(final int userId) {
    if (userTwoFactorRepository.existsByUserId(userId)) {
      throw new BadRequestException("Two-factor authentication is already turned on.");
    }
  }

  private void requireEnabled(final int userId) {
    if (!userTwoFactorRepository.existsByUserId(userId)) {
      throw new BadRequestException("Two-factor authentication is not turned on.");
    }
  }

  @Nullable
  private String emailFor(final User user) {
    final UserContact contact = userContactRepository.findByUser(user);
    if (contact == null || contact.getEmail() == null) return null;
    final String email = contact.getEmail().trim();
    return StringUtil.isEmailValid(email) ? email : null;
  }

  private String issuer() {
    final String name = settings.getSiteName();
    return name == null || name.isBlank() ? "JustJournal" : name.trim();
  }

  private static String trimmed(final String code) {
    return code == null ? "" : code.trim().replace(" ", "");
  }

  long nowSeconds() {
    return System.currentTimeMillis() / 1000L;
  }

  private String baseUri() {
    final String base = settings.getBaseUri();
    return base.endsWith("/") ? base : base + "/";
  }

  private String codeMailBody(final User user, final String code) {
    return "Your JustJournal login code for "
        + user.getUsername()
        + " is:\n\n    "
        + code
        + "\n\nIt is good for "
        + EMAIL_CODE_TTL.toMinutes()
        + " minutes. If you didn't try to log in, someone else knows your password: change it at "
        + baseUri()
        + "prefs/password\n";
  }

  private String enabledMailBody(final User user, final String how) {
    return "Two-factor authentication was just turned on for the JustJournal account "
        + user.getUsername()
        + " using "
        + how
        + ".\n\nFrom now on, logging in on the web site needs a code as well as the password."
        + " The XML-RPC interface and the older desktop clients can't ask for a code, so they"
        + " won't be able to log in while this is on.\n\n"
        + "If you didn't do this, reset your password at "
        + baseUri()
        + "password/forgot and contact the site administrator.\n";
  }

  private String disabledMailBody(final User user) {
    return "Two-factor authentication was just turned off for the JustJournal account "
        + user.getUsername()
        + ". Logging in now needs only the password.\n\n"
        + "If you didn't do this, reset your password at "
        + baseUri()
        + "password/forgot and contact the site administrator.\n";
  }

  /**
   * Queue a notice about a change to the account's second factor.
   *
   * @param required true when the account relies on e-mail, so a notice that can't be queued
   *     fails the change; otherwise the notice is queued in its own transaction and a failure is
   *     only logged
   */
  private void notify(
      final User user,
      final String subject,
      final String body,
      final String purpose,
      final boolean required) {
    final String email = emailFor(user);
    if (email == null) return;

    if (required) {
      try {
        queueMail(email, subject, body, purpose, false);
      } catch (final RuntimeException e) {
        log.error("Could not queue two factor notice for {}", user.getUsername(), e);
        throw new BadRequestException(NOTICE_NOT_MAILED);
      }
      return;
    }

    try {
      noticeTransaction.executeWithoutResult(status -> queueMail(email, subject, body, purpose, false));
    } catch (final RuntimeException e) {
      log.error("Could not queue two factor notice for {}", user.getUsername(), e);
    }
  }

  private void queueMail(
      final String to,
      final String subject,
      final String body,
      final String purpose,
      final boolean immediately) {
    final String from = settings.getMailFrom();
    final QueueMail mail = new QueueMail();
    mail.setFrom(from == null || from.isBlank() ? "root@localhost" : from);
    mail.setTo(to);
    mail.setSubject(subject);
    mail.setBody(body);
    mail.setPurpose(purpose);
    final QueueMail saved = queueMailRepository.saveAndFlush(mail);

    if (immediately) {
      // the scheduled sender runs every two minutes, too slow for a login code
      final MailSender sender = mailSender.getIfAvailable();
      if (sender != null && !sender.sendNow(saved)) {
        // the caller reports the failure, so the scheduler mustn't deliver it later
        queueMailRepository.deleteById(saved.getId());
        throw new IllegalStateException("Immediate send failed for " + purpose);
      }
    }
  }
}
