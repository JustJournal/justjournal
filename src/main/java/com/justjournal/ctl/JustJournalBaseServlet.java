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
package com.justjournal.ctl;


import com.justjournal.core.Constants;
import com.justjournal.core.Settings;
import com.justjournal.utility.ETag;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base servlet to do some of the repetative servlet initialization stuff.
 *
 * <p>Date: Sep 25, 2005 Time: 9:04:00 PM
 *
 * @author Lucas Holt
 * @version $Id: JustJournalBaseServlet.java,v 1.17 2009/07/11 02:03:43 laffer1 Exp $
 * @since 1.0
 */
public abstract class JustJournalBaseServlet extends HttpServlet {

  @Autowired protected transient Settings set;

  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws java.io.IOException {
    processRequest(request, response, false);
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws java.io.IOException {
    processRequest(request, response, false);
  }

  @Override
  protected void doHead(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    processRequest(httpServletRequest, httpServletResponse, true);
  }

  protected void processRequest(
      HttpServletRequest request, HttpServletResponse response, boolean head)
      throws java.io.IOException {
    final String contentType = "text/html; charset=utf-8";
    final StringBuilder sb = new StringBuilder(512);
    final HttpSession session = request.getSession(true);

    response.setContentType(contentType);
    response.setBufferSize(Constants.DEFAULT_BUFFER_SIZE);
    response.setDateHeader("Expires", System.currentTimeMillis());
    response.setDateHeader("Last-Modified", System.currentTimeMillis());
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
    response.setHeader("Pragma", "no-cache");

    execute(request, response, session, sb);

    /* create etag */
    ETag etag = new ETag(response);
    etag.writeFromString(sb.toString());

    response.setContentLength(sb.length());

    if (head) {
      response.flushBuffer();
    } else {
      final ServletOutputStream outstream = response.getOutputStream();
      outstream.print(sb.toString());
      outstream.flush();
      outstream.close();
    }
  }

  @Override
  public long getLastModified(HttpServletRequest request) {
    return new java.util.Date().getTime() / 1000 * 1000;
  }

  protected abstract void execute(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpSession session,
      StringBuilder sb);

  /**
   * Get a string input parameter guaranteed not to be null
   *
   * @param request Servlet Request
   * @param input Name of the parameter
   * @return Trimmed, Not null string from parameter
   */
  protected String fixInput(ServletRequest request, String input) {
    String fixed = request.getParameter(input);

    if (fixed == null) fixed = "";

    return fixed.trim();
  }

  /**
   * Get a string header guaranteed not to be null
   *
   * @param request Servlet Request
   * @param input Name of the header
   * @return Trimmed, Not null string from header
   */
  protected String fixHeaderInput(HttpServletRequest request, String input) {
    String fixed = request.getHeader(input);

    if (fixed == null) fixed = "";
    return fixed.trim();
  }
}
