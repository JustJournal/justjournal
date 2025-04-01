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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.justjournal.model.api.EntryTo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Journal entry transfer object. Contains one journal entry. Maps relationship between table
 * "entry" and java.
 *
 * @author Lucas Holt
 * @version 1.0
 * @see com.justjournal.repository.EntryRepository
 */
@Getter
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "entry")
public class Entry implements Serializable {
  @Serial
  private static final long serialVersionUID = 6558001750470601772L;

  @Setter
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id = 0;

  @Setter
  @Column(name = "date")
  @Temporal(value = TemporalType.TIMESTAMP)
  private Date date = new Date();

  @Setter
  @Column(name = "modified")
  @Temporal(value = TemporalType.TIMESTAMP)
  private Date modified;

  @JsonProperty("location")
  @ManyToOne
  @JoinColumn(name = "location")
  private Location location;

  @Setter
  @Column(name = "location", insertable = false, updatable = false)
  private int locationId = 0;

  @JsonProperty("mood")
  @ManyToOne
  @JoinColumn(name = "mood")
  private Mood mood;

  @Setter
  @Column(name = "mood", insertable = false, updatable = false)
  private int moodId = 0;

  @Setter
  @JsonProperty("user")
  @ManyToOne
  @JoinColumn(name = "uid")
  private User user;

  @JsonProperty("security")
  @ManyToOne
  @JoinColumn(name = "security")
  private Security security;

  @Setter
  @Column(name = "security", insertable = false, updatable = false)
  private int securityId = 0;

  @Setter
  @JsonProperty("subject")
  @Column(name = "subject", length = 255)
  private String subject = "";

  @Setter
  @Basic(fetch = FetchType.LAZY)
  @JsonProperty("body")
  @Column(name = "body")
  @Lob
  private String body = "";

  @Setter
  @JsonProperty("music")
  @Column(name = "music", length = 125)
  private String music = "";

  @Setter
  @JsonProperty("autoFormat")
  @Column(name = "autoformat", nullable = false, length = 1)
  @Convert(converter = PrefBoolConverter.class)
  private PrefBool autoFormat = PrefBool.Y;

