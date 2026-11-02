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

import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.FormatType;
import com.justjournal.services.MarkdownService;
import com.justjournal.utility.DateConvert;
import com.justjournal.utility.Xml;
import java.util.*;

import lombok.Getter;
import lombok.Setter;
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

  private final MarkdownService markdownService;
  private final Settings settings;

  private static final int MAX_LENGTH = 15;

  @Setter
  @Getter
  private String id;
  @Setter
  @Getter
  private String title;
  @Setter
  @Getter
  private String updated;
  @Setter
  private String alternateLink;
  @Setter
  private String selfLink;
  @Setter
  private String authorName;
  private String userName;

  private final List<AtomEntry> items = new ArrayList<>(MAX_LENGTH);

  @Autowired
  public AtomFeed(final MarkdownService markdownService, final Settings settings) {
    this.markdownService = markdownService;
    this.settings = settings;
  }

  public void populate(Collection<Entry> entries) {
    populate(entries, Map.of());
  }

  /**
   * Populate the feed with entries and what is attached to them.
   *
   * @param entries entries, newest first
   * @param attachments the picture and link of each entry, by entry id; entries missing have none
   */
  public void populate(final Collection<Entry> entries, final Map<Integer, FeedAttachments> attachments) {
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

        final String content;
        if (o.getFormat().equals(FormatType.MARKDOWN))
          content = markdownService.convertToText(o.getBody());
        else content = o.getBody();
        final FeedAttachments attached = attachments.get(o.getId());
        item.setAttachments(attached);
        item.setContent(withAttachments(content, attached));
        item.setLink(settings.getBlogBaseUrl(o.getUser().getUsername()) + "/entry/" + o.getId());
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
            + "      xmlns:media=\"http://search.yahoo.com/mrss/\"\n"
            + "      xml:lang=\"en\"\n"
            + "      xml:base=\"https://www.justjournal.com\">\n");
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

    sb.append("<generator uri=\"https://github.com/laffer1/justjournal\" version=\"3.1.10\">JustJournal</generator>\n");

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

      appendAttachments(sb, o.getAttachments());

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

  /**
   * The picture above the text and the link below it, for readers that only show the content.
   * Values are escaped here as HTML; toXml escapes the whole content again as XML.
   */
  private static String withAttachments(final String content, final FeedAttachments a) {
    if (a == null) return content;
    final StringBuilder html = new StringBuilder();
    if (a.hasImage()) {
      html.append("<p><img src=\"").append(Xml.cleanString(a.imageThumbnailUrl()));
      html.append("\" alt=\"").append(Xml.cleanString(a.imageAlt())).append("\"/></p>\n");
    }
    if (content != null) html.append(content);
    if (a.hasLink()) {
      html.append("\n<p><a href=\"").append(Xml.cleanString(a.linkUrl())).append("\">");
      html.append(Xml.cleanString(a.linkTitle())).append("</a>");
      if (a.linkDescription() != null && !a.linkDescription().isEmpty()) {
        html.append("<br/>").append(Xml.cleanString(a.linkDescription()));
      }
      html.append("</p>");
    }
    return html.toString();
  }

  /** The picture as an enclosure and Media RSS thumbnail, and the link as a related link. */
  private static void appendAttachments(final StringBuilder sb, final FeedAttachments a) {
    if (a == null) return;
    if (a.hasImage()) {
      sb.append("\t\t\t<link rel=\"enclosure\" type=\"").append(Xml.cleanString(a.imageType()));
      sb.append("\" length=\"").append(a.imageLength());
      sb.append("\" href=\"").append(Xml.cleanString(a.imageUrl())).append("\"/>\n");
    }
    if (a.hasLink()) {
      sb.append("\t\t\t<link rel=\"related\" type=\"text/html\" href=\"");
      sb.append(Xml.cleanString(a.linkUrl()));
      sb.append("\" title=\"").append(Xml.cleanString(a.linkTitle())).append("\"/>\n");
    }
    final String thumbnail = a.previewImageUrl();
    if (thumbnail != null) {
      final int width = a.hasImage() ? a.imageWidth() : a.linkImageWidth();
      final int height = a.hasImage() ? a.imageHeight() : a.linkImageHeight();
      sb.append("\t\t\t<media:thumbnail url=\"").append(Xml.cleanString(thumbnail)).append('"');
      if (width > 0 && height > 0) {
        sb.append(" width=\"").append(width).append("\" height=\"").append(height).append('"');
      }
      sb.append("/>\n");
    }
  }

  private String date() {
    // Sat, 07 Sep 2002 09:43:33 GMT
    Calendar cal = new GregorianCalendar(java.util.TimeZone.getDefault());
    java.util.Date current = cal.getTime();
    // final SimpleDateFormat formatmydate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss
    // zz");

    return DateConvert.encode3339(current);
  }

    public String getAlternateLink() {
    return alternateLink;
  }

    public String getSelfLink() {
    return selfLink;
  }

    public String getAuthorName() {
    return authorName;
  }

}
