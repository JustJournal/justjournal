/*
 * Copyright (c) 2003-2026 Lucas Holt
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
package com.justjournal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * A public post by a followed fediverse account, kept for a while to show on its followers'
 * friends pages. The content is stored already cleaned; attachments are links, never images.
 */
@Getter
@Setter
@Entity
@Table(name = "activitypub_remote_post")
public class RemotePost implements Serializable {

  @Serial private static final long serialVersionUID = -3305186412837562710L;

  public static final int MAX_URL_LENGTH = RemoteFollower.MAX_URL_LENGTH;

  public static final int MAX_NAME_LENGTH = 512;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  /** The post's ActivityPub id. */
  @Column(name = "object_id", length = MAX_URL_LENGTH, nullable = false, unique = true)
  private String objectId;

  /** The author's actor id. */
  @Column(name = "actor_id", length = MAX_URL_LENGTH, nullable = false)
  private String actorId;

  /** Where the post can be read on its own server. */
  @Column(name = "url", length = MAX_URL_LENGTH)
  private String url;

  /** An article's title, plain text. */
  @Column(name = "name", length = MAX_NAME_LENGTH)
  private String name;

  /** The content warning, plain text. */
  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  /** Cleaned HTML: text, paragraphs, line breaks and links. */
  @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
  private String content;

  /** A JSON array of {@code {"url", "name"}} links, or null. */
  @Column(name = "attachments", columnDefinition = "TEXT")
  private String attachments;

  @Column(name = "published", nullable = false)
  private Date published;

  @Column(name = "created", nullable = false)
  private Date created = new Date();

  @Column(name = "updated")
  private Date updated;
}
