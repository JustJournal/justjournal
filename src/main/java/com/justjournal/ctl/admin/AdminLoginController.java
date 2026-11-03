package com.justjournal.ctl.admin;

import com.justjournal.services.AdminLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The admin login: username and password, then a code mailed to the site admin address. Only once
 * both pass is {@link AdminSession#ADMIN_ATTR} set, after the session id is changed.
 *
 * @author Lucas Holt
 */
@Slf4j
@Controller
@RequestMapping("/admin")
public class AdminLoginController {

  /** Admin username that passed the password check and is waiting on the mailed code. */
  static final String PENDING_ATTR = "admin.pending";

  /** When the password check passed, in epoch milliseconds. */
  static final String PENDING_SINCE_ATTR = "admin.pending.since";

  static final String BAD_LOGIN = "Wrong username or password.";

  static final String BAD_CODE = "That code is not right. Check the latest e-mail and try again.";

  static final String TOO_MANY =
      "Too many wrong codes. Please wait a few minutes and log in again.";

  static final String EXPIRED = "Your login timed out. Please log in again.";

  static final String NO_ADDRESS =
      "The admin login code can't be e-mailed because no site admin e-mail address is set.";

  static final String NOT_MAILED =
      "The admin login code could not be e-mailed. Please try again later.";

  static final String CODE_SENT = "A login code was e-mailed to the site admin address.";

  static final String CODE_RECENT =
      "A login code was e-mailed less than a minute ago. Enter that one, or wait a minute and ask"
          + " for a new one.";

  private final AdminLoginService adminLoginService;

  public AdminLoginController(final AdminLoginService adminLoginService) {
    this.adminLoginService = adminLoginService;
  }

  @GetMapping({"", "/"})
  public String home() {
    return "redirect:/admin/images";
  }

  @GetMapping("/login")
  public String loginForm(final HttpSession session, final Model model) {
    if (AdminSession.isAdmin(session)) return "redirect:/admin";
    return loginPage(model, null);
  }

  @PostMapping("/login")
  public String login(
      @RequestParam(value = "username", required = false) final String username,
      @RequestParam(value = "password", required = false) final String password,
      final HttpServletRequest request,
      final HttpSession session,
      final Model model) {
    clearPending(session);

    if (!adminLoginService.checkCredentials(username, password)) {
      log.warn("Admin login refused from {}: wrong username or password", request.getRemoteAddr());
      return loginPage(model, BAD_LOGIN);
    }
    if (adminLoginService.isLocked()) {
      log.warn("Admin login from {} refused: too many wrong codes", request.getRemoteAddr());
      return loginPage(model, TOO_MANY);
    }

    return switch (adminLoginService.sendCode(request.getRemoteAddr())) {
      case SENT -> {
        setPending(session, adminName(username));
        yield "redirect:/admin/code";
      }
      case THROTTLED -> {
        // the code mailed a moment ago is still good
        setPending(session, adminName(username));
        yield "redirect:/admin/code?recent";
      }
      case NO_ADDRESS -> loginPage(model, NO_ADDRESS);
      case FAILED -> loginPage(model, NOT_MAILED);
    };
  }

  @GetMapping("/code")
  public String codeForm(
      @RequestParam(value = "recent", required = false) final String recent,
      final HttpSession session,
      final Model model,
      final RedirectAttributes redirectAttributes) {
    if (pendingAdmin(session) == null) return startOver(session, redirectAttributes);
    return codePage(model, recent == null ? CODE_SENT : CODE_RECENT, null);
  }

  @PostMapping("/code")
  public String code(
      @RequestParam(value = "code", required = false) final String code,
      @RequestParam(value = "resend", required = false) final String resend,
      final HttpServletRequest request,
      final HttpSession session,
      final Model model,
      final RedirectAttributes redirectAttributes) {
    final String admin = pendingAdmin(session);
    if (admin == null) return startOver(session, redirectAttributes);

    if (resend != null) {
      return switch (adminLoginService.sendCode(request.getRemoteAddr())) {
        case SENT -> {
          // the new code is good for the full time, so the login is too
          setPending(session, admin);
          yield codePage(model, "A new login code was e-mailed.", null);
        }
        case THROTTLED -> codePage(model, null, CODE_RECENT);
        case NO_ADDRESS -> codePage(model, null, NO_ADDRESS);
        case FAILED -> codePage(model, null, NOT_MAILED);
      };
    }

    return switch (adminLoginService.verifyCode(code)) {
      case OK -> {
        request.changeSessionId();
        clearPending(session);
        session.setAttribute(AdminSession.ADMIN_ATTR, admin);
        log.info("Admin {} logged in from {}", admin, request.getRemoteAddr());
        yield "redirect:/admin";
      }
      case INVALID -> codePage(model, null, BAD_CODE);
      case LOCKED -> {
        log.warn("Admin login from {} dropped: too many wrong codes", request.getRemoteAddr());
        clearPending(session);
        redirectAttributes.addFlashAttribute("error", TOO_MANY);
        yield "redirect:/admin/login";
      }
    };
  }

  /** Ends the admin login only; a member login on the same session is left alone. */
  @PostMapping("/logout")
  public String logout(final HttpSession session) {
    session.removeAttribute(AdminSession.ADMIN_ATTR);
    clearPending(session);
    return "redirect:/admin/login";
  }

  /* ---- session state ---- */

  static void setPending(final HttpSession session, final String username) {
    session.setAttribute(PENDING_ATTR, username);
    session.setAttribute(PENDING_SINCE_ATTR, System.currentTimeMillis());
  }

  static void clearPending(final HttpSession session) {
    session.removeAttribute(PENDING_ATTR);
    session.removeAttribute(PENDING_SINCE_ATTR);
  }

  /** @return the admin waiting on the mailed code, or null if none or it timed out */
  static String pendingAdmin(final HttpSession session) {
    if (session.getAttribute(PENDING_ATTR) instanceof String name
        && !name.isEmpty()
        && session.getAttribute(PENDING_SINCE_ATTR) instanceof Long since
        && System.currentTimeMillis() - since <= AdminLoginService.CODE_TTL.toMillis()) {
      return name;
    }
    return null;
  }

  /* ---- pages ---- */

  private String startOver(final HttpSession session, final RedirectAttributes redirectAttributes) {
    final boolean hadPending = session.getAttribute(PENDING_ATTR) != null;
    clearPending(session);
    if (hadPending) redirectAttributes.addFlashAttribute("error", EXPIRED);
    return "redirect:/admin/login";
  }

  private static String adminName(final String username) {
    return username.trim();
  }

  private static String loginPage(final Model model, final String error) {
    model.addAttribute("title", "Log in");
    model.addAttribute("adminSection", "login");
    model.addAttribute("contentTemplate", "admin/login");
    if (error != null) model.addAttribute("error", error);
    return "admin/layout";
  }

  private static String codePage(final Model model, final String message, final String error) {
    model.addAttribute("title", "Login code");
    model.addAttribute("adminSection", "login");
    model.addAttribute("contentTemplate", "admin/code");
    if (message != null) model.addAttribute("message", message);
    if (error != null) model.addAttribute("error", error);
    return "admin/layout";
  }
}
