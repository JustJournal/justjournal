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
package com.justjournal.services;

import static com.justjournal.core.Constants.*;

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.*;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryTagsRepository;
import com.justjournal.repository.LocationRepository;
import com.justjournal.repository.MoodRepository;
import com.justjournal.repository.TagRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.utility.DateConvert;
import com.justjournal.utility.HTMLUtil;
import com.justjournal.utility.StringUtil;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The MetaWeblog API interface for blogging. Similar to Blogger 1.0 API.
 *
 * @author Lucas Holt
 * @version $Id: MetaWeblog.java,v 1.6 2011/06/12 06:24:38 laffer1 Exp $
 *     <p>User: laffer1 Date: Apr 26, 2008 Time: 2:46:56 PM
 *     <p>TODO: Implement the media method
 */
@SuppressWarnings({"UnusedParameters"})
@Slf4j
@Component
public class MetaWeblog extends BaseXmlRpcService {

  @Autowired private EntryRepository entryRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private Login webLogin;

  @Autowired private LocationRepository locationDao;

  @Autowired private MoodRepository moodDao;

  @Autowired private TagRepository tagDao;

  @Autowired private Settings settings;

  @Autowired private EntryTagsRepository entryTagsRepository;

  @Autowired private EntryService entryService;

    public MetaWeblog(MarkdownService markdownService) {
      super(markdownService);
    }


  /**
   * Fetch the users personal information including their username, userid, email address and name.
   *
   * <p>The Blogger API defines nickname, userid, url, email, firstname and lastname here. We don't
   * track last name.
   *
   * @param appkey Not used but required by Blogger API
   * @param username username to authenticate/check
   * @param password the account secret
   * @return A HashMap of the users personal info or an error code as a hashmap
   */
  public HashMap<String, Serializable> getUsersInfo(
      String appkey, String username, String password) {
    final int userId;
    boolean blnError = false;
    final HashMap<String, Serializable> s = new HashMap<>();

    userId = webLogin.validate(username, password);
    if (userId < 1) blnError = true;

    if (!blnError)
      try {
        final User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
          throw new IllegalArgumentException("user");
        }

        s.put("nickname", user.getUsername());
        s.put("userid", userId);
        s.put("url", settings.getBaseUri() + PATH_USERS + user.getUsername());
        s.put("email", user.getUserContact().getEmail());
        s.put("firstname", user.getFirstName());
      } catch (final Exception e) {
        blnError = true;
        log.error( "Error finding blog user {}: {}", username, e.getMessage(), e);
      }

