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

/*
 * Copyright (c) 2005-2011 Lucas Holt
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


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.justjournal.model.FormatType;
import com.justjournal.model.RemoteReply;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * A comment
 *
 * @author Lucas Holt
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class CommentTo implements Serializable {

  @Serial
  private static final long serialVersionUID = 3594701186407268256L;

  private int id = 0;

  @Setter
  private String username;

  @Setter
  private int eid;

  @Setter
  private Date date;

  private String subject = "";

  private String body = "";

  @Getter @Setter private String format = "TEXT";

  /** True for a reply from another fediverse server; its id is not a local comment's. */
  @Getter @Setter private boolean remote;

  /** A remote reply's author's display name; the username is their {@code @user@host}. */
  @Getter @Setter private String authorName;

  /** A remote reply's author's profile page. */
  @Getter @Setter private String authorUrl;

  @JsonCreator
  public CommentTo() {
    super();
  }

  /** A reply from another fediverse server, as a comment whose body is cleaned HTML. */
  public static CommentTo of(final RemoteReply reply) {
    final CommentTo comment = new CommentTo();
    comment.setId(reply.getId());
    comment.setEid(reply.getEntryId());
    comment.setDate(reply.getPublished());
    comment.body = reply.getContent();
    comment.setFormat(FormatType.HTML.name());
    comment.setUsername(reply.getAuthorHandle());
    comment.setRemote(true);
    comment.setAuthorName(reply.getAuthorName());
    comment.setAuthorUrl(reply.getAuthorUrl());
    return comment;
  }

  public int getId() {
    return id;
  }

  public void setId(final int commentId) {
    if (commentId < 0) throw new IllegalArgumentException("commentId");

    this.id = commentId;
  }

  public Date getDate() {
    return date;
  }

    public String getSubject() {
    return subject;
  }

  public void setSubject(final String subjectText) {

    if (subjectText.isEmpty()) this.subject = "(no subject)";
    else this.subject = subjectText;
  }

  public String getBody() {
    return body;
  }

  public void setBody(final String bodyText) {
    if (bodyText.length() < 2) throw new IllegalArgumentException("body");

    this.body = bodyText;
  }

  public String getUsername() {
    return username;
  }

    public int getEid() {
    return eid;
  }

}
