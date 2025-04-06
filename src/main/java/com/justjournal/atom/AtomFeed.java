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
package com.justjournal.atom;


import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.services.MarkdownService;
import com.justjournal.utility.DateConvert;
import com.justjournal.utility.Xml;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Create an atom feed.
 *
 * @author Lucas Holt
 * @version $Id: AtomFeed.java,v 1.5 2011/05/29 22:32:59 laffer1 Exp $
 */
@Slf4j
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AtomFeed {

  /*
    <feed xmlns="http://www.w3.org/2005/Atom"
      xml:lang="en"
      xml:base="http://www.example.org">
  <id>http://www.example.org/myfeed</id>
  <title>My Simple Feed</title>
  <updated>2005-07-15T12:00:00Z</updated>
  <link href="/blog" />
  <link rel="self" href="/myfeed" />
  <entry>
    <id>http://www.example.org/entries/1</id>
    <title>A simple blog entry</title>
    <link href="/blog/2005/07/1" />
    <updated>2005-07-15T12:00:00Z</updated>
    <summary>This is a simple blog entry</summary>
  </entry>
  <entry>
    <id>http://www.example.org/entries/2</id>
    <title />
    <link href="/blog/2005/07/2" />
    <updated>2005-07-15T12:00:00Z</updated>
    <summary>This is simple blog entry without a title</summary>
  </entry>
    </feed>
    */

  private MarkdownService markdownService;

  private static final int MAX_LENGTH = 15;

  private String id;
  private String title;
  private String updated;
  private String alternateLink;
  private String selfLink;
  private String authorName;
  private String userName;

  private List<AtomEntry> items = new ArrayList<>(MAX_LENGTH);

  @Autowired
  public AtomFeed(final MarkdownService markdownService) {
    this.markdownService = markdownService;
  }

  public void populate(Collection<Entry> entries) {
    AtomEntry item;

    // TODO: this sucks... need to make this reusable
    try {
      Entry o;
      Iterator<Entry> itr = entries.iterator();

      for (int x = 0, n = entries.size(); x < n && x < MAX_LENGTH; x++) {
        o = itr.next();
        item = new AtomEntry();
        item.setId("urn:jj:justjournal.com:atom1:" + o.getUser().getUsername() + ":" + o.getId());
        item.setTitle(o.getSubject());

        if (o.getFormat().equals(FormatType.MARKDOWN))
          item.setContent(markdownService.convertToText(o.getBody()));
        else item.setContent(o.getBody());
        item.setLink(
            "http://www.justjournal.com/users/"
                + o.getUser().getUsername()
                + "/entry/"
                + o.getId());
        item.setPublished(DateConvert.encode3339(o.getDate()));
        item.setUpdated(DateConvert.encode3339(o.getDate()));
        add(item);
      }
    } catch (final Exception ignored) {
    }
  }

  public void add(final AtomEntry item) {
    items.add(item);
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(final String userName) {
    this.userName = userName;
  }

  public String toXml() {
    final StringBuilder sb = new StringBuilder();

    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<?xml-stylesheet type=\"text/xsl\" href=\"/static/streamburner/streamburner.xsl\"?>\n");
    sb.append(
        "<feed xmlns=\"http://www.w3.org/2005/Atom\"\n"
            + "      xml:lang=\"en\"\n"
            + "      xml:base=\"http://www.justjournal.com\">\n");
    sb.append("\t<id>urn:jj:justjournal.com:atom1:");
    sb.append(userName);
    sb.append("</id>\n");

    sb.append("\t\t<title>");
    sb.append(Xml.cleanString(title));
    sb.append("</title>\n");

    sb.append("\t<author>\n\t\t<name>");
    sb.append(authorName);
    sb.append("</name>\n\t</author>\n");

    sb.append("<link rel=\"alternate\" type=\"text/html\" href=\"");
    sb.append(alternateLink);
    sb.append("\"/>\n");

    sb.append("<link rel=\"self\" href=\"");
    sb.append(selfLink);
    sb.append("\"/>\n");

    sb.append("<generator uri=\"https://github.com/laffer1/justjournal\" version=\"3.1.4\">JustJournal</generator>\n");

    sb.append("<updated>");
    sb.append(date());
    sb.append("</updated>\n");

    /*
    <link rel="service.feed" type="application/x.atom+xml"
    href="http://www.justjournal.com/users/laffer1/atom" title="Luke"/>
    */

    /* Iterator */
    AtomEntry o;
    Iterator<AtomEntry> itr = items.listIterator();
    for (int i = 0, n = items.size(); i < n && i < MAX_LENGTH; i++) // 15 is the limit for RSS
    {
      o = itr.next();

      sb.append("\t\t<entry>\n");

      sb.append("\t\t\t<id>");
      sb.append(o.getId());
      sb.append("</id>\n");

      sb.append("\t\t\t<title>");
      sb.append(Xml.cleanString(o.getTitle()));
      sb.append("</title>\n");

      sb.append("\t\t\t<link rel=\"alternate\" type=\"text/html\" href=\"");
      sb.append(o.getLink());
      sb.append("\"/>\n");

      sb.append("\t\t\t<published>");
      sb.append(o.getPublished());
      sb.append("</published>\n");

      sb.append("\t\t\t<updated>");
      sb.append(o.getUpdated());
      sb.append("</updated>\n");

      if (o.getSummary() != null) {
        sb.append("\t\t\t<summary>");
        sb.append(o.getSummary());
        sb.append("</summary>\n");
      }

      if (o.getContent() != null) {
        sb.append("\t\t\t<content type=\"html\">");
        sb.append(Xml.cleanString(o.getContent()));
        sb.append("</content>\n");
      }

      sb.append("\t\t</entry>\n");
    }

    sb.append("\t</feed>\n");

    return sb.toString();
  }

  private String date() {
    // Sat, 07 Sep 2002 09:43:33 GMT
    Calendar cal = new GregorianCalendar(java.util.TimeZone.getDefault());
    java.util.Date current = cal.getTime();
    // final SimpleDateFormat formatmydate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss
    // zz");

    return DateConvert.encode3339(current);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUpdated() {
    return updated;
  }

  public void setUpdated(String updated) {
    this.updated = updated;
  }

  public String getAlternateLink() {
    return alternateLink;
  }

  public void setAlternateLink(String alternateLink) {
    this.alternateLink = alternateLink;
  }

  public String getSelfLink() {
    return selfLink;
  }

  public void setSelfLink(String selfLink) {
    this.selfLink = selfLink;
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName(String authorName) {
    this.authorName = authorName;
  }
}
