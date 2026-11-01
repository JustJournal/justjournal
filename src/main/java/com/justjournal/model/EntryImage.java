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

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * The picture attached to a blog post. It is uploaded on its own before the post is saved, and
 * {@link #getEntryId() entryId} stays null until a save attaches it. Unlike album pictures, it
 * is shown only to those who may see its post.
 *
 * @author Lucas Holt
 */
@Getter
@Setter
@Entity
@Table(name = "entry_image")
public class EntryImage implements Serializable {

  @Serial private static final long serialVersionUID = -3482150877021594771L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  /** The user who uploaded it. */
  @Column(name = "owner", nullable = false)
  private int ownerId;

  /** The post it is attached to, or null until a save attaches it. */
  @Column(name = "entry_id")
  private Integer entryId;

  @Column(name = "mimetype", length = 45, nullable = false)
  private String mimeType;

  /** The picture as uploaded, in the image bucket. */
  @JsonIgnore
  @Column(name = "filename", length = 150, nullable = false)
  private String filename;

  /** The JPEG shown on the post, in the image bucket. */
  @JsonIgnore
  @Column(name = "display", length = 150, nullable = false)
  private String display;

  /** Size of the picture as uploaded, in bytes. */
  @Column(name = "size", nullable = false)
  private long size;

  /** Width of the display image in pixels. */
  @Column(name = "width", nullable = false)
  private int width;

  /** Height of the display image in pixels. */
  @Column(name = "height", nullable = false)
  private int height;

  /** What the picture shows, for readers who can't see it. */
  @Column(name = "alt_text", length = 255)
  private String altText;

  /** The content filter's verdict. Anything but APPROVED is shown only to the owner. */
  @JsonIgnore
  @Column(name = "moderation_status", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

  /** Why the filter decided as it did; see {@link UserImage#getModerationCategory()}. */
  @JsonIgnore
  @Column(name = "moderation_category", length = 16)
  private String moderationCategory;

  @Column(name = "created", nullable = false)
  private Date created = new Date();
}
