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

import com.justjournal.model.FeedAttachments;

/**
 * An Atom entry is a single record in an Atom feed.
 *
 * @author Lucas Holt
 * @version $Id: AtomEntry.java,v 1.1 2007/06/04 05:55:13 laffer1 Exp $
 */
public final class AtomEntry {

  /*
  <entry>
  <id>http://www.example.org/entries/1</id>
  <title>A simple blog entry</title>
  <link href="/blog/2005/07/1" />
  <updated>2005-07-15T12:00:00Z</updated>
  <summary>This is a simple blog entry</summary>
  </entry>
  */

  private String id;
  private String title;
  private String link;
  private String updated;
  private String published;
  private String content;
  private String summary;
  private FeedAttachments attachments;

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AtomEntry atomEntry = (AtomEntry) o;

    if (!id.equals(atomEntry.id)) return false;
    if (link != null ? !link.equals(atomEntry.link) : atomEntry.link != null) return false;
    if (content != null ? !content.equals(atomEntry.content) : atomEntry.content != null)
      return false;
    if (title != null ? !title.equals(atomEntry.title) : atomEntry.title != null) return false;
    return updated != null ? updated.equals(atomEntry.updated) : atomEntry.updated == null;
  }

  @Override
  public int hashCode() {
    int result;
    result = id.hashCode();
    result = 29 * result + (title != null ? title.hashCode() : 0);
    result = 29 * result + (link != null ? link.hashCode() : 0);
    result = 29 * result + (updated != null ? updated.hashCode() : 0);
    result = 29 * result + (content != null ? content.hashCode() : 0);
    return result;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(final String summary) {
    this.summary = summary;
  }

  /** The picture and link attached to the entry, or null. */
  public FeedAttachments getAttachments() {
    return attachments;
  }

  public void setAttachments(final FeedAttachments attachments) {
    this.attachments = attachments;
  }

  public String getPublished() {
    return published;
  }

  public void setPublished(final String published) {
    this.published = published;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(final String title) {
    this.title = title;
  }

  public String getLink() {
    return link;
  }

  public void setLink(final String link) {
    this.link = link;
  }

  public String getUpdated() {
    return updated;
  }

  public void setUpdated(final String updated) {
    this.updated = updated;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }
}
