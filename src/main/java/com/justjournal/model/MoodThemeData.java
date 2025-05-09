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


import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.Serial;
import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Emoticon resource data transfer object. Basic properties including the filename, height, width,
 * mood and theme ids.
 *
 * @author Lucas Holt
 * @version 1.0
 * @since 1.0 User: laffer1 Date: Sep 22, 2003 Time: 11:01:45 PM
 */
@Entity
@Table(name = "mood_theme_data")
public class MoodThemeData implements Serializable {
  @Serial
  private static final long serialVersionUID = 8664963658480263224L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(name = "picurl", length = 100)
  private String filename;

  @ManyToOne
  @JoinColumn(name = "moodid")
  private Mood mood;

  @ManyToOne
  @JoinColumn(name = "moodthemeid")
  private MoodTheme theme;

  @Column(name = "width")
  private int width;

  @Column(name = "height")
  private int height;

  @JsonCreator
  public MoodThemeData() {
    super();
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getFileName() {
    return filename;
  }

  public void setFileName(String value) {
    filename = value;
  }

  public MoodTheme getTheme() {
    return theme;
  }

  public void setTheme(MoodTheme value) {
    theme = value;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int value) {
    width = value;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int value) {
    height = value;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public Mood getMood() {
    return mood;
  }

  public void setMood(Mood mood) {
    this.mood = mood;
  }
}
