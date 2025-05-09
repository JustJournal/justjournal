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
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;

/**
 * Blog Link List
 *
 * @author Lucas Holt
 */
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Entity
@Table(name = "user_link")
public class UserLink implements Serializable, Comparable<UserLink> {
  @Serial
  private static final long serialVersionUID = 6356304916167520610L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "linkid")
  private int id;

  @Column(name = "title")
  private String title;

  @Column(name = "uri")
  private String uri;

  @JsonBackReference
  @JsonIgnore
  @ManyToOne
  @JoinColumn(name = "user_id")
  private User user;

  @JsonCreator
  public UserLink() {
    super();
  }

  /**
   * Retrieve unique identifier for link
   *
   * @return int > 0
   */
  public int getId() {
    return id;
  }

  /**
   * Set unique identifier for link
   *
   * @param id > 0
   */
  public void setId(int id) {
    if (id > 0) this.id = id;
    else throw new IllegalArgumentException("id must be greater than zero");
  }

  /**
   * Retrieve the link title
   *
   * @return title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set the link title so that it can be displayed instead of just the hyperlink itself.
   *
   * @param title ascii text
   */
  public void setTitle(String title) {
    if (title != null) this.title = title;
    else throw new IllegalArgumentException("title cannot be null");
  }

  /**
   * Retrieve the address of the link which should be a complete URI
   *
   * @return uri string
   */
  public String getUri() {
    return uri;
  }

  /**
   * Set the Uniform resource identifier as a string
   *
   * @param uri a valid uri
   */
  public void setUri(String uri) {
    if (uri != null) this.uri = uri;
    else throw new IllegalArgumentException("uri cannot be null");
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  @Override
  public int compareTo(UserLink ul) {
    return this.getTitle().compareTo(ul.getTitle());
  }
}
