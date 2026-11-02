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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serial;
import java.io.Serializable;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A tag is a form of metadata about a blog entry. It is similar to a category.
 *
 * @author Lucas Holt
 * @version $Id: Tag.java,v 1.7 2012/06/23 18:15:31 laffer1 Exp $
 *     <p>Date: Apr 25, 2008 Time: 5:13:16 PM
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Entity
@Table(name = "tags")
public class Tag implements Serializable {

  @Serial
  private static final long serialVersionUID = 6428478632490878525L;
  /** Unique id to represent the global tag */
  @JsonProperty("id")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  /** common string representation for public consumption */
  public static final int MAX_NAME_LENGTH = 30;

  private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  @JsonProperty("name")
  @Column(name = "name", nullable = false, length = 30)
  private String name;

  @JsonProperty("count")
  @Transient
  private long count;

  @Setter
  @JsonProperty("type")
  @Transient
  private String type;

  @JsonCreator
  public Tag() {
    super();
  }

  @JsonIgnore
  public Tag(final String name) {
    super();
    this.setName(name);
  }

  /**
   * Get the unique identifier
   *
   * @return tag id > 0
   */
  public int getId() {
    return id;
  }

  /**
   * Set the unique id for the tag
   *
   * @param id tag id > 0
   */
  public void setId(final int id) {
    if (id < 1) throw new IllegalArgumentException("Tag id must be > 0");
    this.id = id;
  }

  /**
   * The number of tags
   *
   * @return tag count
   */
  public long getCount() {
    return count;
  }

  /**
   * Set the number of tag instances
   *
   * @param count number of tags
   */
  public void setCount(final long count) {
    this.count = count;
  }

  /**
   * The common name for the tag which the user will see
   *
   * @return 30 char or less string
   */
  public String getName() {
    return name;
  }

  /**
   * Set the common name to display to the user
   *
   * @param name 30 character or less string of letters, digits and single hyphens between them.
   */
  public void setName(final String name) {
    if (name == null || name.isEmpty()) throw new IllegalArgumentException("Name must be set");
    if (name.length() > MAX_NAME_LENGTH)
      throw new IllegalArgumentException("Name cannot be longer than 30 characters.");

    final String lower = name.toLowerCase(Locale.ROOT);
    if (!VALID_NAME.matcher(lower).matches())
      throw new IllegalArgumentException(
          "Name contains invalid characters.  Must be a-z, 0-9 and hyphens between them");

    this.name = lower;
  }

  /**
   * Turn a requested tag into the stored form: lowercase, a leading # dropped, accents removed, +
   * and # spelled out (c++ is cplusplus, c# is csharp) and every other run of characters
   * outside a-z and 0-9 replaced by one hyphen. Length is not checked.
   *
   * @param raw tag as the client sent it
   * @return cleaned name, empty when nothing usable is left
   */
  public static String normalizeName(final String raw) {
    if (raw == null) return "";

    String s = raw.strip();
    while (s.startsWith("#")) s = s.substring(1);

    s = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
    s = s.toLowerCase(Locale.ROOT).replace("+", "plus").replace("#", "sharp");
    s = s.replaceAll("[^a-z0-9]+", "-");
    return s.replaceAll("^-+|-+$", "");
  }

  public String getType() {
    return type;
  }

    @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    Class<?> oEffectiveClass = o instanceof HibernateProxy hibernateProxy ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
    Class<?> thisEffectiveClass = this instanceof HibernateProxy hibernateProxy? hibernateProxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
    if (thisEffectiveClass != oEffectiveClass) return false;
    Tag tag = (Tag) o;
    return Objects.equals(getId(), tag.getId());
  }

  @Override
  public final int hashCode() {
    return this instanceof HibernateProxy hibernateProxy ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
  }
}
