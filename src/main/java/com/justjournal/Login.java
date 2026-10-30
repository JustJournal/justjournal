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
package com.justjournal;

import static com.justjournal.core.Constants.*;

import com.justjournal.exception.HashNotSupportedException;
import com.justjournal.model.PasswordType;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.UserTwoFactorRepository;
import com.justjournal.repository.cache.TrackBackIpRepository;
import com.justjournal.utility.StringUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Provides authentication and password management services to web applications using the just
 * journal data tier.
 *
 * <p>Created on March 23, 2003, 2:34 PM
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class Login {

  /** How long a session may sit between the password check and the second factor. */
  public static final Duration PENDING_LOGIN_TTL = Duration.ofMinutes(10);

  private final UserRepository userRepository;

  private final TrackBackIpRepository trackBackIpRepository;

  private final UserTwoFactorRepository userTwoFactorRepository;

  public Login(
      final UserRepository userRepository,
      final TrackBackIpRepository trackBackIpRepository,
      final UserTwoFactorRepository userTwoFactorRepository) {
    this.userRepository = userRepository;
    this.trackBackIpRepository = trackBackIpRepository;
    this.userTwoFactorRepository = userTwoFactorRepository;
  }

  public static boolean isAuthenticated(HttpSession session) {
    final String username = (String) session.getAttribute(LOGIN_ATTRNAME);
    return username != null && !username.isEmpty();
  }

  @Nullable
  public static String currentLoginName(HttpSession session) {
    return (String) session.getAttribute(LOGIN_ATTRNAME);
  }

  public static int currentLoginId(HttpSession session) {
    int aUserID = 0;
    final Integer userIDasi = (Integer) session.getAttribute(LOGIN_ATTRID);

    if (userIDasi != null) {
      aUserID = userIDasi;
    }

    return aUserID;
  }

  protected static void logout(HttpSession session) {
    session.removeAttribute(LOGIN_ATTRNAME);
    session.removeAttribute(LOGIN_ATTRID);
  }

  /**
   * Has this session passed the password check and is now waiting on a second factor? Such a
   * session is not logged in; {@link #isAuthenticated(HttpSession)} stays false.
   */
  public static boolean isPendingSecondFactor(HttpSession session) {
    return session.getAttribute(LOGIN_PENDING_ATTRID) instanceof Integer id
        && id > BAD_USER_ID
        && session.getAttribute(LOGIN_PENDING_SINCE) instanceof Long since
        && System.currentTimeMillis() - since <= PENDING_LOGIN_TTL.toMillis();
  }

  public static int pendingLoginId(HttpSession session) {
    if (!isPendingSecondFactor(session)) return BAD_USER_ID;
    return (Integer) session.getAttribute(LOGIN_PENDING_ATTRID);
  }

  @Nullable
  public static String pendingLoginName(HttpSession session) {
    if (!isPendingSecondFactor(session)) return null;
    return (String) session.getAttribute(LOGIN_PENDING_ATTRNAME);
  }

  @Nullable
  public static TwoFactorMethod pendingMethod(HttpSession session) {
    if (!isPendingSecondFactor(session)) return null;
    return (TwoFactorMethod) session.getAttribute(LOGIN_PENDING_METHOD);
  }

  public static void setPendingSecondFactor(
      HttpSession session, int userId, String userName, TwoFactorMethod method) {
    session.setAttribute(LOGIN_PENDING_ATTRID, userId);
    session.setAttribute(LOGIN_PENDING_ATTRNAME, userName);
    session.setAttribute(LOGIN_PENDING_METHOD, method);
    session.setAttribute(LOGIN_PENDING_SINCE, System.currentTimeMillis());
  }

  public static void clearPendingSecondFactor(HttpSession session) {
    session.removeAttribute(LOGIN_PENDING_ATTRID);
    session.removeAttribute(LOGIN_PENDING_ATTRNAME);
    session.removeAttribute(LOGIN_PENDING_METHOD);
    session.removeAttribute(LOGIN_PENDING_SINCE);
  }

  /**
   * Mark the session as logged in once every factor has been checked. The session id is changed so
   * an id handed out before the login can't be used to ride on it.
   *
   * @param request current request, must already have a session
   * @param userId user who logged in
   * @param userName username as entered
   */
  public void completeLogin(HttpServletRequest request, int userId, String userName) {
    final HttpSession session = request.getSession(true);
    clearPendingSecondFactor(session);
    request.changeSessionId();
    session.setAttribute(LOGIN_ATTRID, userId);
    session.setAttribute(LOGIN_ATTRNAME, userName);
    setLastLogin(userId);
  }

  public static boolean isUserName(@Nullable String input) {
    if (!StringUtil.lengthCheck(input, USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH)) {
      return false;
    }

    @SuppressWarnings("java:S6353") // false positive. we don't want extra characters from a \W+
    final Pattern p = Pattern.compile("[A-Za-z0-9_]+");
    final Matcher m = p.matcher(input);

    return m.matches(); // valid on true
  }

  /**
   * Check if a password is valid in terms of characters used.
   *
   * @param password clear text password
   * @return true if valid, false otherwise
   */
  /** Shown when a freshly chosen password breaks {@link #isNewPassword(String)}. */
  public static final String NEW_PASSWORD_RULE =
      "Password must be "
          + NEW_PASSWORD_MIN_LENGTH
          + "-"
          + PASSWORD_MAX_LENGTH
          + " characters using letters, numbers, spaces and _ @ . ! & * # $ ? ^";

  /**
   * Rule for a password being set now (signup, change, reset). Stricter than {@link
   * #isPassword(String)}, which stays lenient so existing short passwords still log in.
   */
  public static boolean isNewPassword(@Nullable String password) {
    return isPassword(password) && password.length() >= NEW_PASSWORD_MIN_LENGTH;
  }

  public static boolean isPassword(String password) {
    if (!StringUtil.lengthCheck(password, PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH)) {
      return false;
    }
    final Pattern p = Pattern.compile("[A-Za-z0-9_@.!&*#$?^ ]+");
    final Matcher m = p.matcher(password);

    return m.matches(); // valid on true
  }

  @NotNull
  private static String convertToHex(byte[] data) {
    final StringBuilder buf = new StringBuilder();
    for (final byte aData : data) {
      int halfByte = (aData >>> 4) & 0x0F;
      int twoHalves = 0;
      do {
        if ((0 <= halfByte) && (halfByte <= 9)) buf.append((char) ('0' + halfByte));
        else buf.append((char) ('a' + (halfByte - 10)));
        halfByte = aData & 0x0F;
      } while (twoHalves++ < 1); // TODO: wtf?
    }
    return buf.toString();
  }

  // Do not use for new passwords
  @Deprecated(forRemoval = true, since = "3.1.12")
  @NotNull
  public String sha1(String text) throws NoSuchAlgorithmException {
    final MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] sha1hash;

    md.update(text.getBytes(StandardCharsets.ISO_8859_1), 0, text.length());
    sha1hash = md.digest();
    return convertToHex(sha1hash);
  }

  @NotNull
  public String sha256(String text) throws NoSuchAlgorithmException {
    final MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] sha2hash;

    md.update(text.getBytes(StandardCharsets.ISO_8859_1), 0, text.length());
    sha2hash = md.digest();
    return convertToHex(sha2hash);
  }

  @NotNull
  public String argon2Hash(String text) {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);

    Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(4)
            .withMemoryAsKB(65536)  // 64 MB
            .withIterations(3);

    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(builder.build());

    byte[] result = new byte[32];
    generator.generateBytes(text.getBytes(StandardCharsets.UTF_8), result);

    String hash = Base64.getEncoder().encodeToString(result);
    String saltString = Base64.getEncoder().encodeToString(salt);

    return String.format("$argon2id$v=19$m=65536,t=3,p=4$%s$%s", saltString, hash);
  }

  public boolean isIpSketch() {
    final String ip = com.justjournal.utility.RequestUtil.getRemoteIP();
    var result = trackBackIpRepository.getIpAddress(ip).blockOptional(Duration.ofMinutes(1));
    if (result.isPresent() && !"0.0.0.0".equals(result.get())) {
      log.warn("Multiple requests during timeout period from IP ADDRESS {} for TrackBack.", ip);
      return true;
    }
    return false;
  }

  public void blockIp(int seconds) {
    final String ip = com.justjournal.utility.RequestUtil.getRemoteIP();
    trackBackIpRepository.saveIpAddress(ip, seconds).block(Duration.ofMinutes(1));
  }

  /**
   * Authenticate the user using clear text username and password. This is a complete login, so
   * users who turned on a second factor are refused here: callers that can't ask for a code (XML-RPC
   * and the old desktop clients) must not get in with the password alone. Web logins go through
   * {@link #validatePassword(String, String)} and then the second factor.
   *
   * @param userName Three to Fifteen characters...
   * @param password Clear text password
   * @return user id of the user who logged in or 0 if the login failed.
   */
  public int validate(String userName, String password) {
    final int userId = validatePassword(userName, password);
    if (userId == BAD_USER_ID) return BAD_USER_ID;

    if (hasSecondFactor(userId)) {
      log.warn(
          "Password only login refused for {}: two factor authentication is turned on", userName);
      return BAD_USER_ID;
    }

    setLastLogin(userId);
    return userId;
  }

  /**
   * Does the user require a second factor after the password?
   *
   * @param userId user id
   * @return true if two factor authentication is turned on for the account
   */
  public boolean hasSecondFactor(final int userId) {
    return userId > BAD_USER_ID && userTwoFactorRepository.existsByUserId(userId);
  }

  /**
   * Check the username and password only. Does not record the login; the caller finishes the
   * login once any second factor is satisfied.
   *
   * @param userName Three to Fifteen characters...
   * @param password Clear text password
   * @return user id if the password is right or 0 if it isn't.
   */
  public int validatePassword(String userName, String password) {

    if (isIpSketch()) {
      blockIp(30); // second+ attempt, bump block time.
      return BAD_USER_ID;
    }

    if (!isUserName(userName)) {
      blockIp(5);
      return BAD_USER_ID; // bad username
    }

    if (!isPassword(password)) {
      blockIp(5);
      return BAD_USER_ID; // bad password
    }

    try {
      // the password is sha1 or sha256 hashed in the mysql server
      final int userId = lookupUserId(userName, password);
      if (userId == BAD_USER_ID) {
        blockIp(5);
      }
      return userId;
    } catch (final Exception e) {
      log.error("validatePassword(): {}", e.getMessage());
    }
    return BAD_USER_ID;
  }

  private int lookupUserId(final String userName, final String password) {
    final com.justjournal.model.User user = lookupUser(userName, password);
    if (user == null) return BAD_USER_ID;
    return user.getId();
  }

  public void setLastLogin(final int id) {
    /* verify it's a real login */
    if (id < 1) return;

    try {
      final com.justjournal.model.User user = userRepository.findById(id).orElse(null);
      if (user == null) throw new IllegalArgumentException("id");

      user.setLastLogin(new java.util.Date());
      userRepository.saveAndFlush(user);
    } catch (final Exception e) {
        log.error("setLastLogin(): {}", e.getMessage());
    }
  }

  /**
   * Change the password for an account given the username, old and new passwords.
   *
   * @param userName username
   * @param password existing password
   * @param newPass new password
   * @return true if the password change worked.
   */
  public boolean changePass(final String userName, final String password, final String newPass) {
    final int uid;

    try {
      // password only: the caller is already in a logged in session, second factor included
      uid = validatePassword(userName, password);

      if (uid > BAD_USER_ID && isNewPassword(newPass)) {
        final com.justjournal.model.User user = lookupUser(userName, password);
        if (user == null) {
          log.error("changePass(): Unable to find user.");
          return false;
        }

        user.setPassword(getHashedPassword(userName, newPass));
        user.setPasswordType(PasswordType.ARGON2);
        userRepository.saveAndFlush(user);

        return true;
      }
    } catch (final Exception e) {
        log.error("changePass(): {}", e.getMessage());
    }

    return false;
  }

  /**
   * Set a new password without the old one. Only for callers that have already proven ownership
   * of the account another way, such as a password reset link.
   *
   * @param userId user id
   * @param newPass new password
   * @return true if the password was changed.
   */
  public boolean resetPass(final int userId, final String newPass) {
    if (userId < 1 || !isNewPassword(newPass)) return false;

    try {
      final com.justjournal.model.User user = userRepository.findById(userId).orElse(null);
      if (user == null) {
        log.error("resetPass(): Unable to find user {}", userId);
        return false;
      }

      user.setPassword(getHashedPassword(user.getUsername(), newPass));
      user.setPasswordType(PasswordType.ARGON2);
      userRepository.saveAndFlush(user);
      return true;
    } catch (final Exception e) {
      log.error("resetPass(): {}", e.getMessage());
    }

    return false;
  }

  @NotNull
  public String getHashedPassword(@NotNull final String userName, @NotNull final String password) {
    try {
      return argon2Hash(userName + password);
    } catch (final Exception e) {
      log.error("Invalid password hash algorithm?", e);
      throw new HashNotSupportedException();
    }
  }

  public boolean exists(final String userName) {
      return isUserName(userName) && userRepository.findByUsername(userName) != null;
  }

  @Nullable
  private com.justjournal.model.User lookupUser(@NotNull final String userName, @NotNull final String password) {
    try {
      com.justjournal.model.User user = userRepository.findByUsername(userName);

      if (user !=null) {
        String storedHash = user.getPassword();
        if (verifyArgon2Hash(userName + password, storedHash)) {
          return user;
        }

        // If the stored password is still in SHA-256 format, upgrade it
        if (storedHash.equals(sha256(userName + password))) {
          user.setPassword(getHashedPassword(userName, password));
          user.setPasswordType(PasswordType.ARGON2);
          return userRepository.saveAndFlush(user);
        }

        if (storedHash.equals(sha1(password))) {
          user.setPassword(getHashedPassword(userName, password));
          user.setPasswordType(PasswordType.ARGON2);
          return userRepository.saveAndFlush(user);
        }
      } else {
        log.warn("User not found: {}", userName);
      }

      return null;
    } catch (final Exception e) {
      log.error("Couldn't lookup user", e);
      throw new HashNotSupportedException();
    }
  }

  private boolean verifyArgon2Hash(String password, String storedHash) {
    String[] parts = storedHash.split("\\$");
    if (parts.length != 6) {
      return false;
    }
    // Argon2 hash format: $argon2id$v=19$m=65536,t=3,p=4$salt$hash
    String salt = parts[4];
    String hash = parts[5];

    Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(Base64.getDecoder().decode(salt))
            .withParallelism(4)
            .withMemoryAsKB(65536)
            .withIterations(3);

    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(builder.build());

    byte[] result = new byte[32];
    generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), result);

    String newHash = Base64.getEncoder().encodeToString(result);
    return hash.equals(newHash);
  }
}
