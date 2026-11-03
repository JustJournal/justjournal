package com.justjournal.ctl.admin;

import com.justjournal.config.AdminProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards every {@code /admin} address: 404 while no admin login is configured, a redirect to the
 * admin login for sessions that haven't passed both factors, and 403 for any form post without the
 * session's token. Admin pages are never cached or framed.
 *
 * @author Lucas Holt
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {

  public static final String LOGIN_PATH = "/admin/login";

  public static final String CODE_PATH = "/admin/code";

  public static final String LOGOUT_PATH = "/admin/logout";

  /** Reachable before the admin has logged in. */
  static final Set<String> OPEN_PATHS = Set.of(LOGIN_PATH, CODE_PATH, LOGOUT_PATH);

  private final AdminProperties adminProperties;

  public AdminInterceptor(final AdminProperties adminProperties) {
    this.adminProperties = adminProperties;
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler)
      throws IOException {
    if (!adminProperties.isEnabled()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return false;
    }

    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");

    final HttpSession session = request.getSession(false);
    if ("POST".equalsIgnoreCase(request.getMethod())
        && !AdminSession.isValidCsrf(session, request.getParameter(AdminSession.CSRF_PARAM))) {
      log.warn("Admin form post to {} refused: missing or wrong token", request.getRequestURI());
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return false;
    }

    if (OPEN_PATHS.contains(path(request)) || AdminSession.isAdmin(session)) return true;

    response.sendRedirect(request.getContextPath() + LOGIN_PATH);
    return false;
  }

  private static String path(final HttpServletRequest request) {
    final String uri = request.getRequestURI().substring(request.getContextPath().length());
    return uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }
}
