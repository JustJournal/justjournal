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
package com.justjournal.model.api;

import com.fasterxml.jackson.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.justjournal.model.Security;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.EntityModel;

/**
 * Journal entry transfer object.
 *
 * @author Lucas Holt
 * @version 1.0
 */
@AllArgsConstructor
@Builder
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntryTo extends EntityModel<EntryTo> implements Serializable {
  @Serial
  private static final long serialVersionUID = 6558001750470601777L;

  @Builder.Default
  @Getter @Setter private int entryId = 0;

  @Builder.Default
  @Getter @Setter private Date date = new Date();

  @Getter @Setter private Date modified;

  @Getter @Setter private int location;

  @Getter @Setter private int mood;

  @Getter @Setter private String user;

  @JsonSerialize(using = SecuritySerializer.class)
  @JsonDeserialize(using = SecurityDeserializer.class)
  @Getter @Setter
  private Security security;

  @Builder.Default
  @Getter @Setter private String subject = "";

  @Builder.Default
  @Getter @Setter private String body = "";

  @Builder.Default
  @Getter @Setter private String music = "";

  @Getter @Setter private String trackback;

  @Builder.Default
  @Getter @Setter private String format = "TEXT";

  @Getter
  @Setter
  @Builder.Default
  @JsonProperty("autoFormat")
  private Boolean autoFormat = true;

  @Getter
  @Setter
  @Builder.Default
  @JsonProperty("allowComments")
  private Boolean allowComments = true;

  @Getter
  @Setter
  @Builder.Default
  @JsonProperty("emailComments")
  private Boolean emailComments = true;

  @Builder.Default
  @Getter @Setter private Boolean draft = false;

  /** For backward compatibility with front end */
  @Deprecated
  @Getter
  @Setter
  @Builder.Default
  @JsonProperty("tag")
  private String tag = "";

  @Getter
  @Setter
  @Builder.Default
  @JsonProperty("tags")
  private Set<String> tags = new HashSet<>();

  @Getter
  @Setter
  @Builder.Default
  @JsonProperty("comments")
  private Set<CommentTo> comments = new HashSet<>();

  @JsonCreator
  public EntryTo() {
    super();
  }
}
