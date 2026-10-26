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
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * An implementation of Really Simple Discovery (RSD).
 *
 * <p>A list of points that blogging clients can use to post-entries by "autodiscovery". This is
 * used by blog clients such as Microsoft's Live Writer.
 *
 *
 * <p>http://cyber.law.harvard.edu/blogs/gems/tech/rsd.html
 *<p>TODO: add atompub?</p>
 * @author Lucas Holt
 */
@RestController
@RequestMapping("/rsd")
public class RsdController {

  private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
  private static final String RSD_HEADER =
      "<rsd xmlns=\"http://archipelago.phrasewise.com/rsd\" version=\"1.0\">\n";
  private static final String RSD_FOOTER = "</rsd>\n";
  private static final MediaType RSD_XML =
      new MediaType("application", "rsd+xml", StandardCharsets.UTF_8);

  private final UserRepository userRepository;
  private final Settings settings;

  public RsdController(UserRepository userRepository, Settings settings) {
    this.userRepository = userRepository;
    this.settings = settings;
  }

  /**
   * RSD for a blog, or for the site when no (or a short) blogID is given. No {@code produces}
   * here: clients and browsers following the EditURI link send all sorts of Accept headers, and
   * a mismatch would surface as an error rather than the document they asked for.
   */
  @GetMapping
  public ResponseEntity<String> get(@RequestParam(value = "blogID", required = false) String blogID) {
    String homePageLink = settings.getBaseUri();
    String username = "";

    if (blogID != null && blogID.length() > 1) {
      final User user = userRepository.findByUsername(blogID);
      if (user == null) {
        return rsd(HttpStatus.NOT_FOUND, XML_HEADER + RSD_HEADER + RSD_FOOTER);
      }
      username = HtmlUtils.htmlEscape(user.getUsername());
      homePageLink = settings.getBlogBaseUrl(username);
    }

    final String apiLink = settings.getBaseUri() + "xml-rpc";
    final StringBuilder sb = new StringBuilder();
    sb.append(XML_HEADER);
    sb.append(RSD_HEADER);
    sb.append("<service>\n");
    sb.append("\t<engineName>JustJournal</engineName>\n");
    sb.append("\t<engineLink>https://www.justjournal.com</engineLink>\n");
    sb.append("\t<homePageLink>").append(homePageLink).append("</homePageLink>\n");
    // APIS we support.
    sb.append("\t<apis>\n");
    sb.append("\t\t<api name=\"Blogger\" preferred=\"false\" apiLink=\"")
        .append(apiLink).append("\" blogID=\"").append(username).append("\" />\n");
    sb.append("\t\t<api name=\"MetaWeblog\" preferred=\"true\" apiLink=\"")
        .append(apiLink).append("\" blogID=\"").append(username).append("\" />\n");
    sb.append("\t</apis>\n");
    sb.append("</service>\n");
    sb.append(RSD_FOOTER);

    return rsd(HttpStatus.OK, sb.toString());
  }

  /** An explicit Content-Type makes Spring write the body as is, whatever the Accept header. */
  private static ResponseEntity<String> rsd(HttpStatus status, String body) {
    return ResponseEntity.status(status).contentType(RSD_XML).body(body);
  }
}
