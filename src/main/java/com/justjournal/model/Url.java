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


import com.justjournal.utility.DateConvert;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;

@XmlAccessorType(value = XmlAccessType.NONE)
@XmlRootElement(name = "url")
public class Url {
  public enum Priority {
    HIGH("1.0"),
    MEDIUMHIGH("0.7"),
    MEDIUM("0.5"),
    MEDIUMLOW("0.2"),
    LOW("0.0");

    private String value;

    Priority(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  @Getter
  public enum ChangeFreqency {
    ALWAYS("always"),
    HOURLY("hourly"),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
    NEVER("never");

    private final String value;

    ChangeFreqency(final String value) {
      this.value = value;
    }

  }

  @XmlElement private String loc;

  @XmlElement private String lastmod = DateConvert.encode8601();

  @XmlElement private String changefreq = ChangeFreqency.DAILY.getValue();

  @XmlElement private String priority = Priority.MEDIUM.getValue();

  public Url() {
    this("", Priority.MEDIUM, ChangeFreqency.DAILY);
  }

  public Url(final String loc, final Priority priority) {
    this(loc, priority, ChangeFreqency.DAILY);
  }

  public Url(final String loc, final Priority priority, final ChangeFreqency freqency) {
    this.loc = loc;
    this.priority = priority.getValue();
    this.changefreq = freqency.getValue();
  }

  public String getLoc() {
    return loc;
  }

  public String getPriority() {
    return priority;
  }

  public String getChangefreq() {
    return changefreq;
  }

  public String getLastmod() {
    return lastmod;
  }
}
