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
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;

/** @author Lucas Holt */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LoginResponse implements Serializable {
  @Serial
  private static final long serialVersionUID = 6809817814936626615L;
  private String status = null;
  private String username = null;

  /** Only present with status JJ.LOGIN.2FA: how the second factor is delivered (EMAIL or TOTP). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String twoFactorMethod = null;

  @JsonCreator
  public LoginResponse() {
    super();
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getStatus() {
    return status;
  }

  public String getTwoFactorMethod() {
    return twoFactorMethod;
  }

  public void setTwoFactorMethod(final String twoFactorMethod) {
    this.twoFactorMethod = twoFactorMethod;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  @JsonIgnore
  @Override
  public String toString() {
    return "LoginResponse{"
        + "status='"
        + status
        + '\''
        + ", username='"
        + username
        + '\''
        + ", twoFactorMethod='"
        + twoFactorMethod
        + '\''
        + '}';
  }
}
