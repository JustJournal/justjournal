package com.justjournal.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Permanently redirects addresses carrying a session id ({@code /;jsessionid=...}), left over from
 * when sessions were tracked in the URL, to the same address without it. Crawlers still request
 * them, and some, like the home page, don't match any page with the id attached.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SessionIdRedirectFilter extends OncePerRequestFilter {

  /** {@code ;jsessionid=...} up to the next path segment, also when the {@code ;} is encoded. */
  static final Pattern SESSION_ID =
      Pattern.compile("(?:;|%3B)jsessionid(?:=|%3D)[^/;?]*", Pattern.CASE_INSENSITIVE);

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain chain)
      throws ServletException, IOException {
    final String uri = request.getRequestURI();
    final String method = request.getMethod();
    if (uri == null
        || !("GET".equals(method) || "HEAD".equals(method))
        || !SESSION_ID.matcher(uri).find()) {
      chain.doFilter(request, response);
      return;
    }

    // collapse leading slashes, so //example.com;jsessionid=x can't become an offsite redirect
    String target = SESSION_ID.matcher(uri).replaceAll("").replaceFirst("^/+", "/");
    if (!target.startsWith("/")) target = "/" + target;
    if (request.getQueryString() != null) target += "?" + request.getQueryString();

    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    response.setHeader("Location", target);
  }
}
