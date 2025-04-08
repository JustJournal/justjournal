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

import com.justjournal.core.Settings;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An implementation of Really Simple Discovery (RSD).
 *
 * <p>A list of points that blogging clients can use to post-entries by "autodiscovery". This is
 * used by blog clients such as Microsoft's Live Writer.
 *
 * TODO: remove Rsd entirely if we don't want xml-rpc?
 *
 * <p>http://cyber.law.harvard.edu/blogs/gems/tech/rsd.html
 *<p>TODO: add atompub?</p>
 * @author Lucas Holt
 */
@Slf4j
@RestController
@RequestMapping("/rsd")
public class RsdController {

  private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
  private static final String RSD_HEADER =
      "<rsd xmlns=\"http://archipelago.phrasewise.com/rsd\" version=\"1.0\">\n";
  private static final String RSD_FOOTER = "</rsd>\n";

  private final UserRepository userRepository;
  private final Settings settings;

  public RsdController(UserRepository userRepository, Settings settings) {
    this.userRepository = userRepository;
    this.settings = settings;
  }

  @GetMapping(produces = "application/rsd+xml")
  public String get(HttpServletRequest request, HttpServletResponse response) {
    final StringBuilder sb = new StringBuilder();
    try {
      response.setContentType("application/rsd+xml; charset=utf-8");

      final String blogID = request.getParameter("blogID");
      if (blogID == null || blogID.length() < 2) {
        throw new IllegalArgumentException("Missing required parameter \"blogID\"");
      }

      final User user = userRepository.findByUsername(blogID);

      sb.append(XML_HEADER);
      sb.append(RSD_HEADER);
      sb.append("<service>\n");
      sb.append("\t<engineName>JustJournal</engineName>\n");
      sb.append("\t<engineLink>https://www.justjournal.com</engineLink>\n");
      sb.append("\t<homePageLink>").append(settings.getBlogBaseUrl(user.getUsername()))
              .append("</homePageLink>\n");
      // APIS we support.
      sb.append("\t<apis>\n");
      sb.append(
          "\t\t<api name=\"Blogger\" preferred=\"false\""
              + " apiLink=\"" + settings.getBaseUri() + "xml-rpc\" blogID=\"");
      sb.append(user.getUsername());
      sb.append("\" />\n");
      sb.append(
          "\t\t<api name=\"MetaWeblog\" preferred=\"true\""
              + " apiLink=\"" + settings.getBaseUri() + "xml-rpc\" blogID=\"");
      sb.append(user.getUsername());
      sb.append("\" />\n");
      sb.append("\t</apis>\n");
      sb.append("</service>\n");
      sb.append(RSD_FOOTER);

    } catch (final Exception e) {
      log.error("Error generating RSD", e);
      sb.delete(0, sb.length() - 1);
      sb.append(XML_HEADER);
      sb.append(RSD_HEADER);
      sb.append(RSD_FOOTER);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    return sb.toString();
  }
}
