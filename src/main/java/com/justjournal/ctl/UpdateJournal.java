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
package com.justjournal.ctl;

import static com.justjournal.core.Constants.*;

import com.justjournal.ErrorPage;
import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.*;
import com.justjournal.model.api.EntryTo;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.LocationRepository;
import com.justjournal.repository.MoodRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.BingService;
import com.justjournal.services.RestPing;
import com.justjournal.services.TrackbackService;
import com.justjournal.utility.HTMLUtil;
import com.justjournal.utility.Spelling;
import com.justjournal.utility.StringUtil;

import java.io.Serial;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Adds journal entries to database.
 *
 * <p>Optionally spell checks entries and returns the user to the edit get to make changes.
 *
 * @author Lucas Holt
 * @version $Id: UpdateJournal.java,v 1.33 2012/07/04 18:49:20 laffer1 Exp $
 * @since 1.0 Created on March 23, 2003, 12:42 PM
 *     <p>1.4.1 Introduced some bug fixes with null handling.
 *     <p>1.4 Changed default behavior for allow comments flag. Assumes the user will uncheck a box
 *     to disable comments. auto formatting was changed in a similar manner for usability.
 */
@Slf4j
@Component
public class UpdateJournal extends HttpServlet {

  @Serial
  private static final long serialVersionUID = -6905389941955230503L;

  private static final String PARAM_CHECKED = "checked";
  private static final String HEADER_EXPIRES = "Expires";
  private static final String HEADER_LAST_MODIFIED = "Last-Modified";

  @SuppressWarnings({"InstanceVariableOfConcreteClass"})
  private final Settings settings;

  private final EntryRepository entryRepository;

  private final UserRepository userRepository;

  private final LocationRepository locationDao;

  private final MoodRepository moodDao;

  private final Login webLogin;

  private final TrackbackService trackbackService;

  private final BingService bingService;

  public UpdateJournal(Settings settings, EntryRepository entryRepository, UserRepository user,
                       LocationRepository locationDao, MoodRepository moodDao, Login webLogin, TrackbackService trackbackService, BingService bingService) {
    super();
    this.settings = settings;
    this.entryRepository = entryRepository;
    this.userRepository = user;
    this.locationDao = locationDao;
    this.moodDao = moodDao;
    this.webLogin = webLogin;
    this.trackbackService = trackbackService;
    this.bingService = bingService;
  }

  /**
   * Determine the type of client
   *
   * @param ua user agent
   * @param client client param
   * @return ClientType
   */
  private static ClientType detectClient(final String ua, final String client) {
    if (ua != null && ua.contains("JustJournal")) {
      return ClientType.desktop;
    } else if (client != null && client.contains(PARAM_DASHBOARD)) {
      return ClientType.dashboard;
    } else if (client != null && client.contains(PARAM_MOBILE)) {
      return ClientType.mobile;
    } else {
      return ClientType.web;
    }
  }