  @Setter
  @JsonProperty("format")
  @Column(name = "format", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private FormatType format = FormatType.TEXT;

  @Setter
  @JsonProperty("allowComments")
  @Column(name = "allow_comments", nullable = false, length = 1)
  @Convert(converter = PrefBoolConverter.class)
  private PrefBool allowComments = PrefBool.Y;

  @Setter
  @JsonProperty("emailComments")
  @Column(name = "email_comments", nullable = false, length = 1)
  @Convert(converter = PrefBoolConverter.class)
  private PrefBool emailComments = PrefBool.Y;

  @Setter
  @Column(name = "draft", nullable = false, length = 1)
  @Convert(converter = PrefBoolConverter.class)
  private PrefBool draft = PrefBool.N;

  @Setter
  @Column(name = "trackback", length = 1024)
  private String trackback;

  // TODO: implement
  @JsonIgnore private transient int attachImage = 0;

  @JsonIgnore private transient int attachFile = 0;

  @JsonManagedReference(value = "entry-entrytag")
  @JsonProperty("tags")
  @OneToMany(cascade = CascadeType.ALL, mappedBy = "entry", fetch = FetchType.EAGER) // TODO: why!
  private Set<EntryTag> tags = new HashSet<>();

  @Setter
  @JsonManagedReference(value = "entry-comment")
  @JsonProperty("comments")
  @OneToMany(cascade = CascadeType.ALL, mappedBy = "entry", fetch = FetchType.EAGER)
  private Set<Comment> comments = new HashSet<>();

  @JsonCreator
  public Entry() {
    super();
  }

  public Entry(final int id, final String subject) {
    super();

    this.id = id;
    this.subject = subject;
  }

  public int getId() {
    return id;
  }

  public void setId(final int id) {
    this.id = id;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(final Date date) {
    this.date = date;
  }

  public Date getModified() {
    return modified;
  }

  public void setModified(final Date modified) {
    this.modified = modified;
  }

  public int getLocationId() {
    return locationId;
  }

  public void setLocationId(final int locationId) {
    this.locationId = locationId;
  }

  public Location getLocation() {
    return location;
  }

  public void setLocation(final Location location) {
    this.location = location;
    setLocationId(location.getId());
  }

  public int getMoodId() {
    return moodId;
  }

  public void setMoodId(final int moodId) {
    this.moodId = moodId;
  }

  public Mood getMood() {
    return mood;
  }

  public void setMood(final Mood mood) {
    this.mood = mood;

    if (mood != null) setMoodId(mood.getId());
    else setMoodId(0);
  }

  public User getUser() {
    return user;
  }

  public void setUser(final User user) {
    this.user = user;
  }

  public int getSecurityId() {
    return securityId;
  }

  public void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(final Security security) {
    this.security = security;
    setSecurityId(security.getId());
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(final String subject) {
    this.subject = subject;
  }

  public String getBody() {
    return body;
  }

  public void setBody(final String body) {
    this.body = body;
  }

  public String getMusic() {
    return music;
  }

  public void setMusic(final String music) {
    this.music = music;
  }

  public PrefBool getAutoFormat() {
    return autoFormat;
  }

  public FormatType getFormat() {
    return format;
  }

  public void setFormat(final FormatType format) {
    this.format = format;
  }

  public void setAutoFormat(final PrefBool autoFormat) {
    this.autoFormat = autoFormat;
  }

  public PrefBool getAllowComments() {
    return allowComments;
  }

  public void setAllowComments(final PrefBool allowComments) {
    this.allowComments = allowComments;
  }

  public PrefBool getEmailComments() {
    return emailComments;
  }

  public void setEmailComments(final PrefBool emailComments) {
    this.emailComments = emailComments;
  }

  public PrefBool getDraft() {
    return draft;
  }

  public void setDraft(final PrefBool draft) {
    this.draft = draft;
  }

  public int getAttachImage() {
    return attachImage;
  }

  public void setAttachImage(final int attachImage) {
    this.attachImage = attachImage;
  }

    public void setAttachFile(final int attachFile) {
    this.attachFile = attachFile;
  }

    public void setTags(final Set<EntryTag> tags) {
    this.tags = tags;
  }

    public Entry(final EntryTo entryTo) {
    if (entryTo.getMood() == 0) entryTo.setMood(12); // DEFAULT NOT SPECIFIED

    if (entryTo.getDate() == null) entryTo.setDate(Calendar.getInstance().getTime());

    setDate(entryTo.getDate());
    setBody(entryTo.getBody());
    setSubject(entryTo.getSubject());

    setLocationId(entryTo.getLocation());
    setMoodId(entryTo.getMood());

    setMusic(entryTo.getMusic());
    setModified(Calendar.getInstance().getTime());

    setAllowComments(entryTo.getAllowComments() ? PrefBool.Y : PrefBool.N);
    setDraft(entryTo.getDraft() ? PrefBool.Y : PrefBool.N);
    setEmailComments(entryTo.getEmailComments() ? PrefBool.Y : PrefBool.N);

    if (entryTo.getFormat().equals("MARKDOWN")) {
      setFormat(FormatType.MARKDOWN);
      setAutoFormat(PrefBool.N);
    } else if (entryTo.getFormat().equals("HTML")) {
      setFormat(FormatType.HTML);
      setAutoFormat(PrefBool.N);
    } else {
      setFormat(FormatType.TEXT);
      setAutoFormat(PrefBool.Y);
    }

    setTrackback(entryTo.getTrackback());
  }

  public EntryTo toEntryTo() {
    final EntryTo entryTo =
        EntryTo.builder()
            .subject(getSubject())
            .body(getBody())
            .location(getLocationId())
            .security(getSecurityId())
            .mood(getMoodId())
            .format(getFormat().toString())
            .date(getDate())
            .entryId(getId())
            .music(getMusic())
            .modified(getModified())
            .allowComments(getAllowComments() == PrefBool.Y)
            .autoFormat(getAutoFormat() == PrefBool.Y)
            .draft(getDraft() == PrefBool.Y)
            .emailComments(getEmailComments() == PrefBool.Y)
            .trackback(getTrackback())
            .build();

    if (entryTo.getDate() == null) entryTo.setDate(Calendar.getInstance().getTime());

    if (getUser() != null) {
      entryTo.setUser(getUser().getUsername());
    }

    if (getTags() != null) {
      entryTo.setTags(
          getTags().stream().map(t -> t.getTag().getName()).collect(Collectors.toSet()));
    } else {
      entryTo.setTags(new HashSet<>());
    }

    // TODO: needed?
    // entryTo.setComments(getComments().stream().map(Comment::toCommentTo).collect(Collectors.toSet()));

    return entryTo;
  }
}
