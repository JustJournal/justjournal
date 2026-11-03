package com.justjournal.ctl.admin;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Admin state kept in the HTTP session. Being logged in as a member says nothing about being the
 * administrator: the admin flag is only set once the password and the mailed code have both been
 * checked.
 *
 * @author Lucas Holt
 */
public final class AdminSession {

  /** Set to the admin username once both factors have been checked. */
  public static final String ADMIN_ATTR = "admin.auth";

  /** Per session token every admin form posts back. */
  public static final String CSRF_ATTR = "admin.csrf";

  /** Name of the hidden form field carrying the token. */
  public static final String CSRF_PARAM = "_csrf";

  /** Model attribute admin templates read the token from. */
  public static final String CSRF_MODEL = "csrfToken";

  private static final SecureRandom RANDOM = new SecureRandom();

  private AdminSession() {}

  public static boolean isAdmin(final HttpSession session) {
    return session != null
        && session.getAttribute(ADMIN_ATTR) instanceof String name
        && !name.isEmpty();
  }

  /** @return the session's form token, creating one the first time */
  public static String csrfToken(final HttpSession session) {
    if (session.getAttribute(CSRF_ATTR) instanceof String token) return token;
    final byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    session.setAttribute(CSRF_ATTR, token);
    return token;
  }

  /** @return true if the posted token is the one this session handed out */
  public static boolean isValidCsrf(final HttpSession session, final String posted) {
    if (session == null || posted == null) return false;
    if (!(session.getAttribute(CSRF_ATTR) instanceof String expected)) return false;
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), posted.getBytes(StandardCharsets.US_ASCII));
  }
}