  /**
   * Print out the webpage for HTML friendly clients.
   *
   * @param sb output buffer
   * @param userName the blog owner
   * @param userID the blog owner's id
   */
  private void htmlOutput(final StringBuilder sb, final String userName, final int userID) {
    /* Initialize Preferences Object */
    final User pf = userRepository.findByUsername(userName);
    final Journal journal = new ArrayList<>(pf.getJournals()).get(0);

    // Begin HTML document.
    // IE hates this.
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
    sb.append(endl);

    //  sb.append("<?xml-stylesheet href=\"http://www.w3.org/StyleSheets/TR/W3C-REC.css\"
    // type=\"text/css\"?>");
    // sb.append(endl);
    sb.append("<?xml-stylesheet href=\"#UserStyleSheet\" type=\"text/css\"?>");
    sb.append(endl);
    sb.append(
        "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" "
            + " \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
    sb.append(endl);

    sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
    sb.append(endl);

    sb.append("<head>");
    sb.append(endl);
    sb.append("\t<title>");
    sb.append(journal.getName());
    sb.append("</title>");
    sb.append(endl);

    /* use our template system instead */
    sb.append("\t<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/styles/")
        .append(journal.getStyle().getId())
        .append(".css\" />");
    sb.append(endl);

    /* End overrides */
    sb.append("</head>\n");

    sb.append("<body>\n");

    // BEGIN MENU
    sb.append("\t<!-- Header: Begin -->");
    sb.append(endl);
    sb.append("\t\t<div id=\"header\">");
    sb.append(endl);
    sb.append("\t\t<h1>");
    sb.append(journal.getName());
    sb.append("</h1>");
    sb.append(endl);
    sb.append("\t</div>");
    sb.append(endl);
    sb.append("\t<!-- Header: End -->\n");
    sb.append(endl);

    sb.append("\t<!-- Menu: Begin -->");
    sb.append(endl);
    sb.append("\t<div id=\"menu\">");
    sb.append(endl);

    sb.append("\t<p id=\"muser\">");
    sb.append(endl);
    sb.append("\t\t<a href=\"/users/");
    sb.append(userName);
    sb.append("\">recent entries</a><br />");
    sb.append(endl);
    sb.append("\t\t<a href=\"/users/").append(userName).append("/calendar\">Calendar</a><br />");
    sb.append(endl);
    sb.append("\t\t<a href=\"/users/").append(userName).append("/friends\">Friends</a><br />");
    sb.append(endl);
    sb.append("\t\t<a href=\"/profile.jsp?user=").append(userName).append("\">Profile</a><br />");
    sb.append(endl);
    sb.append("\t</p>");
    sb.append(endl);

    // General stuff...
    sb.append("\t<p id=\"mgen\">");
    sb.append(endl);
    sb.append("\t\t<a href=\"/update.jsp\">Update Journal</a><br />");
    sb.append(endl);

    // Authentication menu choice
    if (userID > 0) {
      // User is logged in.. give them the option to log out.
      sb.append("\t\t<a href=\"/prefs/index.jsp\">Preferences</a><br />");
      sb.append(endl);
      sb.append("\t\t<a href=\"/logout.jsp\">Log Out</a>");
      sb.append(endl);
    } else {
      // User is logged out.. give then the option to login.
      sb.append("\t\t<a href=\"/login.jsp\">Login</a>");
      sb.append(endl);
    }
    sb.append("\t</p>");
    sb.append(endl);

    sb.append("\t<p>");
    sb.append("<a href=\"/users/");
    sb.append(userName);
    sb.append("/subscriptions\">RSS Reader</a>");
    sb.append("<a href=\"/users/");
    sb.append(userName);
    sb.append("/rss\"><img src=\"/images/rss2.gif\" alt=\"Journal RSS Feed\" /></a><br />");

    sb.append("\t</p>");
    sb.append(endl);

    sb.append("\t</div>");
    sb.append(endl);
    sb.append("\t<!-- Menu: End -->\n");
    sb.append(endl);
    // END MENU

    sb.append("\t<!-- Content: Begin -->");
    sb.append(endl);
    sb.append("\t<div id=\"content\">");
    sb.append(endl);

    if (userID > 0) {
      sb.append("\t<p>You are logged in as <a href=\"/users/")
          .append(userName)
          .append("\"><img src=\"/images/userclass_16.png\" alt=\"user\" />")
          .append(userName)
          .append("</a>.</p>");
      sb.append(endl);
    }

    sb.append("\t\t<h2>Update Journal</h2>");
    sb.append(endl);

    sb.append("\t\t<p><strong>Entry added</strong></p>");
    sb.append(endl);
    sb.append("\t\t<p><a href=\"/update.jsp\">Add another entry</a></p>");
    sb.append(endl);
    sb.append("\t\t<p><a href=\"/users/");
    sb.append(userName);
    sb.append("\">View journal</a></p>");
    sb.append(endl);

    sb.append("\t</div>");
    sb.append(endl);
    sb.append("\t<!-- Content: End -->");
    sb.append(endl);
    sb.append(endl);

    sb.append("\t<!-- Footer: Begin -->");
    sb.append(endl);
    sb.append("\t<div id=\"footer\">");
    sb.append(endl);
    sb.append("\t\t<a href=\"/\" title=\"");
    sb.append(settings.getSiteName());
    sb.append("\">");
    sb.append(settings.getSiteName());
    sb.append("</a> ");
    sb.append("\t</div>");
    sb.append(endl);

    sb.append("\t<!-- Footer: End -->\n");
    sb.append(endl);

    sb.append("</body>");
    sb.append(endl);
    sb.append("</html>");
    sb.append(endl);
  }

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws java.io.IOException it is a web app
   */
  protected void processRequest(
      final HttpServletRequest request, final HttpServletResponse response)
      throws java.io.IOException {

    boolean blnError = false;
    final StringBuilder sb = new StringBuilder();

    // start session if one does not exist.
    final HttpSession session = request.getSession(true);
    int userID = 0;
    String userName;
    final Integer userIDasi;

    userName = (String) session.getAttribute(LOGIN_ATTRNAME);
    userIDasi = (Integer) session.getAttribute("auth.uid");

    if (userIDasi != null) {
      userID = userIDasi;
    }

    /* Detect user agent */
    final String userAgent = request.getHeader("User-Agent");
    final ClientType myclient = detectClient(userAgent, request.getParameter("client"));

    if (myclient == ClientType.web) {
      // Send HTML type in http stream
      final String mimeType = HTMLUtil.determineMimeType(request.getHeader("Accept"), userAgent);
      response.setContentType(mimeType + "; charset=utf-8");
      response.setBufferSize(DEFAULT_BUFFER_SIZE);
      response.setDateHeader(HEADER_EXPIRES, System.currentTimeMillis());
      response.setDateHeader(HEADER_LAST_MODIFIED, System.currentTimeMillis());
      response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
      response.setHeader("Pragma", "no-cache");
    } else if (myclient == ClientType.mobile) {
      response.setContentType("application/xhtml+xml; charset=utf-8");
      response.setBufferSize(DEFAULT_BUFFER_SIZE);
      response.setDateHeader(HEADER_EXPIRES, System.currentTimeMillis());
      response.setDateHeader(HEADER_LAST_MODIFIED, System.currentTimeMillis());
      response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
      response.setHeader("Pragma", "no-cache");
    } else {
      response.setContentType("text/plain");
      response.setDateHeader(HEADER_EXPIRES, System.currentTimeMillis());
      response.setDateHeader(HEADER_LAST_MODIFIED, System.currentTimeMillis());
    }

    // Validate the login
    if (userID < 1) {
      try {
        userName = request.getParameter("user");
        if (StringUtils.isNotBlank(userName))
          userName = userName.toLowerCase();
        final String password = request.getParameter("pass");
        userID = webLogin.validate(userName, password);
        if (userID == BAD_USER_ID)
          throw new Exception("Invalid username or password.");

        String keepLogin = request.getParameter("keeplogin");
        if (keepLogin == null) keepLogin = "";
        if (keepLogin.compareTo(PARAM_CHECKED) == 0) {
          session.setAttribute(LOGIN_ATTRID, userID);
          session.setAttribute(LOGIN_ATTRNAME, userName);
          webLogin.setLastLogin(userID);
        }
      } catch (Exception e3) {
        if (myclient == ClientType.web)
          ErrorPage.display(
              "Authentication Error",
              "Unable to login.  Please check your username and password.",
              sb);
        else sb.append(JJ_LOGIN_FAIL);
      }
    }

    if (userID > 0) {
      // We authenticated OK.  Continue...

      final User user = userRepository.findById(userID).orElse(null);
      final Entry et = new Entry();

      // Get the user input
      final int security = Integer.parseInt(request.getParameter(PARAM_SECURITY));
      final int location = Integer.parseInt(request.getParameter(PARAM_LOCATION));
      final int mood = Integer.parseInt(request.getParameter(PARAM_MOOD));
      String music = request.getParameter(PARAM_MUSIC);
      String aformat = request.getParameter(PARAM_AUTO_FORMAT);
      String allowcomment = request.getParameter(PARAM_ALLOW_COMMENT);
      String emailcomment = request.getParameter(PARAM_EMAIL_COMMENT);
      String tags = request.getParameter(PARAM_TAGS);
      String trackback = request.getParameter(PARAM_TRACKBACK);
      String date = request.getParameter(PARAM_DATE);
      String time = request.getParameter(PARAM_TIME);

      if (music == null) music = "";
      if (aformat == null) aformat = "";
      if (allowcomment == null) allowcomment = "";
      if (emailcomment == null) emailcomment = "";
      if (tags == null) tags = "";
      if (trackback == null) trackback = "";
      if (date == null) {
        final java.text.SimpleDateFormat fmtdate = new java.text.SimpleDateFormat("yyyy-MM-dd");
        final java.text.SimpleDateFormat fmttime = new java.text.SimpleDateFormat("HH:mm:ss");
        final java.sql.Date now = new java.sql.Date(System.currentTimeMillis());
        date = fmtdate.format(now);
        time = fmttime.format(now);
      }
      if (time == null) {
        time = ""; // assume date was set ok
      }

      music = music.trim();
      aformat = aformat.trim();
      allowcomment = allowcomment.trim();
      emailcomment = emailcomment.trim();
      tags = tags.trim();
      tags = tags.toLowerCase(); // tags must be lowercase
      trackback = trackback.trim();
      date = date.trim();
      time = time.trim();
      time = StringUtil.replace(time, 'T', "");
      log.debug("date: " + date);
      log.debug("time: " + time);
      String subject = request.getParameter("subject");
      String body = request.getParameter("body");

      if ((subject == null || subject.isEmpty()) && body != null) {
        final Pattern p = Pattern.compile("(<title>)(.*?)(</title>)");

        final Matcher m = p.matcher(body);

        if (m.find()) {
          subject = m.group(2);
          body = m.replaceAll("");
        }
      }

      // escape out for MySQL
      if (subject != null)
        subject = StringUtil.replace(subject, '\'', "\\\'");

      try {
        et.setUser(user);
        final DateTime dtb = new DateTimeBean();
        dtb.set(date + " " + time);
        et.setDate(dtb.toDate());
        et.setSubject(subject);
        et.setMusic(StringUtil.replace(music, '\'', "\\\'"));

        et.setSecurity(Security.fromValue(security));

        et.setLocation(locationDao.findById(location).orElse(null));
        et.setMood(moodDao.findById(mood));

        // assume it's not a draft for the old submit code.
        et.setDraft(PrefBool.N);

        // the check box says disable auto format
        if ((aformat.equals(PARAM_CHECKED))
            || myclient == ClientType.dashboard
            || myclient == ClientType.mobile) {
          et.setAutoFormat(PrefBool.Y);
          et.setFormat(FormatType.TEXT);
        } else {
          et.setAutoFormat(PrefBool.N);
          et.setFormat(FormatType.HTML);
          // probably HTML, run jtidy on it
          body = HTMLUtil.clean(body, false);
        }

        // escape out for MySQL
        if (body != null)
          body = StringUtil.replace(body, '\'', "\\\'");
        et.setBody(body);

        // disable comments
        if ((allowcomment.equals(PARAM_CHECKED))
            || myclient == ClientType.dashboard
            || myclient == ClientType.mobile) et.setAllowComments(PrefBool.Y);
        else et.setAllowComments(PrefBool.N);

        // disable email notifications
        if ((emailcomment.equals(PARAM_CHECKED))
            || myclient == ClientType.dashboard
            || myclient == ClientType.mobile) et.setEmailComments(PrefBool.Y);
        else et.setEmailComments(PrefBool.N);

        et.setTrackback(trackback);
      } catch (final IllegalArgumentException e1) {
        if (myclient == ClientType.web) ErrorPage.display("Input Error", e1.getMessage(), sb);
        else sb.append("JJ.JOURNAL.UPDATE.FAIL");

        blnError = true;
      } catch (final ParseException e) {
        log.error(e.getMessage());
        if (myclient == ClientType.web) ErrorPage.display("Input Error", "Date Parse failure", sb);
        else sb.append("JJ.JOURNAL.UPDATE.FAIL");
      }

      final String isSpellCheck = request.getParameter("spellcheck");
      if (isSpellCheck != null && isSpellCheck.compareTo(PARAM_CHECKED) == 0) {
        final Spelling sp = new Spelling();

        // store everything
        session.setAttribute("spell.check", "true");
        session.setAttribute("spell.body", et.getBody());
        session.setAttribute("spell.music", et.getMusic());
        session.setAttribute("spell.location", et.getLocation());
        session.setAttribute("spell.subject", et.getSubject());
        session.setAttribute("spell.date", et.getDate());
        session.setAttribute("spell.security", et.getSecurity());
        session.setAttribute("spell.mood", et.getMood());
        session.setAttribute("spell.tags", tags);
        session.setAttribute("spell.trackback", trackback);

        // check the spelling now
        session.setAttribute(
            "spell.suggest", sp.checkSpelling(HTMLUtil.textFromHTML(et.getBody())));

        // redirect the user agent to the promised land.
        response.sendRedirect("/update.jsp");

      } else {
        // clear out spell check variables to be safe
        // note this might be wrong still
        session.setAttribute("spell.check", ""); // false
        session.setAttribute("spell.body", "");
        session.setAttribute("spell.music", "");
        session.setAttribute("spell.location", 0);
        session.setAttribute("spell.subject", "");
        session.setAttribute("spell.date", "");
        session.setAttribute("spell.time", "");
        session.setAttribute("spell.security", 0);
        session.setAttribute("spell.mood", -1);
        session.setAttribute("spell.tags", "");
        session.setAttribute("spell.trackback", "");

        // create entry
        if (!blnError) {
          entryRepository.save(et);

          /*  if (!result) {
              if (myclient == ClientType.web)
                  WebError.Display("Error", "Error adding the journal entry", sb);
              else
                  sb.append("JJ.JOURNAL.UPDATE.FAIL");
              blnError = true;
          }*/
        }

        // add tags
        if (!blnError) {
          log.debug("Add Tags");
          if (!tags.isEmpty()) {
            final Collection<String> t = new ArrayList<>();
            final StringTokenizer st = new StringTokenizer(tags, " :,;");
            while (st.hasMoreTokens()) {
              String tok = st.nextToken();
              tok = StringUtil.deleteChar(tok, ',');
              tok = StringUtil.deleteChar(tok, ':');
              tok = StringUtil.deleteChar(tok, ' ');
              tok = StringUtil.deleteChar(tok, ';');
              if (StringUtil.isAlpha(tok)) t.add(tok);
              else log.debug("tag is: " + tok);
            }

            // lookup the tag id
            // if (t.size() > 0) {
            // TODO: is this right?
            // Entry et2 = entryRepository.findById(et.getId()).orElse(null);
            //    entryDao.setTags(et2.getId(), t);
            //  }
          }
        }

        // display message to user.
        if (!blnError) {
          if (myclient == ClientType.web) htmlOutput(sb, userName, userID);
          else if (myclient == ClientType.mobile) {
            // TODO: Set content type?
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            sb.append(endl);
            sb.append(
                "<!DOCTYPE html PUBLIC \"-//WAPFORUM//DTD XHTML Mobile 1.1//EN\"\n"
                    + "    \"http://www.openmobilealliance.org/tech/DTD/xhtml-mobile11.dtd\">");
            sb.append(endl);
            sb.append(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">\n"
                    + "<head>\n"
                    + "<title>JustJournal.com: Entry Posted</title>\n"
                    + "</head>\n"
                    + "<body>\n"
                    + "<h1>JustJournal.com</h1>\n");
            sb.append("<h2>Entry Posted</h2>");
            sb.append(endl);
            sb.append(
                "<p><a href=\"./mob/update.jsp\">Post another?</a>  <a"
                    + " href=\"./mob/logout.jsp\">Log out?</a></p>");
            sb.append(endl);
            sb.append("</body>\n</html>");
            sb.append(endl);
          } else sb.append("JJ.JOURNAL.UPDATE.OK");

          if (et.getSecurity() == Security.PUBLIC) {
            /* Initialize Preferences Object */
            final User pf = userRepository.findByUsername(userName);
            final Journal journal = new ArrayList<>(pf.getJournals()).get(0);

            if (!journal.isOwnerViewOnly() && journal.isPingServices()) {
              final RestPing rp = new RestPing("http://ping.blo.gs/");
              rp.setName(journal.getName());
              rp.setUri(settings.getBaseUri() + PATH_USERS + pf.getUsername());
              rp.setChangesURL(settings.getBaseUri() + PATH_USERS + pf.getUsername() + "/rss");
              rp.ping();

              final Entry et2 = entryRepository.findById(et.getId()).orElse(null);
              if (et2 != null) {
                trackbackPing(et2.toEntryTo(), user, et2.getId());

                if (user != null)
                  bingService.indexNow(settings.getBaseUri() + PATH_USERS + user.getUsername() + PATH_ENTRY + et2.getId());
              }
            }
          }
        }

        // output the result of our processing
        final ServletOutputStream outstream = response.getOutputStream();
        outstream.println(sb.toString());
        outstream.flush();
      }
    } else {
      if (myclient == ClientType.web)
        // We couldn't authenticate.  Tell the user.
        ErrorPage.display(
            "Authentication Error",
            "Unable to login.  Please check your username and password.",
            sb);
      else sb.append(JJ_LOGIN_FAIL);

      // output the result of our processing
      final ServletOutputStream outstream = response.getOutputStream();
      outstream.println(sb.toString());
      outstream.flush();
    }
  }

  private void trackbackPing(EntryTo entryTo, User user, int entryId) {
    if (StringUtils.isNotBlank(entryTo.getTrackback())) {
      try {
            Optional<Journal> journal = user.getJournals().stream().findFirst();
            journal.ifPresent(value -> trackbackService.sendForBlog(entryTo.getTrackback(), entryId, user.getUsername(),
                    value.getName(), entryTo.getSubject(), entryTo.getBody()));
      } catch (final Exception e) {
        log.error("Could not save trackback on entry {}", entryId, e);
      }
    }

    List<String> trackbackCandidates = HTMLUtil.getURIs(entryTo.getBody());
    for (String tbUrl : trackbackCandidates) {
      if (Objects.equals(tbUrl, entryTo.getTrackback())) continue;

      try {
            Optional<Journal> journal = user.getJournals().stream().findFirst();
            journal.ifPresent(value -> trackbackService.sendForBlog(tbUrl, entryId, user.getUsername(), value.getName(),
                    entryTo.getSubject(), entryTo.getBody()));
      } catch (final Exception e) {
        log.error("Could not save trackback on entry {}", entryId, e);
      }
    }
  }

  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   */
  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws java.io.IOException {
    processRequest(request, response);
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   */
  @Override
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
      throws java.io.IOException {
    processRequest(request, response);
  }

  /** Returns a short description of the servlet. */
  @Override
  public String getServletInfo() {
    return "add a journal entry";
  }

  enum ClientType {
    web,
    mobile,
    dashboard,
    desktop
  }
}
