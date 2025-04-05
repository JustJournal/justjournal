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
package com.justjournal.model.api;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.justjournal.model.TrackbackType;
import java.io.Serializable;
import java.util.Date;

/**
 * A Trackback ping
 *
 * @author Lucas Holt
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class TrackbackTo implements Serializable {

  private int id;

  private int entryId = 0;

  @JsonIgnore private Date date = new Date();

  private String subject = null;

  private String body = null;

  private String authorEmail = null;

  private String authorName = null;

  private String blogName = null;

  private String url = null;

  private TrackbackType type = null;

  @JsonCreator
  public TrackbackTo() {
    super();
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    if (url == null || url.length() < 5) throw new IllegalArgumentException("Illegal url: " + url);

    this.url = url;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    if (id < 0) throw new IllegalArgumentException("Illegal id: " + id);
    this.id = id;
  }

  public int getEntryId() {
    return entryId;
  }

  public void setEntryId(int entryId) {
    if (entryId < 0) throw new IllegalArgumentException("Illegal eid: " + entryId);
    this.entryId = entryId;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    if (subject.length() == 0) this.subject = "(no subject)";
    else this.subject = subject;
  }

  public String getBlogName() {
    return blogName;
  }

  public void setBlogName(String blogName) {
    if (blogName == null) this.blogName = "";
    else this.blogName = blogName;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    if (body == null) this.body = "";
    else this.body = body;
  }

  public String getAuthorEmail() {
    return authorEmail;
  }

  public void setAuthorEmail(String authorEmail) {
    if (authorEmail == null) this.authorEmail = "";
    else this.authorEmail = authorEmail;
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName(String authorName) {
    if (authorName == null) this.authorName = "";
    else this.authorName = authorName;
  }

  public TrackbackType getType() {
    return type;
  }

  public void setType(TrackbackType type) {
    this.type = type;
  }
}
