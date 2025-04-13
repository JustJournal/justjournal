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
package com.justjournal.core;


import com.justjournal.repository.SettingsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Global settings for the site. Any changes here will effect the entire site. User preferences are
 * separate and any negative setting here will supersede per user settings like commentMailEnable =
 * false would disable email comments even if Users have them turned on.
 *
 * @author Lucas Holt
 */
@Slf4j
@Component
public class Settings {

  private final SettingsRepository settingsDao;

  /* paths */
  @Getter
  private String baseUri = "http://localhost:8080/";
  private String fsPath = ""; // file storage path.
  private String contextPath = "";

  /* site settings */
  private boolean siteEnable = true;
  @Getter
  private String siteName = "";
  @Getter
  private String siteAdmin = "";
  @Getter
  private String siteAdminEmail = "";
  private String siteBlog = ""; // blog about site activities, updates.
  private boolean siteSearch = true; // enable site search feature
  private boolean siteDirectory = true; // member directory
  private boolean siteRss = true; // recent blog posts

  /* e-mail */
  private boolean mailEnable = true;
  @Getter
  private String mailHost = "localhost";
  @Getter
  private int mailPort = 25;
  @Getter
  private String mailUser = "";
  private String mailPass = "";
  private String mailFrom = siteAdminEmail;
  private String mailSubject = siteName + " Notification";

  /* comments */
  private boolean commentEnable = true;
  private boolean commentMailEnable = true;
  private boolean commentEnableAnonymous = false;

  /* time */
  private int tzOffset = -5;
  private String tzName = "EST";
  private boolean tzLocalize = true;
  private boolean tzUseGMT = true;

  /* Users */
  @Getter
  private boolean userAllowNew = true;

  @Autowired
  public Settings(SettingsRepository settingsDao) {
    this.settingsDao = settingsDao;
    try {
      String name;
      String value;

      for (com.justjournal.model.Settings set : settingsDao.findAll(Sort.by("name"))) {
        value = set.getValue();
        name = set.getName();

        if (name == null) {
          log.warn("setting.name is null");
          continue;
        }
        if (value == null) {
          log.warn("setting.value is null");
          continue;
        }

        if (name.equalsIgnoreCase("baseuri")) baseUri = value;
        else if (name.equalsIgnoreCase("fsPath")) fsPath = value;
        else if (name.equalsIgnoreCase("contextPath")) contextPath = value;
        else if (name.equalsIgnoreCase("siteEnable")) siteEnable = value.equalsIgnoreCase("true");
        else if (name.equalsIgnoreCase("siteName")) siteName = value;
        else if (name.equalsIgnoreCase("siteAdmin")) siteAdmin = value;
        else if (name.equalsIgnoreCase("siteAdminEmail")) siteAdminEmail = value;
        else if (name.equalsIgnoreCase("siteBlog")) siteBlog = value;
        else if (name.equalsIgnoreCase("siteSearch")) {
          siteSearch = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("siteDirectory")) {
          siteDirectory = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("siteRss")) {
          siteRss = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("mailEnable")) {
          mailEnable = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("mailHost")) mailHost = value;
        else if (name.equalsIgnoreCase("mailPort")) mailPort = Integer.parseInt(value);
        else if (name.equalsIgnoreCase("mailUser")) mailUser = value;
        else if (name.equalsIgnoreCase("mailPass")) mailPass = value;
        else if (name.equalsIgnoreCase("mailFrom")) mailFrom = value;
        else if (name.equalsIgnoreCase("mailSubject")) mailSubject = value;
        else if (name.equalsIgnoreCase("commentEnable")) {
          commentEnable = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("commentMailEnable")) {
          commentMailEnable = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("commentEnableAnonymous")) {
          mailEnable = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("tzOffset")) tzOffset = Integer.parseInt(value);
        else if (name.equalsIgnoreCase("tzName")) tzName = value;
        else if (name.equalsIgnoreCase("tzLocalize")) {
          tzLocalize = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("tzUseGMT")) {
          tzUseGMT = value.equalsIgnoreCase("true");
        } else if (name.equalsIgnoreCase("userAllowNew")) {
          userAllowNew = value.equalsIgnoreCase("true");
        }
      }
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  /**
   * Get the base URL for the user blog with the given username.
   * @param username blog owner username
   * @return full url minus the trailing slash.
   */
  public String getBlogBaseUrl(String username) {
    return getBaseUri() + Constants.PATH_USERS +  username;
  }

  /**
   * Get the email address and name of the webmaster based on site admin settings.
   * Used for RSS feeds and other notifications.
   * @return email address and name of the webmaster.
   */
  public String getWebmaster() {
    return getSiteAdminEmail() + " (" + getSiteAdmin() + ")";
  }

    public String getFsPath() {
    return fsPath;
  }

  public String getContextPath() {
    return contextPath;
  }

  public boolean isSiteEnable() {
    return siteEnable;
  }

    public String getSiteBlog() {
    return siteBlog;
  }

  public boolean isSiteSearch() {
    return siteSearch;
  }

  public boolean isSiteDirectory() {
    return siteDirectory;
  }

  public boolean isSiteRss() {
    return siteRss;
  }

  public boolean isMailEnable() {
    return mailEnable;
  }

    public String getMailPass() {
    return mailPass;
  }

  public String getMailFrom() {
    return mailFrom;
  }

  public String getMailSubject() {
    return mailSubject;
  }

  public boolean isCommentEnable() {
    return commentEnable;
  }

  public boolean isCommentMailEnable() {
    return commentMailEnable;
  }

  public boolean isCommentEnableAnonymous() {
    return commentEnableAnonymous;
  }

  public int getTzOffset() {
    return tzOffset;
  }

  public String getTzName() {
    return tzName;
  }

  public void setTzName(final String tzName) {
    this.tzName = tzName;
  }

  public boolean isTzLocalize() {
    return tzLocalize;
  }

  public boolean isTzUseGMT() {
    return tzUseGMT;
  }

}
