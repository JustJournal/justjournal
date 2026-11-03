package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static com.justjournal.core.Constants.PARAM_USERNAME;

import com.justjournal.Login;
import com.justjournal.services.DisabledAccounts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Keeps disabled accounts out. A session logged in (or half way through a second factor) as a
 * disabled account is logged out, and the request carries on as a visitor's. Any address naming a
 * disabled account in its {@code {username}} part, which covers the journal pages, feeds, the
 * fediverse actor and its inbox, and the per user APIs, answers 404.
 *
 * @author Lucas Holt
 */
@Slf4j
@Component
public class DisabledAccountInterceptor implements HandlerInterceptor {

  private final DisabledAccounts disabledAccounts;

  public DisabledAccountInterceptor(final DisabledAccounts disabledAccounts) {
    this.disabledAccounts = disabledAccounts;
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler)
      throws IOException {
    final HttpSession session = request.getSession(false);
    if (session != null) logOutIfDisabled(session);

    if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
            instanceof Map<?, ?> variables
        && variables.get(PARAM_USERNAME) instanceof String username
        && disabledAccounts.isDisabled(username)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return false;
    }
    return true;
  }

  private void logOutIfDisabled(final HttpSession session) {
    if (session.getAttribute(LOGIN_ATTRID) instanceof Integer userId
        && disabledAccounts.isDisabled(userId)) {
      log.info(
          "Logged out a session of disabled account {} ({})",
          session.getAttribute(LOGIN_ATTRNAME),
          userId);
      session.removeAttribute(LOGIN_ATTRID);
      session.removeAttribute(LOGIN_ATTRNAME);
    }

    final int pendingId = Login.pendingLoginId(session);
    if (disabledAccounts.isDisabled(pendingId)) {
      log.info("Dropped a pending second factor login of disabled account {}", pendingId);
      Login.clearPendingSecondFactor(session);
    }
  }
}
