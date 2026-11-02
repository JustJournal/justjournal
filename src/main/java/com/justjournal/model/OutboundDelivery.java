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

/** One activity to post to one remote inbox, and how far that has got. */
@Getter
@Setter
@Entity
@Table(name = "activitypub_delivery")
public class OutboundDelivery implements Serializable {

  @Serial private static final long serialVersionUID = -6024163771853350958L;

  /** Matches the last_error column size. */
  public static final int MAX_ERROR_LENGTH = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  /** The sending user, or null for the instance actor. */
  @Column(name = "user_id")
  private Integer userId;

  /** The sender's actor id, which also picks the key the request is signed with. */
  @Column(name = "actor_id", length = RemoteFollower.MAX_URL_LENGTH, nullable = false)
  private String actorId;

  @Column(name = "target_inbox", length = RemoteFollower.MAX_URL_LENGTH, nullable = false)
  private String targetInbox;

  /** The activity as JSON, posted exactly as stored. */
  @Column(name = "activity", nullable = false, columnDefinition = "MEDIUMTEXT")
  private String activity;

  /** How many times posting was tried. */
  @Column(name = "attempts", nullable = false)
  private int attempts;

  /** When a PENDING delivery may next be tried; also the lease a running attempt holds. */
  @Column(name = "next_attempt_at", nullable = false)
  private Date nextAttemptAt = new Date();

  /** Why the last attempt failed; for the logs, never shown to anyone. */
  @Column(name = "last_error", length = MAX_ERROR_LENGTH)
  private String lastError;

  @Column(name = "status", nullable = false, length = 9)
  @Enumerated(EnumType.STRING)
  private DeliveryStatus status = DeliveryStatus.PENDING;

  @Column(name = "created", nullable = false)
  private Date created = new Date();

  /** When it was delivered or given up. */
  @Column(name = "finished_at")
  private Date finishedAt;
}
