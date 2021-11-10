/*
Copyright (c) 2003-2021, Lucas Holt
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

  Redistributions of source code must retain the above copyright notice, this list of
  conditions and the following disclaimer.

  Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or other
  materials provided with the distribution.

  Neither the name of the Just Journal nor the names of its contributors
  may be used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/
package com.justjournal.model;


import com.justjournal.model.api.TrackbackTo;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.NoArgsConstructor;

/**
 * A Trackback ping
 *
 * @author Lucas Holt
 */
@NoArgsConstructor
@Entity
@Table(name = "trackback")
public class Trackback implements Serializable {
  private static final long serialVersionUID = 1249662473110605504L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  @Column(name = "eid")
  private int entryId = 0;

  @Column(name = "date")
  @Temporal(value = TemporalType.DATE)
  private Date date = new Date();

  @Column(name = "subject", length = 150)
  private String subject = null;

  @Lob private String body = null;

  @Column(name = "author_email", length = 150)
  private String authorEmail = null;

  @Column(name = "author_name", length = 50)
  private String authorName = null;

  @Column(name = "blogname", length = 150)
  private String blogName = null;

  @Column(name = "url", length = 150)
  private String url = null;

  @Column(name = "type", length = 10)
  @Enumerated(EnumType.STRING)
  private TrackbackType type = null;

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
    if (subject == null || subject.isEmpty()) this.subject = "(no subject)";
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

  public TrackbackTo toTrackbackTo() {
    final TrackbackTo trackbackTo = new TrackbackTo();
    trackbackTo.setBody(getBody());
    trackbackTo.setDate(getDate());
    trackbackTo.setSubject(getSubject());
    trackbackTo.setId(getId());
    trackbackTo.setAuthorEmail(getAuthorEmail());
    trackbackTo.setAuthorName(getAuthorName());
    trackbackTo.setBlogName(getBlogName());
    trackbackTo.setEntryId(getEntryId());
    trackbackTo.setType(getType());

    return trackbackTo;
  }
}
