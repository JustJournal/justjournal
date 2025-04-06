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
package com.justjournal.rss;

import lombok.Setter;

/**
 * An RSS Item is one entry in an RSS feed.
 *
 * @author Lucas Holt
 * @version $Id: RssItem.java,v 1.5 2008/10/16 20:20:07 laffer1 Exp $ User: laffer1 Date: Aug 28,
 *     2003 Time: 12:19:36 AM
 */
public final class RssItem {

  private String title;
  @Setter
  private String link; // link of exact item.
  private String description;
  @Setter
  private String author; // email address of author of item.
  @Setter
  private String comments; // url of comments page for item.
  @Setter
  private String pubDate; // publication date.
  @Setter
  private String guid; // url to item, always works.

  /* file attachment rss 2 feature
  <enclosure url="http://www.scripting.com/mp3s/touchOfGrey.mp3"
  length="5588242" type="audio/mpeg"/>
  */
  private String enclosureURL;
  @Setter
  private String enclosureLength;
  @Setter
  private String enclosureType;

  @Setter
  private boolean truncateFields = true;

  public boolean isTruncateFields() {
    return truncateFields;
  }

    /**
   * URL pointed to an object to embed in the feed. MP3, images, and other elements make good
   * candidates.
   *
   * @return URL
   */
  public String getEnclosureURL() {
    return enclosureURL;
  }

  /**
   * We want to embed something in our feed.
   *
   * @param enclosureURL A string reepresentation of a valid URL pointing to a resource such as a
   *     jpeg, mp3, etc.
   */
  public void setEnclosureURL(String enclosureURL) {
    this.enclosureURL = enclosureURL;
  }

  /**
   * Size of item pointed to by the URL.
   *
   * @return Number of bytes as a string.
   */
  public String getEnclosureLength() {
    return enclosureLength;
  }

    public String getEnclosureType() {
    return enclosureType;
  }

    public String getAuthor() {
    return author;
  }

    public String getComments() {
    return comments;
  }

    public String getPubDate() {
    return pubDate;
  }

    public String getGuid() {
    return guid;
  }

    public String getTitle() {
    return title;
  }

  /**
   * Sets thte tile, triming the title to 100 characters
   *
   * @param title
   */
  public void setTitle(String title) {

    if (title.length() > 98 && truncateFields) {
      this.title = title.substring(0, 99);
    } else {
      this.title = title;
    }
  }

  public String getLink() {
    return link;
  }

    public String getDescription() {
    return description;
  }

  /**
   * Sets the description, trimming to 497
   *
   * @param description
   */
  public void setDescription(final String description) {

    if (description.length() > 495 && truncateFields) {
      this.description = description.substring(0, 496);
    } else {
      this.description = description;
    }
  }

  /** Clear all properties for reuse of the class instance. */
  public void recycle() {
    title = "";
    link = "";
    description = "";
    author = "";
    comments = "";
    pubDate = "";
    guid = "";
    enclosureURL = "";
    enclosureLength = "";
    enclosureType = "";
  }
}