    if (blnError) {
      s.clear();
      s.put(FAULT_CODE, 4);
      s.put(FAULT_STRING, ERROR_USER_AUTH + username);
    }
    return s;
  }

  /**
   * Fetch the users blogs. JJ only supports 1 blog per user right now. Still this must be a list of
   * all blogs.
   *
   * <p>The Blogger API defines url, blogid and blogName.
   *
   * @param appkey Not used but required by Blogger API
   * @param username username to authenticate/check
   * @param password the account secret
   * @return A list of hash maps for each blog
   */
  public ArrayList<HashMap<Object, Serializable>> getUsersBlogs(
      String appkey, String username, String password) {
    int userId;
    boolean blnError = false;
    ArrayList<HashMap<Object, Serializable>> a = new ArrayList<>();
    HashMap<Object, Serializable> s = new HashMap<>();

    userId = webLogin.validate(username, password);
    if (userId < 1) blnError = true;

    if (!blnError)
      try {
        final User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
          throw new IllegalArgumentException("user");
        }

        final Journal journal = new ArrayList<>(user.getJournals()).get(0);

        s.put("url", settings.getBaseUri() + PATH_USERS + user.getUsername());
        s.put("blogid", userId);
        s.put("blogName", journal.getName());
      } catch (final Exception e) {
        blnError = true;
        log.error( "Error finding blog user {}: {}", username, e.getMessage(), e);
      }

    if (blnError) {
      s.clear();
      s.put(FAULT_CODE, 4);
      s.put(FAULT_STRING, ERROR_USER_AUTH + username);
    }

    a.add(s);

    return a;
  }

  /**
   * Create a new blog entry using the limited feautres of the blogger api.
   *
   * @param blogid Ignored since JJ supports 1 blog per user. Just the UID anyway.
   * @param username Username of blog owner.
   * @param password password of blog owner.
   * @param content The body/content of the blog (no subjects)
   * @param publish A boolean representing the publish state of the entry. JJ does not support
   *     drafts yet.
   * @return The entry id of the entry as a string or an error.
   */
  public Serializable newPost(
      String blogid, String username, String password, Map content, Boolean publish) {
    String result = "";
    final int userId;
    boolean blnError = false;
    final Entry et = new Entry();
    final HashMap<String, Serializable> s = new HashMap<>();

    userId = webLogin.validate(username, password);
    if (userId < 1) blnError = true;

    if (!blnError)
      try {
        final User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
          throw new IllegalArgumentException("user");
        }

        et.setUser(user);
        et.setDate(new java.util.Date());
        et.setSubject((String) content.get(TITLE_KEY));
        //   et.setSubject(StringUtil.replace(request.getParameter("subject"), '\'',
        // "\\\'"));
        et.setBody(StringUtil.replace((String) content.get(DESC_KEY), '\'', "\\\'"));
        // et.setMusic(StringUtil.replace(music, '\'', "\\\'"));
        et.setMusic("");
        et.setSecurity(Security.PUBLIC); // public
        et.setLocation(
            Objects.requireNonNull(locationDao.findById(0).orElse(null))); // not specified
        et.setMood(moodDao.findById(12)); // not specified
        et.setAutoFormat(PrefBool.N);
        et.setAllowComments(PrefBool.Y);
        et.setEmailComments(PrefBool.Y);
        et.setDraft(Boolean.TRUE.equals(publish) ? PrefBool.Y : PrefBool.N);

        entryRepository.save(et);
        result = Integer.toString(et.getId());
        log.debug("Result is: " + result);

        Journal journal = new ArrayList<>(user.getJournals()).get(0);

        if (!journal.isOwnerViewOnly() && journal.isPingServices()) {
          RestPing rp = new RestPing("http://ping.blo.gs/");
          rp.setName(journal.getName());
          rp.setUri(settings.getBaseUri() + PATH_USERS + user.getUsername());
          rp.setChangesURL(settings.getBaseUri() + PATH_USERS + user.getUsername() + "/rss");
          rp.ping();
        }

      } catch (final Exception e) {
        blnError = true;
        log.error( "Error finding blog user {}: {}", username, e.getMessage(), e);
      }

    if (blnError) {
      s.put(FAULT_CODE, 2041);
      s.put(FAULT_STRING, ERROR_USER_AUTH + username);
      return s;
    }

    return result;
  }

  /**
   * Delete a blog entry.
   *
   * <p>This is an extension to the Blogger API common on several other platforms including Six
   * Apart's stuff. http://www.sixapart.com/developers/xmlrpc/blogger_api/bloggerdeletepost.html
   *
   * @param appkey Not used, but for compatibility.
   * @param postid blog entry id
   * @param username owner of blog
   * @param password password for that blog
   * @param publish ignored.
   * @return true on success, hashmap on error.
   */
  public Serializable deletePost(
      String appkey, String postid, String username, String password, boolean publish) {
    final int userId;
    int eid = 0;

    userId = webLogin.validate(username, password);
    if (userId < 1) {
      log.error( "Error validating blog user {}", username);
      return error(ERROR_USER_AUTH + username);
    }

    try {
      eid = Integer.parseInt(postid);
      if (eid < 1) {
        log.debug( "Entry id invalid {}", postid);
        return error(ERROR_ENTRY_ID + postid);
      }
    } catch (final IllegalFormatException ex) {
      log.debug( "Entry id invalid {}", postid);
      return error(ERROR_ENTRY_ID + postid);
    }

    try {
      final Entry entry = entryRepository.findById(eid).orElse(null);
      if (entry == null) {
        log.warn( "Entry id invalid {}", postid);
        return error(ERROR_ENTRY_ID + postid);
      }

      if (entry.getUser().getId() == userId) entryRepository.deleteById(eid);
    } catch (final Exception e) {
      log.error("Unable to delete entry", e);
      return error("Unable to delete entry id " + postid);
    }

    return true; /* ie true per spec */
  }

  /**
   * Modify a blog entry using the limited feautres of the blogger api.
   *
   * @param postid entry id as a string
   * @param username Username of blog owner.
   * @param password password of blog owner.
   * @param content The body/content of the blog (no subjects)
   * @param publish A boolean representing the publish state of the entry. JJ does not support
   *     drafts yet.
   * @return The entry id of the entry as a string or an error.
   */
  public Serializable editPost(
      String postid, String username, String password, Map content, Boolean publish) {
    int userId;
    boolean blnError = false;

    int eid = 0;

    userId = webLogin.validate(username, password);
    if (userId < 1) blnError = true;

    try {
      eid = Integer.parseInt(postid);
    } catch (final IllegalFormatException ex) {
      return error(ERROR_ENTRY_ID + postid);
    }

    if (!blnError && eid > 0) {
      try {
        /* we're just updating the content aka body as this is the only thing the protocol supports. */
        final Entry et2 = entryRepository.findById(eid).orElse(null);
        if (et2 == null) {
          return error(ERROR_ENTRY_ID + postid);
        }
        if (et2.getUser().getId() == userId) {
          et2.setSubject((String) content.get(TITLE_KEY));
          et2.setBody(StringUtil.replace((String) content.get(DESC_KEY), '\'', "\\\'"));
          et2.setDraft(Boolean.TRUE.equals(publish) ? PrefBool.Y : PrefBool.N);
          /* TODO: add date edit support */
          entryRepository.save(et2);

          String[] tags = (String[]) content.get("categories");
          for (final String tag : tags) {
            final Tag tag1 = tagDao.findByName(tag);

            final EntryTag entryTags = new EntryTag();
            entryTags.setTag(tag1);
            entryTags.setEntry(et2);

            entryTagsRepository.save(entryTags);
          }
        }
      } catch (final Exception e) {
        blnError = true;
        log.debug(e.getMessage());
      }
    }

    if (eid < 1) {
      return error(ERROR_ENTRY_ID + postid);
    } else if (blnError) {
      return error(ERROR_USER_AUTH + username);
    }
    return true; /* ie true per spec */
  }

  /**
   * Retrieve recent posts from the blog in chronological order.
   *
   * <p>Sample request:
   *
   * <p>
   *
   * <p><?xml version="1.0" encoding="UTF-8"?> <methodCall>
   * <methodName>metaWeblog.getRecentPosts</methodName> <params>
   * <param><value><int>178663</int></value></param>
   * <param><value><string>yourUsername</string></value></param>
   * <param><value><string>somePassword</string></value></param>
   * <param><value><int>4</int></value></param> </params> </methodCall>
   *
   * <p>Sample response:
   *
   * <p><?xml version="1.0" encoding="UTF-8"?> 2 <methodResponse> 3 <params> 4
   * <param><value><array><data><value> 5 <struct> 6 <member> 7 <name>link</name> 8
   * <value><string>http://typekeytest111.typepad.com/my_weblog/2005/07/one_more.html</string></value>
   * 9 </member> 10 <member> 11 <name>permaLink</name> 12
   * <value><string>http://typekeytest111.typepad.com/my_weblog/2005/07/one_more.html</string></value>
   * 13 </member> 14 <member> 15 <name>userid</name> 16 <value><string>28376</string></value> 17
   * </member> 18 <member> 19 <name>mt_allow_pings</name> 20 <value><int>0</int></value> 21
   * </member> 22 <member> 23 <name>mt_allow_comments</name> 24 <value><int>1</int></value> 25
   * </member> 26 <member> 27 <name>description</name> 28 <value><string/></value> 29 </member> 30
   * <member> 31 <name>mt_convert_breaks</name> 32 <value><string>0</string></value> 33 </member> 34
   * <member> 35 <name>postid</name> 36 <value><string>5423957</string></value> 37 </member> 38
   * <member> 39 <name>mt_excerpt</name> 40 <value><string/></value> 41 </member> 42 <member> 43
   * <name>mt_keywords</name> 44 <value><string/></value> 45 </member> 46 <member> 47
   * <name>title</name> 48 <value><string>One more!</string></value> 49 </member> 50 <member> 51
   * <name>mt_text_more</name> 52 <value><string/></value> 53 </member> 54 <member> 55
   * <name>dateCreated</name> 56
   * <value><dateTime.iso8601>2005-07-02T02:37:04Z</dateTime.iso8601></value> 57 </member> 58
   * </struct></value></data></array></value> 59 </param> 60 </params> 61 </methodResponse>
   *
   * <p>URI of example:
   * http://www.sixapart.com/developers/xmlrpc/blogger_api/bloggergetrecentposts.html
   *
   * @param blogid Unused, but required by Blogger API
   * @param username user to check
   * @param password password for user
   * @param numberOfPosts The max number of posts to get. (we should match this exactly)
   * @return An arraylist of posts or a hashmap of error info.
   */
  public Cloneable getRecentPosts(
      String blogid, String username, String password, int numberOfPosts) {
    final ArrayList<HashMap<Object, Serializable>> arr = new ArrayList<>(numberOfPosts);
    final Collection<Entry> total;
    final int userId = webLogin.validate(username, password);
    if (userId < 1) {
      return error(ERROR_USER_AUTH + username);
    }

    total = entryRepository.findByUsername(username);

    Iterator<Entry> it = total.iterator();

    for (int i = 0; i < numberOfPosts; i++)
      if (it.hasNext()) {
        HashMap<Object, Serializable> entry = new HashMap<>();
        Entry e = it.next();
        String body = convertBody(e.getFormat(), e.getBody());

        entry.put(
            "link",
                settings.getBlogBaseUrl(e.getUser().getUsername()) + "/entry/" + e.getId());
        entry.put(
            "permaLink",
                settings.getBlogBaseUrl(e.getUser().getUsername())+ "/entry/" + e.getId());
        entry.put("userid", Integer.toString(e.getUser().getId()));
        entry.put("mt_allow_pings", 0); /* TODO: on or off? */
        entry.put("mt_allow_comments", 1); /* TODO: on or off? */

        entry.put("description", body);
        entry.put("content", body);
        entry.put("mt_convert_breaks", 0); /* TODO: research what these are... */
        entry.put("postid", Integer.toString(e.getId()));
        entry.put("mt_excerpt", body);
        entry.put("mt_keywords", "");
        entry.put("title", e.getSubject());
        entry.put("mt_text_more", "");
        entry.put("dateCreated", DateConvert.encode8601(e.getDate()));
        Collection<String> list = new ArrayList<>();
        for (EntryTag tag : e.getTags()) {
          list.add(tag.getTag().getName());
        }
        String[] str = list.toArray(new String[list.size()]);
        entry.put("categories", str); // according to microsoft it's a string array
        arr.add(entry);
      }

    return arr;
  }

  /**
   * Get a single blog entry by the post id (aka entry id)
   *
   * @param postid the entry id to fetch
   * @param username the username of the entry
   * @param password the password of the entry
   * @return a signle entry as a hashmap for consumption by xml-rpc
   */
  public HashMap<String, Serializable> getPost(String postid, String username, String password) {
    final int userId;
    final HashMap<String, Serializable> entry = new HashMap<>();
    final Entry e;

    userId = webLogin.validate(username, password);
    if (userId < 1) {
      log.warn( "Unable to validate user {}", username);
      return error(ERROR_USER_AUTH + username);
    }

    e = entryRepository.findById(Integer.parseInt(postid)).orElse(null);

    if (e == null) {
      log.debug( "Entry id invalid {}", postid);
      return error(ERROR_ENTRY_ID + postid);
    }

    if (e.getUser().getId() != userId) {
      log.warn( "user does not own entry {} blog user {}", postid, username);
      return error(ERROR_USER_AUTH + username);
    }

    String body = convertBody(e.getFormat(), e.getBody());

    entry.put(
        "link",
            settings.getBlogBaseUrl(e.getUser().getUsername()) + "/entry/" + e.getId());
    entry.put(
        "permaLink",
            settings.getBlogBaseUrl(e.getUser().getUsername()) + "/entry/" + e.getId());
    entry.put("userid", Integer.toString(e.getUser().getId()));
    entry.put("mt_allow_pings", 0); /* TODO: on or off? */
    entry.put("mt_allow_comments", 1); /* TODO: on or off? */
    entry.put("description", body);
    entry.put("content", body);
    entry.put("mt_convert_breaks", 0); /* TODO: research what these are... */
    entry.put("postid", Integer.toString(e.getId()));
    entry.put("mt_excerpt", body);
    entry.put("mt_keywords", "");
    entry.put("title", e.getSubject());
    entry.put("mt_text_more", "");
    entry.put("dateCreated", DateConvert.encode8601(e.getDate()));
    Collection<String> list = new ArrayList<>();
    for (EntryTag entryTag : e.getTags()) {
      list.add(entryTag.getTag().getName());
    }
    String[] str = list.toArray(new String[0]);
    entry.put("categories", str); // according to microsoft it's a string array

    return entry;
  }

  public Cloneable getCategories(
      final String blogid, final String username, final String password) {
    final int userId;
    final ArrayList<HashMap<Object, Serializable>> arr = new ArrayList<>();

    userId = webLogin.validate(username, password);
    if (userId < 1) {
      log.warn( "Unable to validate user {}", username);
      return error(ERROR_USER_AUTH + username);
    }

    try {
      entryService
          .getEntryTags(username)
          .map(
              curtag -> {
                final HashMap<Object, Serializable> entry = new HashMap<>();
                entry.put("description", curtag.getName());
                entry.put("title", curtag.getName());
                arr.add(entry);

                return curtag;
              })
          .subscribe();
    } catch (final ServiceException se) {
      log.error("Could not load categories {}", se.getMessage(), se);
    }
    return arr;
  }
}
