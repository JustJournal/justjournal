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
import com.justjournal.repository.UserRepository;
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
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
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

  private final UserRepository userRepository;

  private final TrackBackIpRepository trackBackIpRepository;

  @Autowired
  public Login(final UserRepository userRepository, TrackBackIpRepository trackBackIpRepository) {
    this.userRepository = userRepository;
    this.trackBackIpRepository = trackBackIpRepository;
  }

  public static boolean isAuthenticated(final HttpSession session) {
    final String username = (String) session.getAttribute(LOGIN_ATTRNAME);
    return username != null && !username.isEmpty();
  }

  @Nullable
  public static String currentLoginName(final HttpSession session) {
    return (String) session.getAttribute(LOGIN_ATTRNAME);
  }

  public static int currentLoginId(final HttpSession session) {
    int aUserID = 0;
    final Integer userIDasi = (Integer) session.getAttribute(LOGIN_ATTRID);

    if (userIDasi != null) {
      aUserID = userIDasi;
    }

    return aUserID;
  }

  protected static void logout(final HttpSession session) {
    session.removeAttribute(LOGIN_ATTRNAME);
    session.removeAttribute(LOGIN_ATTRID);
  }

  public static boolean isUserName(final String input) {
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
  public static boolean isPassword(final String password) {
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
  public static String sha1(final String text) throws NoSuchAlgorithmException {
    final MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] sha1hash;

    md.update(text.getBytes(StandardCharsets.ISO_8859_1), 0, text.length());
    sha1hash = md.digest();
    return convertToHex(sha1hash);
  }

  @NotNull
  public static String sha256(final String text) throws NoSuchAlgorithmException {
    final MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] sha2hash;

    md.update(text.getBytes(StandardCharsets.ISO_8859_1), 0, text.length());
    sha2hash = md.digest();
    return convertToHex(sha2hash);
  }

  @NotNull
  public static String argon2Hash(final String text) {
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
   * Authenticate the user using clear text username and password.
   *
   * @param userName Three to Fifteen characters...
   * @param password Clear text password
   * @return user id of the user who logged in or 0 if the login failed.
   */
  public int validate(final String userName, final String password) {

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
      } else {
        setLastLogin(userId);
      }
      return userId;
    } catch (final Exception e) {
      log.error("validate(): {}", e.getMessage());
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
      uid = validate(userName, password);

      if (uid > BAD_USER_ID && isPassword(newPass)) {
        final com.justjournal.model.User user = lookupUser(userName, password);
        user.setPassword(getHashedPassword(userName, newPass));
        user.setPasswordType(PasswordType.SHA256);
        userRepository.saveAndFlush(user);

        return true;
      }
    } catch (final Exception e) {
        log.error("changePass(): {}", e.getMessage());
    }

    return false;
  }

  @NotNull
  public static String getHashedPassword(final String userName, final String password) {
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
      }

      return null;
    } catch (final Exception e) {
      log.error("Couldn't lookup user", e);
      throw new HashNotSupportedException();
    }
  }

  private boolean verifyArgon2Hash(String password, String storedHash) {
    String[] parts = storedHash.split("\\$");
    if (parts.length != 5) {
      return false;
    }
    String salt = parts[3];
    String hash = parts[4];

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
