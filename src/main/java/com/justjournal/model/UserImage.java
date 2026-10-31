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


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.*;

/**
 * User Images
 *
 * @author Lucas Holt
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Entity
@Table(name = "user_images")
public class UserImage implements Serializable {
  @Serial
  private static final long serialVersionUID = 6356304916167520629L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private int id;

  @Column(name = "title", length = 150, nullable = true)
  private String title;

  @JsonBackReference
  @JsonIgnore
  @ManyToOne
  @JoinColumn(name = "owner")
  private User user;

  @Column(name = "mimetype", length = 45, nullable = false)
  private String mimeType;

  @Column(name = "modified")
  private Date modified;

  /*
   * The image column still holds pictures uploaded before they were stored in MinIO, until they
   * are copied there. It isn't mapped, so listing pictures doesn't load every image.
   */

  /** The picture in the image bucket, or null if it is still only in the database. */
  @JsonIgnore
  @Column(name = "filename", length = 150)
  private String filename;

  /** Its thumbnail in the image bucket. */
  @JsonIgnore
  @Column(name = "thumbnail", length = 150)
  private String thumbnail;

  /** Size of the picture in bytes. */
  @Column(name = "size")
  private Long size;

  /** When a picture stored in the database was copied to MinIO and checked. */
  @JsonIgnore
  @Column(name = "migrated")
  private Date migrated;

  /** The content filter's verdict. Anything but APPROVED is shown only to the owner. */
  @JsonIgnore
  @Column(name = "moderation_status", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private ModerationStatus moderationStatus = ModerationStatus.APPROVED;

  @JsonCreator
  public UserImage() {
    super();
  }

  /**
   * Retrieve unique identifier for image
   *
   * @return int > 0
   */
  public int getId() {
    return id;
  }

  /**
   * Set unique identifier for image
   *
   * @param id > 0
   */
  public void setId(int id) {
    if (id > 0) this.id = id;
    else throw new IllegalArgumentException("id must be greater than zero");
  }

  /**
   * Retrieve the image title
   *
   * @return title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set the image title so that it can be displayed instead of just the image itself.
   *
   * @param title ascii text
   */
  public void setTitle(String title) {
    if (title != null) this.title = title;
    else throw new IllegalArgumentException("title cannot be null");
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public Date getModified() {
    return modified;
  }

  public void setModified(Date modified) {
    this.modified = modified;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getThumbnail() {
    return thumbnail;
  }

  public void setThumbnail(String thumbnail) {
    this.thumbnail = thumbnail;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  public Date getMigrated() {
    return migrated;
  }

  public void setMigrated(Date migrated) {
    this.migrated = migrated;
  }

  public ModerationStatus getModerationStatus() {
    return moderationStatus;
  }

  public void setModerationStatus(ModerationStatus moderationStatus) {
    this.moderationStatus = moderationStatus;
  }
}
