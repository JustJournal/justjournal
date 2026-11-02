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
 * A reply to an entry from an account on another fediverse server, shown with the entry's
 * comments. It has no local user, so it is kept apart from {@link Comment}, whose user is always a
 * local one.
 */
@Getter
@Setter
@Entity
@Table(name = "activitypub_reply")
public class RemoteReply implements Serializable {

  @Serial private static final long serialVersionUID = -3127784560127659834L;

  /** Longest URL kept in the URL columns. */
  public static final int MAX_URL_LENGTH = RemoteFollower.MAX_URL_LENGTH;

  /** Longest author name or handle kept. */
  public static final int MAX_NAME_LENGTH = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  /** The entry it replies to. */
  @Column(name = "entry_id", nullable = false)
  private int entryId;

  /** The reply's ActivityPub id; the same reply delivered again has the same one. */
  @Column(name = "object_id", length = MAX_URL_LENGTH, nullable = false)
  private String objectId;

  /** The author's actor id, which signed the reply. */
  @Column(name = "actor_id", length = MAX_URL_LENGTH, nullable = false)
  private String actorId;

  /** The author's display name. */
  @Column(name = "author_name", length = MAX_NAME_LENGTH, nullable = false)
  private String authorName;

  /** The author as {@code @user@host}, or null when the actor gave no usable username. */
  @Column(name = "author_handle", length = MAX_NAME_LENGTH)
  private String authorHandle;

  /** The author's profile page. */
  @Column(name = "author_url", length = MAX_URL_LENGTH, nullable = false)
  private String authorUrl;

  /** The reply as cleaned HTML: text, paragraphs, line breaks and links only. */
  @Column(name = "content", nullable = false)
  private String content;

  /** When the author says it was written, but no later than when it arrived. */
  @Column(name = "published", nullable = false)
  private Date published = new Date();

  @Column(name = "created", nullable = false)
  private Date created = new Date();

  /** When the author last changed it, or null. */
  @Column(name = "updated")
  private Date updated;
}
