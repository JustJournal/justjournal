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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * An account on another fediverse server that a writer follows. Only the count of accepted ones
 * is ever shown publicly; the list is for the writer alone.
 */
@Getter
@Setter
@Entity
@Table(name = "activitypub_following")
public class RemoteFollowing implements Serializable {

  @Serial private static final long serialVersionUID = 6127718530642017093L;

  /** Longest URL kept in the URL columns. */
  public static final int MAX_URL_LENGTH = RemoteFollower.MAX_URL_LENGTH;

  public static final int MAX_NAME_LENGTH = 255;

  public static final int MAX_HANDLE_LENGTH = 512;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  /** The following writer. */
  @Column(name = "user_id", nullable = false)
  private int userId;

  /** The followed remote actor's id. */
  @Column(name = "actor_id", length = MAX_URL_LENGTH, nullable = false)
  private String actorId;

  /** The remote actor's own inbox, where the Follow and its Undo go. */
  @Column(name = "inbox", length = MAX_URL_LENGTH, nullable = false)
  private String inbox;

  /** The inbox the remote server shares between its actors, when it has one. */
  @Column(name = "shared_inbox", length = MAX_URL_LENGTH)
  private String sharedInbox;

  /** The id of the Follow sent, which an Accept or Reject names. */
  @Column(name = "follow_id", length = MAX_URL_LENGTH, nullable = false)
  private String followId;

  @Column(name = "state", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private FollowingState state = FollowingState.PENDING;

  /** The account's name as its server gives it, plain text. */
  @Column(name = "display_name", length = MAX_NAME_LENGTH)
  private String displayName;

  /** The account as {@code @user@host}. */
  @Column(name = "handle", length = MAX_HANDLE_LENGTH)
  private String handle;

  /** The account's profile page. */
  @Column(name = "profile_url", length = MAX_URL_LENGTH)
  private String profileUrl;

  @Column(name = "created", nullable = false)
  private Date created = new Date();
}
