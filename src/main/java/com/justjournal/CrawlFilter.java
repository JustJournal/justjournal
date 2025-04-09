/*
 * Copyright (c) 2003-2021 Lucas Holt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.justjournal;


import org.htmlunit.BrowserVersion;
import org.htmlunit.NicelyResynchronizingAjaxController;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.errors.EncodingException;

@Slf4j
public final class CrawlFilter implements Filter {

  private static final String PARAM_ESCAPED_FRAGMENT = "_escaped_fragment_";

  private FilterConfig filterConfig = null;

  @NotNull
  private static String rewriteQueryString(String queryString) throws EncodingException {
    StringBuilder queryStringSb = new StringBuilder(queryString);
    int i = queryStringSb.indexOf("&_escaped_fragment_");
    if (i != -1) {
      final StringBuilder tmpSb = new StringBuilder(queryStringSb.substring(0, i));
      tmpSb.append("#!");
      tmpSb.append(
          ESAPI.encoder().decodeFromURL(queryStringSb.substring(i + 20, queryStringSb.length())));
      queryStringSb = tmpSb;
    }

    i = queryStringSb.indexOf(PARAM_ESCAPED_FRAGMENT);
    if (i != -1) {
      final StringBuilder tmpSb = new StringBuilder(queryStringSb.substring(0, i));
      tmpSb.append("#!");
      tmpSb.append(
          ESAPI.encoder().decodeFromURL(queryStringSb.substring(i + 19, queryStringSb.length())));
      queryStringSb = tmpSb;
    }
    if (queryStringSb.indexOf("#!") != 0) {
      queryStringSb.insert(0, '?');
    }
    queryString = queryStringSb.toString();

    return queryString;
  }

  /** Initializes the filter configuration */
  @Override
  public void init(FilterConfig filterConfig) {
    this.filterConfig = filterConfig;
  }

  /** Filters all requests and invokes headless browser if necessary */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException {
    if (filterConfig == null) {
      return;
    }

    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;
    String queryString = req.getQueryString();

    if ((queryString != null) && (queryString.contains(PARAM_ESCAPED_FRAGMENT))) {
      StringBuilder pageNameSb = new StringBuilder(req.getScheme() + "://");
      pageNameSb.append(req.getServerName());
      if (req.getServerPort() != 0) {
        pageNameSb.append(":");
        pageNameSb.append(req.getServerPort());
      }
      pageNameSb.append(req.getRequestURI());
      try {
        queryString = rewriteQueryString(queryString);
      } catch (final EncodingException e) {
        log.error("Could not generate url", e);
        return;
      }
      pageNameSb.append(queryString);

      try (WebClient webClient = new WebClient(BrowserVersion.CHROME);
          PrintWriter out = res.getWriter()) {
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        webClient.getOptions().setJavaScriptEnabled(true);
        String pageName = pageNameSb.toString();
        HtmlPage page = webClient.getPage(pageName);
        webClient.waitForBackgroundJavaScriptStartingBefore(2000);
        webClient.waitForBackgroundJavaScript(8000);

        res.setContentType("text/html;charset=UTF-8");

        out.println("<hr>");
        out.println(
            "<center><h3>You are viewing a non-interactive page that is intended for"
                + " the crawler.  You probably want to see this page: <a href=\""
                + ESAPI.encoder().encodeForHTMLAttribute(pageName)
                + "\">"
                + ESAPI.encoder().encodeForHTML(pageName)
                + "</a></h3></center>");
        out.println("<hr>");

        out.println(page.asXml());
      }
    } else {
      try {
        chain.doFilter(request, response);
      } catch (ServletException e) {
        log.error(e.getMessage());
      }
    }
  }

  public void doFilterOld(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (filterConfig == null) {
      return;
    }

    ServletOutputStream out = response.getOutputStream();
    String escapedFragment = request.getParameter(PARAM_ESCAPED_FRAGMENT);

    if (escapedFragment != null) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      /*
       * Rewrite the URL back to the original #! version.
       */
      String urlWithHashFragment =
          httpRequest.getScheme()
              + "://"
              + httpRequest.getServerName()
              + ":"
              + httpRequest.getServerPort()
              + httpRequest.getContextPath()
              + httpRequest.getServletPath();

      String pathInfo = httpRequest.getPathInfo();
      if (pathInfo != null) {
        urlWithHashFragment += pathInfo;
      }

      String queryString = httpRequest.getQueryString();
      Pattern pattern = Pattern.compile("(.*&)?_escaped_fragment_=[^&]*(&(.*))?");
      Matcher matcher = pattern.matcher(queryString);

      if (matcher.matches()) {
        urlWithHashFragment += "?" + matcher.group(1) + matcher.group(3);
      }

      /*
       * TODO Unescape %XX characters.
       */
      urlWithHashFragment += "#!" + escapedFragment;

      /*
       * Use the headless browser (HtmlUnit) to obtain an HTML snapshot.
       */
      try (WebClient webClient = new WebClient()) {
        final HtmlPage page = webClient.getPage(urlWithHashFragment);

        /*
         * Give the headless browser enough time to execute JavaScript. The
         * exact time to wait may depend on your application.
         */
        webClient.waitForBackgroundJavaScript(2000);

        /*
         * Return the snapshot.
         */
        out.println(page.asXml());
      }
    } else {
      try {
        /*
         * Not an _escaped_fragment_ URL, so move up the chain of
         * servlet filters.
         */
        chain.doFilter(request, response);
      } catch (final ServletException e) {
        log.error(e.getMessage());
      }
    }
  }

  /** Destroys the filter configuration */
  @Override
  public void destroy() {
    this.filterConfig = null;
  }
}
