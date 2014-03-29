/*
Copyright (c) 2007, Lucas Holt
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

  Redistributions of source code must retain the above copyright notice, this list of
  conditions and the following disclaimer.

  Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or other
  materials provided with the distribution.

  Neither the name of the Just Journal nor the names of its contributors
  may be used to endorse or promote products derived from this software without
  specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

package com.justjournal.google;

import com.justjournal.Login;
import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import com.justjournal.repository.*;
import com.justjournal.restping.BasePing;
import com.justjournal.restping.IceRocket;
import com.justjournal.restping.TechnoratiPing;
import com.justjournal.utility.DateConvert;
import com.justjournal.utility.HTMLUtil;
import com.justjournal.utility.StringUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A blogger 1 compatible interface exposed by XML-RPC
 * <p/>
 * http://www.blogger.com/developers/api/1_docs/
 * <p/>
 * Methods defined in Blogger 1.0 API: blogger.newPost: Makes a new post to a designated blog. Optionally, will publish
 * the blog after making the post. blogger.editPost: Edits a given post. Optionally, will publish the blog after making
 * the edit. blogger.getUsersBlogs: Returns information on all the blogs a given user is a member of.
 * blogger.getUserInfo: Authenticates a user and returns basic user info (name, email, userid, etc.)
 * blogger.getTemplate: Returns the main or archive index template of a given blog. blogger.setTemplate: Edits the main
 * or archive index template of a given blog.
 */
@SuppressWarnings({"UnusedParameters"})
@Component
public class Blogger {

    private static final Logger log = Logger.getLogger(Blogger.class);

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private Login webLogin;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MoodDao moodDao;

    @Autowired
    private LocationDao locationDao;

    @Autowired
    private SecurityDao securityDao;

    /**
     * Fetch the users personal information including their username, userid, email address and name.
     * <p/>
     * The Blogger API defines nickname, userid, url, email, firstname and lastname here.  We don't track last name.
     *
     * @param appkey   Not used but required by Blogger API
     * @param username username to authenticate/check
     * @param password the account secret
     * @return A HashMap of the users personal info or an error code as a hashmap
     */
    public HashMap<String, Serializable> getUsersInfo(String appkey, String username, String password) {
        int userId;
        boolean blnError = false;
        HashMap<String, Serializable> s = new HashMap<String, Serializable>();


        if (!StringUtil.lengthCheck(username, 3, Login.USERNAME_MAX_LENGTH)) {
            blnError = true;
        }

        if (!StringUtil.lengthCheck(password, 5, Login.PASSWORD_MAX_LENGTH)) {
            blnError = true;
        }

        userId = webLogin.validate(username, password);
        if (userId < 1)
            blnError = true;

        if (!blnError)
            try {
                User user = userRepository.findOne(userId);

                s.put("nickname", user.getUsername());
                s.put("userid", userId);
                s.put("url", "http://www.justjournal.com/users/" + user.getUsername());
                s.put("email", user.getUserContact().getEmail());
                s.put("firstname", user.getFirstName());
            } catch (Exception e) {
                blnError = true;
                log.debug(e.getMessage());
            }

        if (blnError) {
            s.clear();
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);
        }


