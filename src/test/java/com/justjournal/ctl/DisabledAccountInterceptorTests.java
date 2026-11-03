package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.model.TwoFactorMethod;
import com.justjournal.services.DisabledAccounts;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.HandlerMapping;

/** Sessions of disabled accounts are logged out; addresses naming them answer 404. */
class DisabledAccountInterceptorTests {

  private DisabledAccounts disabledAccounts;
  private DisabledAccountInterceptor interceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    disabledAccounts = mock(DisabledAccounts.class);
    when(disabledAccounts.isDisabled(7)).thenReturn(true);
    when(disabledAccounts.isDisabled("mallory")).thenReturn(true);
    interceptor = new DisabledAccountInterceptor(disabledAccounts);
    session = new MockHttpSession();
    request = new MockHttpServletRequest("GET", "/");
    request.setSession(session);
    response = new MockHttpServletResponse();
  }

  private void loggedInAs(final int id, final String name) {
    session.setAttribute(LOGIN_ATTRID, id);
    session.setAttribute(LOGIN_ATTRNAME, name);
  }

  @Test
  void sessionOfADisabledAccount_isLoggedOut() throws Exception {
    loggedInAs(7, "mallory");

    assertTrue(interceptor.preHandle(request, response, new Object()));

    assertNull(session.getAttribute(LOGIN_ATTRID));
    assertNull(session.getAttribute(LOGIN_ATTRNAME));
    assertFalse(Login.isAuthenticated(session));
    assertFalse(session.isInvalid());
  }

  @Test
  void sessionOfAnotherAccount_isLeftAlone() throws Exception {
    loggedInAs(5, "ada");

    assertTrue(interceptor.preHandle(request, response, new Object()));

    assertEquals(5, session.getAttribute(LOGIN_ATTRID));
    assertEquals("ada", session.getAttribute(LOGIN_ATTRNAME));
    assertEquals(200, response.getStatus());
  }

  @Test
  void pendingSecondFactorOfADisabledAccount_isDropped() throws Exception {
    Login.setPendingSecondFactor(session, 7, "mallory", TwoFactorMethod.TOTP);

    assertTrue(interceptor.preHandle(request, response, new Object()));

    assertFalse(Login.isPendingSecondFactor(session));
  }

  @Test
  void pendingSecondFactorOfAnotherAccount_isKept() throws Exception {
    Login.setPendingSecondFactor(session, 5, "ada", TwoFactorMethod.TOTP);

    assertTrue(interceptor.preHandle(request, response, new Object()));

    assertTrue(Login.isPendingSecondFactor(session));
  }

  @Test
  void noSession_isNotCreated() throws Exception {
    final MockHttpServletRequest anonymous = new MockHttpServletRequest("GET", "/users/ada");

    assertTrue(interceptor.preHandle(anonymous, response, new Object()));

    assertNull(anonymous.getSession(false));
  }

  @Test
  void addressNamingADisabledAccount_isNotFound() throws Exception {
    request.setRequestURI("/users/Mallory/rss");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("username", "Mallory"));
    when(disabledAccounts.isDisabled("Mallory")).thenReturn(true);

    assertFalse(interceptor.preHandle(request, response, new Object()));

    assertEquals(404, response.getStatus());
  }

  @Test
  void addressNamingAnotherAccount_carriesOn() throws Exception {
    request.setRequestURI("/users/ada/rss");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("username", "ada"));

    assertTrue(interceptor.preHandle(request, response, new Object()));

    assertEquals(200, response.getStatus());
  }

  @Test
  void disabledVisitor_canStillReadOtherJournals() throws Exception {
    loggedInAs(7, "mallory");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("username", "ada"));

    assertTrue(interceptor.preHandle(request, response, new Object()));

    assertFalse(Login.isAuthenticated(session));
  }
}
