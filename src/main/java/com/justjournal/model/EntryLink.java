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
 * The link attached to a blog post and its Open Graph preview, which a background job fetches
 * after the post is saved. A changed link is a new row, so a preview never belongs to another
 * address.
 *
 * @author Lucas Holt
 */
@Getter
@Setter
@Entity
@Table(name = "entry_link")
public class EntryLink implements Serializable {

  @Serial private static final long serialVersionUID = 5170982336095817428L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  /** The user who added it. */
  @Column(name = "owner", nullable = false)
  private int ownerId;

  /** The post it is attached to, or null when that post is gone. */
  @Column(name = "entry_id")
  private Integer entryId;

  /** The checked, normalized link; the only address ever rendered as a link. */
  @Column(name = "url", length = 1024, nullable = false)
  private String url;

  /** Where redirects ended when the preview was fetched. */
  @Column(name = "final_url", length = 1024)
  private String finalUrl;

  @Column(name = "title", length = 300)
  private String title;

  @Column(name = "description", length = 1000)
  private String description;

  @Column(name = "site_name", length = 200)
  private String siteName;

  /** The preview image in the image bucket, or null if the page had none that could be used. */
  @JsonIgnore
  @Column(name = "image_filename", length = 150)
  private String imageFilename;

  @Column(name = "image_width")
  private Integer imageWidth;

  @Column(name = "image_height")
  private Integer imageHeight;

  /** The content filter's verdict on the preview image; null when there is none. */
  @JsonIgnore
  @Column(name = "image_moderation_status", length = 8)
  @Enumerated(EnumType.STRING)
  private ModerationStatus imageModerationStatus;

  @JsonIgnore
  @Column(name = "image_moderation_category", length = 16)
  private String imageModerationCategory;

  @Column(name = "preview_status", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private LinkPreviewStatus previewStatus = LinkPreviewStatus.PENDING;

  /** Why the preview failed or was blocked; for the logs and the owner, never for readers. */
  @JsonIgnore
  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  /** How many times fetching the preview was tried. */
  @Column(name = "attempts", nullable = false)
  private int attempts;

  /** When a PENDING preview may next be fetched; also the lease a running fetch holds. */
  @JsonIgnore
  @Column(name = "next_attempt_at")
  private Date nextAttemptAt = new Date();

  @Column(name = "fetched_at")
  private Date fetchedAt;

  @Column(name = "created", nullable = false)
  private Date created = new Date();
}
