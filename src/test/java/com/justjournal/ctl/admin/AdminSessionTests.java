package com.justjournal.ctl.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** @author Lucas Holt */
class AdminSessionTests {

  @Test
  void onlyANamedAdminIsAnAdmin() {
    final MockHttpSession session = new MockHttpSession();

    assertFalse(AdminSession.isAdmin(null));
    assertFalse(AdminSession.isAdmin(session));
    session.setAttribute(AdminSession.ADMIN_ATTR, "");
    assertFalse(AdminSession.isAdmin(session));
    session.setAttribute(AdminSession.ADMIN_ATTR, Boolean.TRUE);
    assertFalse(AdminSession.isAdmin(session));
    session.setAttribute(AdminSession.ADMIN_ATTR, "admin");
    assertTrue(AdminSession.isAdmin(session));
  }

  @Test
  void tokenIsMadeOncePerSession() {
    final MockHttpSession session = new MockHttpSession();

    final String token = AdminSession.csrfToken(session);

    assertEquals(token, AdminSession.csrfToken(session));
    assertEquals(token, session.getAttribute(AdminSession.CSRF_ATTR));
    assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);
    assertNotEquals(token, AdminSession.csrfToken(new MockHttpSession()));
  }

  @Test
  void onlyTheSessionsOwnTokenIsValid() {
    final MockHttpSession session = new MockHttpSession();

    assertFalse(AdminSession.isValidCsrf(null, "x"));
    assertFalse(AdminSession.isValidCsrf(session, "x"));

    final String token = AdminSession.csrfToken(session);
    assertTrue(AdminSession.isValidCsrf(session, token));
    assertFalse(AdminSession.isValidCsrf(session, null));
    assertFalse(AdminSession.isValidCsrf(session, ""));
    assertFalse(AdminSession.isValidCsrf(session, token + "x"));
    assertFalse(AdminSession.isValidCsrf(session, AdminSession.csrfToken(new MockHttpSession())));
  }
}
