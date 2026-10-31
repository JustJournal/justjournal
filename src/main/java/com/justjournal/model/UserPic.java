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
package com.justjournal.model;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** @author Lucas Holt */
@Getter
@Setter
@Entity
@Table(name = "user_pic")
public class UserPic implements Serializable {

  @Serial
  private static final long serialVersionUID = -180763510438968573L;

  @Id
  // @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  /*
  @JsonIgnore
  @OneToOne
  @JoinColumn(name = "owner")
  private User user;
  */

  @Column(name = "mimetype", length = 75, nullable = false)
  private String mimeType;

  @Column(name = "filename", length = 100)
  private String filename;

  @Temporal(value = TemporalType.TIMESTAMP)
  @Column(name = "date_modified")
  private Date modified;

  @JsonProperty("source")
  @Column(name = "source", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private AvatarSource source = AvatarSource.UPLOAD;

  /** The content filter's verdict. Anything but APPROVED is shown only to the owner. */
  @JsonProperty("moderationStatus")
  @Column(name = "moderation_status", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private ModerationStatus moderationStatus = ModerationStatus.APPROVED;
}
