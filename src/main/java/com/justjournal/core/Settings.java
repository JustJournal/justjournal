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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Global settings for the site. Any changes here will effect the entire site. User preferences are
 * separate and any negative setting here will supersede per user settings like commentMailEnable =
 * false would disable email comments even if Users have them turned on.
 *
 * <p>The values are read from the settings table at startup and again whenever {@link #reload()}
 * is called. A reload builds a complete new set of values before swapping it in, so readers never
 * see a half loaded mix.
 *
 * @author Lucas Holt
 */
@Slf4j
@Component
public class Settings {

  private final SettingsRepository settingsDao;

  private volatile Values values = new Values();

  @Autowired
  public Settings(SettingsRepository settingsDao) {
    this.settingsDao = settingsDao;
    reload();
  }

  /**
   * Read every row of the settings table again. Settings without a row keep their defaults. If the
   * table can't be read at all the current values are kept; a value that fails to parse stops the
   * read, keeping what was read before it.
   */
  public synchronized void reload() {
    final Iterable<com.justjournal.model.Settings> rows;
    try {
      rows = settingsDao.findAll(Sort.by("name"));
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      return;
    }

    final Values next = new Values();
    try {
      for (com.justjournal.model.Settings set : rows) {
        final String value = set.getValue();
        final String name = set.getName();

        if (name == null) {
          log.warn("setting.name is null");
          continue;
        }
        if (value == null) {
          log.warn("setting.value is null");
          continue;
        }

        next.apply(name, value);
      }
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    values = next;
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

  public String getBaseUri() {
    return values.baseUri;
  }

  public String getFsPath() {
    return values.fsPath;
  }

  public String getContextPath() {
    return values.contextPath;
  }

  public boolean isSiteEnable() {
    return values.siteEnable;
  }

  public String getSiteName() {
    return values.siteName;
  }

  public String getSiteAdmin() {
    return values.siteAdmin;
  }

  public String getSiteAdminEmail() {
    return values.siteAdminEmail;
  }

  public String getSiteBlog() {
    return values.siteBlog;
  }

  public boolean isSiteSearch() {
    return values.siteSearch;
  }

  public boolean isSiteDirectory() {
    return values.siteDirectory;
  }

  public boolean isSiteRss() {
    return values.siteRss;
  }

  public boolean isMailEnable() {
    return values.mailEnable;
  }

  public String getMailHost() {
    return values.mailHost;
  }

  public int getMailPort() {
    return values.mailPort;
  }

  public String getMailUser() {
    return values.mailUser;
  }

  public String getMailPass() {
    return values.mailPass;
  }

  public String getMailFrom() {
    return values.mailFrom;
  }

  public String getMailSubject() {
    return values.mailSubject;
  }

  public boolean isCommentEnable() {
    return values.commentEnable;
  }

  public boolean isCommentMailEnable() {
    return values.commentMailEnable;
  }

  public boolean isCommentEnableAnonymous() {
    return values.commentEnableAnonymous;
  }

  public int getTzOffset() {
    return values.tzOffset;
  }

  public String getTzName() {
    return values.tzName;
  }

  /** Changes the time zone name until the next {@link #reload()}. */
  public void setTzName(final String tzName) {
    values.tzName = tzName;
  }

  public boolean isTzLocalize() {
    return values.tzLocalize;
  }

  public boolean isTzUseGMT() {
    return values.tzUseGMT;
  }

  public boolean isUserAllowNew() {
    return values.userAllowNew;
  }

  /** One complete set of values, with the defaults used for any setting the table lacks. */
  private static final class Values {

    /* paths */
    private String baseUri = "http://localhost:8080/";
    private String fsPath = ""; // file storage path.
    private String contextPath = "";

    /* site settings */
    private boolean siteEnable = true;
    private String siteName = "";
    private String siteAdmin = "";
    private String siteAdminEmail = "";
    private String siteBlog = ""; // blog about site activities, updates.
    private boolean siteSearch = true; // enable site search feature
    private boolean siteDirectory = true; // member directory
    private boolean siteRss = true; // recent blog posts

    /* e-mail */
    private boolean mailEnable = true;
    private String mailHost = "localhost";
    private int mailPort = 25;
    private String mailUser = "";
    private String mailPass = "";
    private String mailFrom = siteAdminEmail;
    private String mailSubject = siteName + " Notification";

    /* comments */
    private boolean commentEnable = true;
    private boolean commentMailEnable = true;
    private boolean commentEnableAnonymous = false;

    /* time */
    private volatile String tzName = "EST";
    private int tzOffset = -5;
    private boolean tzLocalize = true;
    private boolean tzUseGMT = true;

    /* Users */
    private boolean userAllowNew = true;

    private void apply(final String name, final String value) {
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
        commentEnableAnonymous = value.equalsIgnoreCase("true");
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
  }
}
