package com.justjournal.ctl.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.justjournal.config.AdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** @author Lucas Holt */
class AdminInterceptorTests {

  private static final String HASH = "$argon2id$v=19$m=16,t=1,p=1$c2FsdA$aGFzaA";

  private final AdminInterceptor interceptor =
      new AdminInterceptor(new AdminProperties("admin", HASH));

  private MockHttpSession session;

  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    response = new MockHttpServletResponse();
  }

  private MockHttpServletRequest request(final String method, final String uri) {
    final MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setSession(session);
    return request;
  }

  private MockHttpServletRequest post(final String uri, final String token) {
    final MockHttpServletRequest request = request("POST", uri);
    if (token != null) request.setParameter(AdminSession.CSRF_PARAM, token);
    return request;
  }

  private boolean handle(final MockHttpServletRequest request) throws Exception {
    return interceptor.preHandle(request, response, new Object());
  }

  @Test
  void everythingIsNotFoundWhileTheAdminAreaIsOff() throws Exception {
    for (final AdminProperties off :
        new AdminProperties[] {
          new AdminProperties("", "$argon2id$x"),
          new AdminProperties("admin", " "),
          new AdminProperties(null, null)
        }) {
      final MockHttpServletResponse notFound = new MockHttpServletResponse();
      final AdminInterceptor guard = new AdminInterceptor(off);
      assertFalse(guard.preHandle(request("GET", "/admin/login"), notFound, new Object()));
      assertEquals(404, notFound.getStatus());
    }
  }

  @Test
  void pagesAreNeverCachedOrFramed() throws Exception {
    handle(request("GET", "/admin/login"));

    assertEquals("no-store", response.getHeader("Cache-Control"));
    assertEquals("DENY", response.getHeader("X-Frame-Options"));
    assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
  }

  @Test
  void loginCodeAndLogoutAreOpen() throws Exception {
    assertTrue(handle(request("GET", "/admin/login")));
    assertTrue(handle(request("GET", "/admin/login/")));
    assertTrue(handle(request("GET", "/admin/code")));
    assertNull(response.getRedirectedUrl());
  }

  @Test
  void otherPagesSendAStrangerToTheLogin() throws Exception {
    assertFalse(handle(request("GET", "/admin/images")));
    assertEquals("/admin/login", response.getRedirectedUrl());

    response = new MockHttpServletResponse();
    assertFalse(handle(request("GET", "/admin")));
    assertEquals("/admin/login", response.getRedirectedUrl());
  }

  @Test
  void redirectKeepsTheContextPath() throws Exception {
    final MockHttpServletRequest request = request("GET", "/jj/admin/settings");
    request.setContextPath("/jj");

    assertFalse(handle(request));
    assertEquals("/jj/admin/login", response.getRedirectedUrl());

    final MockHttpServletRequest open = request("GET", "/jj/admin/login");
    open.setContextPath("/jj");
    assertTrue(interceptor.preHandle(open, new MockHttpServletResponse(), new Object()));
  }

  @Test
  void pendingLoginIsNotAnAdmin() throws Exception {
    AdminLoginController.setPending(session, "admin");

    assertFalse(handle(request("GET", "/admin/images")));
    assertEquals("/admin/login", response.getRedirectedUrl());
  }

  @Test
  void adminReachesEveryPage() throws Exception {
    session.setAttribute(AdminSession.ADMIN_ATTR, "admin");

    assertTrue(handle(request("GET", "/admin/images")));
    assertTrue(handle(request("GET", "/admin")));
    assertTrue(handle(post("/admin/settings", AdminSession.csrfToken(session))));
  }

  @Test
  void postWithoutASessionIsForbidden() throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/login");
    request.setParameter(AdminSession.CSRF_PARAM, "anything");

    assertFalse(handle(request));
    assertEquals(403, response.getStatus());
  }

  @Test
  void postWithoutATokenIsForbidden() throws Exception {
    AdminSession.csrfToken(session);

    assertFalse(handle(post("/admin/login", null)));
    assertEquals(403, response.getStatus());
  }

  @Test
  void postWithTheWrongTokenIsForbidden() throws Exception {
    AdminSession.csrfToken(session);

    assertFalse(handle(post("/admin/login", "not-the-token")));
    assertEquals(403, response.getStatus());
  }

  @Test
  void postBeforeAnyTokenWasHandedOutIsForbidden() throws Exception {
    assertFalse(handle(post("/admin/login", "")));
    assertEquals(403, response.getStatus());
  }

  @Test
  void adminPostStillNeedsTheToken() throws Exception {
    session.setAttribute(AdminSession.ADMIN_ATTR, "admin");
    AdminSession.csrfToken(session);

    assertFalse(handle(post("/admin/settings", "not-the-token")));
    assertEquals(403, response.getStatus());
  }

  @Test
  void postWithTheRightTokenGoesThrough() throws Exception {
    final String token = AdminSession.csrfToken(session);

    assertTrue(handle(post("/admin/login", token)));
    assertTrue(handle(post("/admin/code", token)));
    assertTrue(handle(post("/admin/logout", token)));
    assertEquals(200, response.getStatus());
  }

  @Test
  void strangerPostWithTheRightTokenIsStillSentToTheLogin() throws Exception {
    assertFalse(handle(post("/admin/settings", AdminSession.csrfToken(session))));
    assertEquals("/admin/login", response.getRedirectedUrl());
  }
}
