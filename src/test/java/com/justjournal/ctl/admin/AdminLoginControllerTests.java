package com.justjournal.ctl.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.services.AdminLoginService;
import com.justjournal.services.AdminLoginService.SendResult;
import com.justjournal.services.AdminLoginService.VerifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminLoginControllerTests {

  @Mock private AdminLoginService adminLoginService;

  @InjectMocks private AdminLoginController controller;

  private MockHttpServletRequest request;

  private MockHttpSession session;

  private Model model;

  private RedirectAttributesModelMap redirectAttributes;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    request = new MockHttpServletRequest("POST", "/admin/login");
    request.setSession(session);
    request.setRemoteAddr("192.0.2.4");
    model = new ExtendedModelMap();
    redirectAttributes = new RedirectAttributesModelMap();

    when(adminLoginService.checkCredentials("admin", "secret")).thenReturn(true);
    when(adminLoginService.isLocked()).thenReturn(false);
    when(adminLoginService.sendCode(anyString())).thenReturn(SendResult.SENT);
  }

  private String login(final String username, final String password) {
    return controller.login(username, password, request, session, model);
  }

  private String code(final String code) {
    return controller.code(code, null, request, session, model, redirectAttributes);
  }

  private void pending() {
    AdminLoginController.setPending(session, "admin");
  }

  private boolean isPending() {
    return AdminLoginController.pendingAdmin(session) != null;
  }

  /* ---- password ---- */

  @Test
  void loginFormShowsTheLoginPage() {
    assertEquals("admin/layout", controller.loginForm(session, model));
    assertEquals("admin/login", model.getAttribute("contentTemplate"));
    assertNull(model.getAttribute("error"));
  }

  @Test
  void loginFormSendsAnAdminOnIn() {
    session.setAttribute(AdminSession.ADMIN_ATTR, "admin");

    assertEquals("redirect:/admin", controller.loginForm(session, model));
  }

  @Test
  void wrongPasswordShowsOneGenericError() {
    assertEquals("admin/layout", login("admin", "wrong"));
    assertEquals(AdminLoginController.BAD_LOGIN, model.getAttribute("error"));
    assertEquals("admin/login", model.getAttribute("contentTemplate"));
    assertFalse(isPending());
    verify(adminLoginService, never()).sendCode(any());
  }

  @Test
  void wrongUsernameShowsTheSameError() {
    login("nobody", "secret");

    assertEquals(AdminLoginController.BAD_LOGIN, model.getAttribute("error"));
    assertFalse(isPending());
    verify(adminLoginService, never()).sendCode(any());
  }

  @Test
  void rightPasswordMailsACodeAndAsksForIt() {
    assertEquals("redirect:/admin/code", login("admin", "secret"));

    verify(adminLoginService).sendCode("192.0.2.4");
    assertEquals("admin", AdminLoginController.pendingAdmin(session));
    assertNull(session.getAttribute(AdminSession.ADMIN_ATTR));
  }

  @Test
  void rightPasswordJustAfterAnotherLoginUsesTheCodeAlreadyMailed() {
    when(adminLoginService.sendCode(anyString())).thenReturn(SendResult.THROTTLED);

    assertEquals("redirect:/admin/code?recent", login("admin", "secret"));
    assertTrue(isPending());
  }

  @Test
  void noSiteAdminAddressRefusesTheLogin() {
    when(adminLoginService.sendCode(anyString())).thenReturn(SendResult.NO_ADDRESS);

    assertEquals("admin/layout", login("admin", "secret"));
    assertEquals(AdminLoginController.NO_ADDRESS, model.getAttribute("error"));
    assertFalse(isPending());
  }

  @Test
  void codeThatCannotBeMailedRefusesTheLogin() {
    when(adminLoginService.sendCode(anyString())).thenReturn(SendResult.FAILED);

    assertEquals("admin/layout", login("admin", "secret"));
    assertEquals(AdminLoginController.NOT_MAILED, model.getAttribute("error"));
    assertFalse(isPending());
  }

  @Test
  void lockedLoginMailsNothing() {
    when(adminLoginService.isLocked()).thenReturn(true);

    assertEquals("admin/layout", login("admin", "secret"));
    assertEquals(AdminLoginController.TOO_MANY, model.getAttribute("error"));
    assertFalse(isPending());
    verify(adminLoginService, never()).sendCode(any());
  }

  @Test
  void failedLoginDropsAnEarlierPendingOne() {
    pending();

    login("admin", "wrong");

    assertFalse(isPending());
  }

  /* ---- code ---- */

  @Test
  void codeFormWithoutAPendingLoginGoesBackToLogin() {
    assertEquals(
        "redirect:/admin/login", controller.codeForm(null, session, model, redirectAttributes));
    assertTrue(redirectAttributes.getFlashAttributes().isEmpty());
  }

  @Test
  void codeFormAfterThePendingLoginExpiredGoesBackToLogin() {
    pending();
    session.setAttribute(
        AdminLoginController.PENDING_SINCE_ATTR,
        System.currentTimeMillis() - AdminLoginService.CODE_TTL.toMillis() - 1000L);

    assertEquals(
        "redirect:/admin/login", controller.codeForm(null, session, model, redirectAttributes));
    assertEquals(
        AdminLoginController.EXPIRED, redirectAttributes.getFlashAttributes().get("error"));
    assertNull(session.getAttribute(AdminLoginController.PENDING_ATTR));
  }

  @Test
  void codeFormShowsTheCodePage() {
    pending();

    assertEquals("admin/layout", controller.codeForm(null, session, model, redirectAttributes));
    assertEquals("admin/code", model.getAttribute("contentTemplate"));
    assertEquals(AdminLoginController.CODE_SENT, model.getAttribute("message"));

    controller.codeForm("", session, model, redirectAttributes);
    assertEquals(AdminLoginController.CODE_RECENT, model.getAttribute("message"));
  }

  @Test
  void codeWithoutAPendingLoginIsNotChecked() {
    assertEquals("redirect:/admin/login", code("123456"));

    verify(adminLoginService, never()).verifyCode(any());
    assertNull(session.getAttribute(AdminSession.ADMIN_ATTR));
  }

  @Test
  void codeAfterThePendingLoginExpiredIsNotChecked() {
    pending();
    session.setAttribute(
        AdminLoginController.PENDING_SINCE_ATTR,
        System.currentTimeMillis() - AdminLoginService.CODE_TTL.toMillis() - 1000L);

    assertEquals("redirect:/admin/login", code("123456"));

    verify(adminLoginService, never()).verifyCode(any());
    assertNull(session.getAttribute(AdminSession.ADMIN_ATTR));
    assertNull(session.getAttribute(AdminLoginController.PENDING_ATTR));
  }

  @Test
  void rightCodeFinishesTheLoginOnANewSessionId() {
    pending();
    final String before = session.getId();
    when(adminLoginService.verifyCode("123456")).thenReturn(VerifyResult.OK);

    assertEquals("redirect:/admin", code("123456"));

    assertNotEquals(before, session.getId());
    assertEquals("admin", session.getAttribute(AdminSession.ADMIN_ATTR));
    assertTrue(AdminSession.isAdmin(session));
    assertFalse(isPending());
    assertNull(session.getAttribute(AdminLoginController.PENDING_SINCE_ATTR));
  }

  @Test
  void wrongCodesCountUntilTheLoginIsDropped() {
    pending();
    when(adminLoginService.verifyCode("000000"))
        .thenReturn(
            VerifyResult.INVALID,
            VerifyResult.INVALID,
            VerifyResult.INVALID,
            VerifyResult.INVALID,
            VerifyResult.LOCKED);

    for (int i = 0; i < 4; i++) {
      assertEquals("admin/layout", code("000000"));
      assertEquals(AdminLoginController.BAD_CODE, model.getAttribute("error"));
      assertTrue(isPending());
    }

    assertEquals("redirect:/admin/login", code("000000"));
    assertEquals(
        AdminLoginController.TOO_MANY, redirectAttributes.getFlashAttributes().get("error"));
    assertFalse(isPending());
    assertNull(session.getAttribute(AdminSession.ADMIN_ATTR));
  }

  @Test
  void resendMailsANewCode() {
    pending();

    assertEquals(
        "admin/layout", controller.code(null, "1", request, session, model, redirectAttributes));

    verify(adminLoginService).sendCode("192.0.2.4");
    verify(adminLoginService, never()).verifyCode(any());
    assertTrue(isPending());
    assertNull(model.getAttribute("error"));
  }

  @Test
  void resendTooSoonSaysSo() {
    pending();
    when(adminLoginService.sendCode(anyString())).thenReturn(SendResult.THROTTLED);

    controller.code(null, "1", request, session, model, redirectAttributes);

    assertEquals(AdminLoginController.CODE_RECENT, model.getAttribute("error"));
    assertTrue(isPending());
  }

  /* ---- logout and home ---- */

  @Test
  void logoutEndsOnlyTheAdminLogin() {
    session.setAttribute(AdminSession.ADMIN_ATTR, "admin");
    session.setAttribute("auth.user", "member");
    pending();

    assertEquals("redirect:/admin/login", controller.logout(session));

    assertNull(session.getAttribute(AdminSession.ADMIN_ATTR));
    assertFalse(isPending());
    assertEquals("member", session.getAttribute("auth.user"));
    assertFalse(session.isInvalid());
  }

  @Test
  void adminHomeIsThePictureQueue() {
    assertEquals("redirect:/admin/images", controller.home());
  }
}
