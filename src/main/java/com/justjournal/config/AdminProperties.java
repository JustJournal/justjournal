package com.justjournal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The site administrator's login, from {@code admin.username} and {@code admin.password-hash}. The
 * hash is an argon2id string in the same format member passwords are stored in. With either one
 * blank the admin area is turned off and every {@code /admin} address answers 404.
 *
 * @author Lucas Holt
 */
@Component
public class AdminProperties {

  private final String username;

  private final String passwordHash;

  public AdminProperties(
      @Value("${admin.username:}") final String username,
      @Value("${admin.password-hash:}") final String passwordHash) {
    this.username = username == null ? "" : username.trim();
    this.passwordHash = passwordHash == null ? "" : passwordHash.trim();
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isEnabled() {
    return !username.isEmpty() && !passwordHash.isEmpty();
  }
}