        return s;
    }


    /**
     * Fetch the users blogs.  JJ only supports 1 blog per user right now.  Still this must be a list of all blogs.
     * <p/>
     * The Blogger API defines url, blogid and blogName.
     *
     * @param appkey   Not used but required by Blogger API
     * @param username username to authenticate/check
     * @param password the account secret
     * @return A list of hash maps for each blog
     */
    public ArrayList<HashMap<Object, Serializable>> getUsersBlogs(String appkey, String username, String password) {
        int userId;
        boolean blnError = false;
        ArrayList<HashMap<Object, Serializable>> a = new ArrayList<HashMap<Object, Serializable>>();
        HashMap<Object, Serializable> s = new HashMap<Object, Serializable>();


        if (!StringUtil.lengthCheck(username, 3, 15)) {
            blnError = true;
        }

        if (!StringUtil.lengthCheck(password, 5, 18)) {
            blnError = true;
        }

        userId = webLogin.validate(username, password);
        if (userId < 1)
            blnError = true;

        if (!blnError)
            try {
                User user = userRepository.findOne(userId);

                s.put("url", "http://www.justjournal.com/users/" + user.getUsername());
                s.put("blogid", userId);
                s.put("blogName", user.getUserPref().getJournalName());
            } catch (Exception e) {
                blnError = true;
                log.debug(e.getMessage());
            }

        if (blnError) {
            s.clear();
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);
        }

        a.add(s);

        return a;
    }


    /**
     * Create a new blog entry using the limited feautres of the blogger api.
     *
     * @param appkey   Ignored but required
     * @param blogid   Ignored since JJ supports 1 blog per user.  Just the UID anyway.
     * @param username Username of blog owner.
     * @param password password of blog owner.
     * @param content  The body/content of the blog (no subjects)
     * @param publish  A boolean representing the publish state of the entry.  JJ does not support drafts yet.
     * @return The entry id of the entry as a string or an error.
     */
    public Serializable newPost(String appkey, String blogid, String username, String password, String content, Boolean publish) {
        String result = "";
        int userId;
        boolean blnError = false;
        final Entry et = new Entry();
        Entry et2;
        HashMap<String, Serializable> s = new HashMap<String, Serializable>();

        if (!StringUtil.lengthCheck(username, 3, Login.USERNAME_MAX_LENGTH)) {
            blnError = true;
        }

        if (!StringUtil.lengthCheck(password, 5, Login.PASSWORD_MAX_LENGTH)) {
            blnError = true;
        }

        userId = webLogin.validate(username, password);
        if (userId < 1)
            blnError = true;

        if (!blnError)
            try {
                com.justjournal.model.User user = userRepository.findOne(userId);
                et.setUser(user);
                et.setDate(new java.util.Date());

                /* Allow html style title tag to set subject, used by many clients */
                String subject;
                final Pattern p = Pattern.compile("(<title>)(.*?)(</title>)");
                final Matcher m = p.matcher(content);
                if (m.find()) {
                    subject = m.group(2);
                    content = m.replaceAll("");
                } else
                    subject = "";
                et.setSubject(StringUtil.replace(subject, '\'', "\\\'"));

                et.setBody(StringUtil.replace(content, '\'', "\\\'"));

                /* Allow html style music tag to set music, used by few clients */
                String music;
                final Pattern p2 = Pattern.compile("(<music>)(.*?)(</music>)");
                final Matcher m2 = p2.matcher(content);
                if (m2.find()) {
                    music = m2.group(2);
                    m.replaceAll("");
                } else
                    music = "";
                et.setMusic(StringUtil.replace(music, '\'', "\\\'"));

                et.setSecurity(securityDao.findOne(2));   // public
                et.setLocation(locationDao.findOne(0)); // not specified
                et.setMood(moodDao.findOne(12));    // not specified
                et.setAutoFormat(PrefBool.Y);
                et.setAllowComments(PrefBool.Y);
                et.setEmailComments(PrefBool.Y);
                et.setUser(userRepository.findOne(userId));

                entryRepository.save(et);
                et2 = entryRepository.findOne(et.getId()); // TODO: this is wrong.
                result = Integer.toString(et2.getId());

                if (user.getUserPref().getOwnerViewOnly() == PrefBool.N) {
                    log.debug("Ping weblogs");
                    /* WebLogs, Google, blo.gs */
                    BasePing rp = new BasePing("http://rpc.weblogs.com/pingSiteForm");
                    rp.setName(user.getUserPref().getJournalName());
                    rp.setUri("http://www.justjournal.com/" + "users/" + user.getUsername());
                    rp.setChangesURL("http://www.justjournal.com" + "/users/" + user.getUsername() + "/rss");
                    rp.ping();
                    rp.setPingUri("http://blogsearch.google.com/ping");
                    rp.ping();
                    rp.setPingUri("http://ping.blo.gs/");
                    rp.ping();

                    /* Technorati */
                    TechnoratiPing rpt = new TechnoratiPing();
                    rpt.setName(user.getUserPref().getJournalName());
                    rpt.setUri("http://www.justjournal.com/" + "users/" + user.getUsername());
                    rpt.ping();

                    /* IceRocket */
                    IceRocket ice = new IceRocket();
                    ice.setName(user.getUserPref().getJournalName());
                    ice.setUri("http://www.justjournal.com/" + "users/" + user.getUsername());
                    ice.ping();
                }

            } catch (Exception e) {
                blnError = true;
                log.debug(e.getMessage());
            }

        if (blnError) {
            s.clear();
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);
            return s;
        }

        return result;
    }


    /**
     * Delete a blog entry.
     * <p/>
     * This is an extension to the Blogger API common on several other platforms including Six Apart's stuff.
     * http://www.sixapart.com/developers/xmlrpc/blogger_api/bloggerdeletepost.html
     *
     * @param appkey   Not used, but for compatibility.
     * @param postid   blog entry id
     * @param username owner of blog
     * @param password password for that blog
     * @param publish  ignored.
     * @return true on success, hashmap on error.
     */
    public Serializable deletePost(String appkey, String postid, String username, String password, boolean publish) {
        int userId;
        boolean blnError = false;
        HashMap<String, Serializable> s = new HashMap<String, Serializable>();

        int eid = 0;

        if (!StringUtil.lengthCheck(username, 3, 15)) {
            blnError = true;
        }

        if (!StringUtil.lengthCheck(password, 5, 18)) {
            blnError = true;
        }

        userId = webLogin.validate(username, password);
        if (userId < 1)
            blnError = true;

        try {
            eid = Integer.parseInt(postid);
        } catch (IllegalFormatException ex) {
            blnError = true;
        }

        if (!blnError && eid > 0) {
            try {
                Entry entry = entryRepository.findOne(eid);
                if (entry.getUser().getId() == userId)
                    entryRepository.delete(eid);
            } catch (Exception e) {
                blnError = true;
                log.debug(e.getMessage());
            }
        }

        if (eid < 1) {
            s.put("faultCode", 4);
            s.put("faultString", "Invalid entry id " + postid);

        } else if (blnError) {
            s.clear();
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);

        } else {
            return true; /* ie true per spec */
        }

        return s;
    }

    /**
     * Modify a blog entry using the limited feautres of the blogger api.
     *
     * @param appkey   Ignored but required
     * @param postid   entry id as a string
     * @param username Username of blog owner.
     * @param password password of blog owner.
     * @param content  The body/content of the blog (no subjects)
     * @param publish  A boolean representing the publish state of the entry.  JJ does not support drafts yet.
     * @return The entry id of the entry as a string or an error.
     */
    public Serializable editPost(String appkey, String postid, String username, String password, String content, Boolean publish) {
        int userId;
        boolean blnError = false;
        HashMap<String, Serializable> s = new HashMap<String, Serializable>();

        int eid = 0;

        if (!StringUtil.lengthCheck(username, 3, 15)) {
            blnError = true;
        }

        if (!StringUtil.lengthCheck(password, 5, 18)) {
            blnError = true;
        }

        userId = webLogin.validate(username, password);
        if (userId < 1)
            blnError = true;

        try {
            eid = Integer.parseInt(postid);
        } catch (IllegalFormatException ex) {
            blnError = true;
        }

        if (!blnError && eid > 0) {
            try {
                /* we're just updating the content aka body as this is the
           only thing the protocol supports. */
                Entry et2 = entryRepository.findOne(eid);
                if (userId == et2.getUser().getId()) {
                    et2.setBody(StringUtil.replace(content, '\'', "\\\'"));
                    entryRepository.save(et2);
                } else blnError = true;
            } catch (Exception e) {
                blnError = true;
                log.debug(e.getMessage());
            }
        }

        if (eid < 1) {
            s.put("faultCode", 4);
            s.put("faultString", "Invalid entry id " + postid);

        } else if (blnError) {
            s.clear();
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);

        } else {
            return true; /* true per spec */
        }

        return s;
    }

    /**
     * Retrieve recent posts from the blog in chronological order.
     * <p/>
     * Sample request:
     * <p/>
     * <p/>
     * <?xml version="1.0" encoding="UTF-8"?> <methodCall> <methodName>metaWeblog.getRecentPosts</methodName> <params>
     * <param><value><int>178663</int></value></param> <param><value><string>yourUsername</string></value></param>
     * <param><value><string>somePassword</string></value></param> <param><value><int>4</int></value></param> </params>
     * </methodCall>
     * <p/>
     * Sample response:
     * <p/>
     * <?xml version="1.0" encoding="UTF-8"?> 2	<methodResponse> 3	  <params> 4	    <param><value><array><data><value>
     * 5	      <struct> 6	        <member> 7	          <name>link</name> 8 <value><string>http://typekeytest111.typepad.com/my_weblog/2005/07/one_more.html</string></value>
     * 9 </member> 10	        <member> 11	          <name>permaLink</name> 12 <value><string>http://typekeytest111.typepad.com/my_weblog/2005/07/one_more.html</string></value>
     * 13 </member> 14	        <member> 15	          <name>userid</name> 16 <value><string>28376</string></value> 17
     * </member> 18	        <member> 19 <name>mt_allow_pings</name> 20 <value><int>0</int></value> 21 </member> 22
     * <member> 23 <name>mt_allow_comments</name> 24	          <value><int>1</int></value> 25 </member> 26 <member> 27
     * <name>description</name> 28	          <value><string/></value> 29 </member> 30	        <member> 31
     * <name>mt_convert_breaks</name> 32	          <value><string>0</string></value> 33	        </member> 34 <member>
     * 35	          <name>postid</name> 36 <value><string>5423957</string></value> 37 </member> 38 <member> 39
     * <name>mt_excerpt</name> 40 <value><string/></value> 41 </member> 42 <member> 43 <name>mt_keywords</name> 44
     * <value><string/></value> 45 </member> 46 <member> 47	          <name>title</name> 48 <value><string>One
     * more!</string></value> 49 </member> 50	        <member> 51 <name>mt_text_more</name> 52 <value><string/></value>
     * 53 </member> 54	        <member> 55 <name>dateCreated</name> 56 <value><dateTime.iso8601>2005-07-02T02:37:04Z</dateTime.iso8601></value>
     * 57 </member> 58 </struct></value></data></array></value> 59	    </param> 60	  </params> 61	</methodResponse>
     * <p/>
     * URI of example: http://www.sixapart.com/developers/xmlrpc/blogger_api/bloggergetrecentposts.html
     *
     * @param appkey        Unused, but required by Blogger API
     * @param blogid        Unused, but required by Blogger API
     * @param username      user to check
     * @param password      password for user
     * @param numberOfPosts The max number of posts to get.  (we should match this exactly)
     * @return An arraylist of posts or a hashmap of error info.
     */
    public Cloneable getRecentPosts(String appkey, String blogid, String username, String password, int numberOfPosts) {
        ArrayList<HashMap<Object, Serializable>> arr = new ArrayList<HashMap<Object, Serializable>>(numberOfPosts);
        Collection<Entry> total;
        Boolean blnError = false;
        int userId;
        HashMap<String, Serializable> s = new HashMap<String, Serializable>();

        if (!StringUtil.lengthCheck(username, 3, Login.USERNAME_MAX_LENGTH)) {
            blnError = true;
        }

        if (!StringUtil.lengthCheck(password, 5, Login.PASSWORD_MAX_LENGTH)) {
            blnError = true;
        }

        userId = webLogin.validate(username, password);
        if (blnError || userId < 1) {
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);
            return s;
        }

        total = entryRepository.findByUsername(username);

        Iterator<Entry> it = total.iterator();

        for (int i = 0; i < numberOfPosts; i++)
            if (it.hasNext()) {
                HashMap<Object, Serializable> entry = new HashMap<Object, Serializable>();
                Entry e = it.next();
                entry.put("link", "http://www.justjournal.com/users/" + username + "/entry/" + e.getId());
                entry.put("permaLink", "http://www.justjournal.com/users/" + username + "/entry/" + e.getId());
                entry.put("userid", Integer.toString(e.getUser().getId()));
                entry.put("mt_allow_pings", 0);     /* TODO: on or off? */
                entry.put("mt_allow_comments", 1);  /* TODO: on or off? */
                entry.put("description", HTMLUtil.textFromHTML(e.getBody()));  /* TODO: change format? no html */
                entry.put("content", HTMLUtil.textFromHTML(e.getBody()));  /* TODO: change format? no html */
                entry.put("mt_convert_breaks", 0);                 /* TODO: research what these are... */
                entry.put("postid", Integer.toString(e.getId()));
                entry.put("mt_excerpt", HTMLUtil.textFromHTML(e.getBody()));
                entry.put("mt_keywords", "");
                entry.put("title", e.getSubject());
                entry.put("mt_text_more", "");
                entry.put("dateCreated", DateConvert.encode8601(e.getDate()));
                arr.add(entry);
            }

        return arr;
    }

    /**
     * Get a single blog entry by the post id (aka entry id)
     *
     * @param appkey   For google compatibility.. not used
     * @param postid   the entry id to fetch
     * @param username the username of the entry
     * @param password the password of the entry
     * @return a signle entry as a hashmap for consumption by xml-rpc
     */
    public HashMap<Object, Serializable> getPost(String appkey, String postid, String username, String password) {
        Boolean blnError = false;
        int userId;
        HashMap<Object, Serializable> s = new HashMap<Object, Serializable>();
        HashMap<Object, Serializable> entry = new HashMap<Object, Serializable>();
        Entry e;

        if (!StringUtil.lengthCheck(username, 3, 15)) {
            blnError = true;
        }

        if (!StringUtil.lengthCheck(password, 5, 18)) {
            blnError = true;
        }

        userId = webLogin.validate(username, password);
        if (blnError || userId < 1) {
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);
            return s;
        }

        e = entryRepository.findOne(Integer.parseInt(postid));
        if (userId != e.getUser().getId()) {
            s.put("faultCode", 4);
            s.put("faultString", "User authentication failed: " + username);
            return s;
        }

        entry.put("link", "http://www.justjournal.com/users/" + e.getUser().getUsername() + "/entry/" + e.getId());
        entry.put("permaLink", "http://www.justjournal.com/users/" + e.getUser().getUsername() + "/entry/" + e.getId());
        entry.put("userid", Integer.toString(userId));
        entry.put("mt_allow_pings", 0);     /* TODO: on or off? */
        entry.put("mt_allow_comments", 1);  /* TODO: on or off? */
        entry.put("description", HTMLUtil.textFromHTML(e.getBody()));  /* TODO: change format? */
        entry.put("content", HTMLUtil.textFromHTML(e.getBody()));  /* TODO: change format? */
        entry.put("mt_convert_breaks", 0);                 /* TODO: research what these are... */
        entry.put("postid", Integer.toString(e.getId()));
        entry.put("mt_excerpt", HTMLUtil.textFromHTML(e.getBody()));
        entry.put("mt_keywords", "");
        entry.put("title", e.getSubject());
        entry.put("mt_text_more", "");
        entry.put("dateCreated", DateConvert.encode8601(e.getDate()));

        return entry;
    }
}
