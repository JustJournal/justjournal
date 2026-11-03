package com.justjournal.ctl.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Hands every admin page the session's form token, so each form can post it back as {@code
 * _csrf}.
 *
 * @author Lucas Holt
 */
@ControllerAdvice(basePackageClasses = AdminControllerAdvice.class)
public class AdminControllerAdvice {

  @ModelAttribute(AdminSession.CSRF_MODEL)
  public String csrfToken(final HttpSession session) {
    return AdminSession.csrfToken(session);
  }

  @ModelAttribute("adminLoggedIn")
  public boolean adminLoggedIn(final HttpSession session) {
    return AdminSession.isAdmin(session);
  }
}
