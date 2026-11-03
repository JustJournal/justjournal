package com.justjournal.services;

import com.justjournal.config.AdminProperties;
import com.justjournal.core.MailSender;
import com.justjournal.core.Settings;
import com.justjournal.model.QueueMail;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.cache.AdminTwoFactorRepository;
import com.justjournal.utility.StringUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Checks the site administrator's login: the password against {@code admin.password-hash}, then a
 * six digit code mailed to the site admin address. Codes are stored hashed in redis, are good for
 * {@link #CODE_TTL}, work once, and stop working after {@link #MAX_ATTEMPTS} wrong guesses.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class AdminLoginService {

  /** Result of mailing a code. */
  public enum SendResult {
    SENT,
    /** A code went out less than {@link #RESEND_THROTTLE} ago; that one still works. */
    THROTTLED,
    /** No site admin e-mail address is set. */
    NO_ADDRESS,
    /** The mail could not be queued or sent; no code is waiting. */
    FAILED
  }

  /** Result of checking a mailed code. */
  public enum VerifyResult {
    OK,
    INVALID,
    /** Too many wrong codes; the login has to start over. */
    LOCKED
  }

  public static final Duration CODE_TTL = Duration.ofMinutes(10);

  /** Minimum time between mailed codes. */
  static final Duration RESEND_THROTTLE = Duration.ofSeconds(60);

  /** Wrong codes are counted over this window, which starts with the first wrong one. */
  static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);

  static final int MAX_ATTEMPTS = 5;

  static final String MAIL_PURPOSE = "admin_login_code";

  private static final int CODE_DIGITS = 6;

  private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(10);

  /** Upper bounds on the hash's cost settings, so a mistyped config can't stall every login. */
  private static final int MAX_MEMORY_KB = 4 * 1024 * 1024;

  private static final int MAX_ITERATIONS = 64;

  private static final int MAX_PARALLELISM = 64;

  private final SecureRandom secureRandom = new SecureRandom();

  private final AdminProperties adminProperties;

  private final AdminTwoFactorRepository adminTwoFactorRepository;

  private final QueueMailRepository queueMailRepository;

  private final ObjectProvider<MailSender> mailSender;

  private final Settings settings;

  public AdminLoginService(
      final AdminProperties adminProperties,
      final AdminTwoFactorRepository adminTwoFactorRepository,
      final QueueMailRepository queueMailRepository,
      final ObjectProvider<MailSender> mailSender,
      final Settings settings) {
    this.adminProperties = adminProperties;
    this.adminTwoFactorRepository = adminTwoFactorRepository;
    this.queueMailRepository = queueMailRepository;
    this.mailSender = mailSender;
    this.settings = settings;
  }

  /* ---- password ---- */

  /**
   * @return true only if the admin area is turned on and both the username and the password match.
   *     The password hash is always computed, so a wrong username takes as long as a wrong
   *     password.
   */
  public boolean checkCredentials(
      @Nullable final String username, @Nullable final String password) {
    if (!adminProperties.isEnabled()) return false;
    final boolean userOk =
        MessageDigest.isEqual(
            sha256(username == null ? "" : username.trim()),
            sha256(adminProperties.getUsername()));
    final boolean passwordOk =
        verifyArgon2id(password == null ? "" : password, adminProperties.getPasswordHash());
    return userOk & passwordOk;
  }

  /**
   * Check a password against an argon2id string such as {@code
   * $argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>}, taking the cost settings from the string. Salt
   * and hash may be base64 with or without padding.
   *
   * @return true if the password matches; false if it doesn't or the string can't be read
   */
  static boolean verifyArgon2id(final String password, final String encoded) {
    if (password == null || encoded == null) return false;
    final String[] parts = encoded.trim().split("\\$", -1);
    // "", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash
    if (parts.length != 6 || !parts[0].isEmpty() || !"argon2id".equals(parts[1])) {
      log.warn("admin.password-hash is not an argon2id string");
      return false;
    }
    if (!"v=19".equals(parts[2])) {
      log.warn("admin.password-hash has an unsupported argon2 version");
      return false;
    }

    int memory = -1;
    int iterations = -1;
    int parallelism = -1;
    final byte[] salt;
    final byte[] expected;
    try {
      for (final String param : parts[3].split(",")) {
        final String[] kv = param.split("=", 2);
        if (kv.length != 2) throw new IllegalArgumentException("bad parameter " + param);
        final int value = Integer.parseInt(kv[1].trim());
        switch (kv[0].trim()) {
          case "m" -> memory = value;
          case "t" -> iterations = value;
          case "p" -> parallelism = value;
          default -> throw new IllegalArgumentException("unknown parameter " + kv[0]);
        }
      }
      salt = Base64.getDecoder().decode(parts[4]);
      expected = Base64.getDecoder().decode(parts[5]);
    } catch (final IllegalArgumentException e) {
      log.warn("admin.password-hash could not be read: {}", e.getMessage());
      return false;
    }
    if (parallelism < 1
        || parallelism > MAX_PARALLELISM
        || iterations < 1
        || iterations > MAX_ITERATIONS
        || memory < 8 * parallelism
        || memory > MAX_MEMORY_KB
        || salt.length < 8
        || expected.length < 4) {
      log.warn("admin.password-hash has cost settings or lengths out of range");
      return false;
    }

    final Argon2Parameters params =
        new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memory)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build();
    final Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(params);
    final byte[] actual = new byte[expected.length];
    generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), actual);
    return MessageDigest.isEqual(expected, actual);
  }

  /* ---- mailed code ---- */

  /** @return true if too many wrong codes were tried lately for a new login to start */
  public boolean isLocked() {
    final String attempts =
        adminTwoFactorRepository.getAttempts(adminName()).block(REDIS_TIMEOUT);
    if (attempts == null) return false;
    try {
      return Long.parseLong(attempts.trim()) >= MAX_ATTEMPTS;
    } catch (final NumberFormatException e) {
      return false;
    }
  }

  /**
   * Mail a new code to the site admin address, replacing any earlier one.
   *
   * @param requestedFrom client address, mentioned in the mail
   */
  public SendResult sendCode(@Nullable final String requestedFrom) {
    final String email = adminEmail();
    if (email == null) {
      log.error("Admin login code can't be mailed: the site admin e-mail address is not set");
      return SendResult.NO_ADDRESS;
    }

    final String name = adminName();
    if (!Boolean.TRUE.equals(
        adminTwoFactorRepository.tryAcquireSend(name, RESEND_THROTTLE).block(REDIS_TIMEOUT))) {
      log.info("Admin login code requested again too soon");
      return SendResult.THROTTLED;
    }

    final String code = newCode();
    adminTwoFactorRepository
        .saveCode(name, PasswordResetService.hash(code), CODE_TTL)
        .block(REDIS_TIMEOUT);
    try {
      queueMail(email, "JustJournal: Your Admin Login Code", mailBody(code, requestedFrom));
    } catch (final RuntimeException e) {
      log.error("Could not mail the admin login code", e);
      // nothing went out, so don't leave a code waiting or make the admin wait to ask again
      adminTwoFactorRepository.deleteCode(name).block(REDIS_TIMEOUT);
      adminTwoFactorRepository.releaseSend(name).block(REDIS_TIMEOUT);
      return SendResult.FAILED;
    }
    return SendResult.SENT;
  }

  /**
   * Check a mailed code. A right code is used up; the {@link #MAX_ATTEMPTS}th wrong one in the
   * window also throws the outstanding code away.
   */
  public VerifyResult verifyCode(@Nullable final String code) {
    final String name = adminName();
    final Long attempts =
        adminTwoFactorRepository.incrementAttempts(name, ATTEMPT_WINDOW).block(REDIS_TIMEOUT);
    final long count = attempts == null ? 0L : attempts;
    if (count > MAX_ATTEMPTS) {
      log.warn("Admin login code attempts exhausted");
      adminTwoFactorRepository.deleteCode(name).block(REDIS_TIMEOUT);
      return VerifyResult.LOCKED;
    }

    if (checkCode(name, trimmed(code))) {
      adminTwoFactorRepository.clearAttempts(name).block(REDIS_TIMEOUT);
      return VerifyResult.OK;
    }
    if (count >= MAX_ATTEMPTS) {
      log.warn("Admin login code attempts exhausted");
      adminTwoFactorRepository.deleteCode(name).block(REDIS_TIMEOUT);
      return VerifyResult.LOCKED;
    }
    return VerifyResult.INVALID;
  }

  /* ---- helpers ---- */

  private boolean checkCode(final String name, final String code) {
    if (code.length() != CODE_DIGITS) return false;
    final String expected = adminTwoFactorRepository.getCode(name).block(REDIS_TIMEOUT);
    if (expected == null) return false;
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        PasswordResetService.hash(code).getBytes(StandardCharsets.US_ASCII))) {
      return false;
    }
    adminTwoFactorRepository.deleteCode(name).block(REDIS_TIMEOUT);
    return true;
  }

  private String adminName() {
    return adminProperties.getUsername();
  }

  @Nullable
  private String adminEmail() {
    final String email = settings.getSiteAdminEmail();
    if (email == null || email.isBlank()) return null;
    final String trimmed = email.trim();
    return StringUtil.isEmailValid(trimmed) ? trimmed : null;
  }

  private String newCode() {
    return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
  }

  private static String trimmed(@Nullable final String code) {
    return code == null ? "" : code.trim().replace(" ", "");
  }

  private static byte[] sha256(final String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String mailBody(final String code, @Nullable final String requestedFrom) {
    return "Your JustJournal admin login code is:\n\n    "
        + code
        + "\n\nIt is good for "
        + CODE_TTL.toMinutes()
        + " minutes."
        + (requestedFrom == null || requestedFrom.isBlank()
            ? ""
            : " The login came from " + requestedFrom + ".")
        + " If you didn't try to log in to the admin area, someone else knows the admin password:"
        + " change admin.password-hash right away.\n";
  }

  private void queueMail(final String to, final String subject, final String body) {
    final String from = settings.getMailFrom();
    final QueueMail mail = new QueueMail();
    mail.setFrom(from == null || from.isBlank() ? "root@localhost" : from);
    mail.setTo(to);
    mail.setSubject(subject);
    mail.setBody(body);
    mail.setPurpose(MAIL_PURPOSE);
    final QueueMail saved = queueMailRepository.saveAndFlush(mail);

    // the scheduled sender runs every two minutes, too slow for a login code
    final MailSender sender = mailSender.getIfAvailable();
    if (sender != null && !sender.sendNow(saved)) {
      // the caller reports the failure, so the scheduler mustn't deliver it later
      queueMailRepository.deleteById(saved.getId());
      throw new IllegalStateException("Immediate send failed for " + MAIL_PURPOSE);
    }
  }
}
