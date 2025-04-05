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
import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/** @author Lucas Holt */
@Setter
@Getter
@Component
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class UserStatistics implements Serializable {
  @JsonProperty("username")
  private String username = "";

  @JsonProperty("entryCount")
  private int entryCount = 0;

  @JsonProperty("commentCount")
  private int commentCount = 0;

  @JsonCreator
  public UserStatistics() { /* TODO document why this constructor is empty */ }

    @JsonIgnore
  @Override
  public String toString() {
    return "UserStatisticsImpl{"
        + "username='"
        + username
        + '\''
        + ", entryCount="
        + entryCount
        + ", commentCount="
        + commentCount
        + '}';
  }

  @JsonIgnore
  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final UserStatistics that = (UserStatistics) o;

    if (commentCount != that.commentCount) return false;
    if (entryCount != that.entryCount) return false;
    if (!username.equals(that.username)) return false;

    return true;
  }

  @JsonIgnore
  @Override
  public int hashCode() {
    int result = username.hashCode();
    result = 31 * result + entryCount;
    result = 31 * result + commentCount;
    return result;
  }
}
