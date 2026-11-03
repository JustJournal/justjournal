package com.justjournal.ctl.admin;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.User;
import com.justjournal.services.AdminAccountService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Accounts in the admin area: find an account by username and disable or enable it. {@link
 * AdminInterceptor} has already checked the admin login and the form token.
 *
 * @author Lucas Holt
 */
@Slf4j
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

  static final String VIEW = "admin/layout";

  static final String REDIRECT = "redirect:/admin/users";

  private final AdminAccountService adminAccountService;

  public AdminUserController(final AdminAccountService adminAccountService) {
    this.adminAccountService = adminAccountService;
  }

  @GetMapping
  public String list(
      @RequestParam(value = "q", required = false) final String query,
      @RequestParam(value = "page", defaultValue = "0") final int page,
      final Model model) {
    final String text = query == null ? "" : query.trim();
    final Page<User> users = adminAccountService.search(text, page);
    model.addAttribute("title", "Accounts");
    model.addAttribute("adminSection", "users");
    model.addAttribute("contentTemplate", "admin/users");
    model.addAttribute("q", text);
    model.addAttribute("users", users);
    return VIEW;
  }

  @PostMapping("/{id}/disable")
  public String disable(
      @PathVariable("id") final int id,
      @RequestParam(value = "q", required = false) final String query,
      final HttpSession session,
      final RedirectAttributes redirect) {
    try {
      final User user = adminAccountService.disable(id, admin(session));
      redirect.addFlashAttribute(
          "message",
          "Disabled " + user.getUsername() + ". It can't log in and its journal is hidden.");
    } catch (final NotFoundException e) {
      redirect.addFlashAttribute("error", "That account doesn't exist.");
    }
    return back(query, redirect);
  }

  @PostMapping("/{id}/enable")
  public String enable(
      @PathVariable("id") final int id,
      @RequestParam(value = "q", required = false) final String query,
      final HttpSession session,
      final RedirectAttributes redirect) {
    try {
      final User user = adminAccountService.enable(id, admin(session));
      redirect.addFlashAttribute("message", "Enabled " + user.getUsername() + ".");
    } catch (final NotFoundException e) {
      redirect.addFlashAttribute("error", "That account doesn't exist.");
    }
    return back(query, redirect);
  }

  /** Back to the list, keeping the search. */
  private static String back(final String query, final RedirectAttributes redirect) {
    if (query != null && !query.isBlank()) redirect.addAttribute("q", query.trim());
    return REDIRECT;
  }

  private static String admin(final HttpSession session) {
    return session.getAttribute(AdminSession.ADMIN_ATTR) instanceof String name ? name : "?";
  }
}
