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

/** @author Lucas Holt */
public class Constants {

  private Constants() {
    super();
  }

  public static final char endl = '\n';
  public static final int DEFAULT_BUFFER_SIZE = 8192;

  public static final String MIME_TYPE_RSS = "application/rss+xml";

  public static final String JJ_LOGIN_OK = "JJ.LOGIN.OK";
  public static final String JJ_LOGIN_FAIL = "JJ.LOGIN.FAIL";
  public static final String JJ_LOGIN_NONE = "JJ.LOGIN.NONE";

  public static final String LOGIN_ATTRNAME = "auth.user";
  public static final String LOGIN_ATTRID = "auth.uid";

  public static final int USERNAME_MAX_LENGTH = 50;
  public static final int USERNAME_MIN_LENGTH = 3;
  public static final int PASSWORD_MIN_LENGTH = 5;
  public static final int PASSWORD_MAX_LENGTH = 50;
  public static final int BAD_USER_ID = 0;

  public static final String HEADER_USER_AGENT = "User-Agent";
  public static final String HEADER_LAST_MODIFIED = "Last-Modified";
  public static final String HEADER_EXPIRES = "Expires";
  public static final String HEADER_CACHE_CONTROL = "Cache-Control";
  public static final String HEADER_PRAGMA = "Pragma";
  public static final String HEADER_ACCEPT_ALL = "Accept=*/*";

  public static final String PARAM_SIZE = "size";
  public static final String PARAM_PAGE = "page";
  public static final String PARAM_ID = "id";
  public static final String PARAM_ENTRY_ID = "entryId";
  public static final String PARAM_USERNAME = "username";
  public static final String PARAM_PASSWORD = "password";
  public static final String PARAM_MOBILE = "mobile";
  public static final String PARAM_DASHBOARD = "dash";
  public static final String PARAM_SECURITY = "security";
  public static final String PARAM_LOCATION = "location";
  public static final String PARAM_MOOD = "mood";
  public static final String PARAM_MUSIC = "music";
  public static final String PARAM_TRACKBACK = "trackback";
  public static final String PARAM_AUTO_FORMAT = "aformat";
  public static final String PARAM_TAGS = "tags";
  public static final String PARAM_ALLOW_COMMENT = "allow_comment";
  public static final String PARAM_EMAIL_COMMENT = "email_comment";
  public static final String PARAM_DATE = "date";
  public static final String PARAM_TIME = "time";
  public static final String PARAM_YEAR = "year";
  public static final String PARAM_TITLE = "title";

  public static final String PATH_USERS = "users/";
  public static final String PATH_ENTRY = "/entry/";

  public static final String ERR_INVALID_LOGIN = "The login timed out or is invalid.";
  public static final String ERR_ADD_USER = "Could not add user";
}
