/*
Copyright (c) 2005, Lucas Holt
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

package com.justjournal.ctl.api;

import com.justjournal.Login;
import com.justjournal.model.Comment;
import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/comment")
public class CommentController {
    private org.slf4j.Logger log = LoggerFactory.getLogger(CommentController.class);

    private CommentRepository commentDao = null;
    private EntryRepository entryDao = null;

    public void setEntryDao(final EntryRepository entryDao) {
        this.entryDao = entryDao;
    }

    public void setCommentDao(final CommentRepository commentDao) {
        this.commentDao = commentDao;
    }

    @RequestMapping("/api/comment/{id}")
    @ResponseBody
    public Comment getById(@PathVariable("id") Integer id) {
        return commentDao.findOne(id);
    }

    @RequestMapping(method = RequestMethod.GET, produces = "application/json")
    public
    @ResponseBody
    List<Comment> getComments(@RequestParam("entryId") Integer entryId, HttpServletResponse response) throws Exception {
        Entry entry = entryDao.findOne(entryId);

        try {
            if (entry.getUser().getUserPref().getOwnerViewOnly() == PrefBool.Y ||
                    entry.getAllowComments() == PrefBool.N ||
                    entry.getSecurity().getId() == 0) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        return commentDao.findByEntryId(entryId);
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, String> delete(@PathVariable("id") int id, HttpSession session, HttpServletResponse response) throws Exception {

        if (!Login.isAuthenticated(session)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return java.util.Collections.singletonMap("error", "The login timed out or is invalid.");
        }

        try {
            Comment comment = commentDao.findOne(id);
            if (comment.getUser().getId() == Login.currentLoginId(session))
                commentDao.delete(id);

            return java.util.Collections.singletonMap("id", Integer.toString(comment.getId()));
        } catch (Exception e) {
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return java.util.Collections.singletonMap("error", "Could not delete the comment.");
        }
    }
  /*    TODO: finish once DAO is working
    @RequestMapping(method = RequestMethod.POST, produces = "application/json")
    public
    @ResponseBody
    Map<String, String> update(@RequestBody Comment comment, HttpSession session, HttpServletResponse response) {

        try {
            comment.setUserId(WebLogin.currentLoginId(session));
            boolean result = commentDao.update(comment);

            if (!result) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return java.util.Collections.singletonMap("error", "Error editing comment.");
            }
            return java.util.Collections.singletonMap("id", Integer.toString(comment.getId()));
        } catch (Exception e) {
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return java.util.Collections.singletonMap("error", "Error editing comment.");
        }
    }

    @RequestMapping(method = RequestMethod.PUT, produces = "application/json")
    public
    @ResponseBody
    Map<String, String> put(@RequestBody Comment comment, HttpSession session, HttpServletResponse response) {

        try {
            Settings settings = new Settings();
            comment.setUserId(WebLogin.currentLoginId(session));
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.sql.Date now = new java.sql.Date(System.currentTimeMillis());
            comment.setDate(fmt.format(now));

            EntryTo et = entryDao.viewSinglePublic(comment.getEid());

            if (!et.getAllowComments()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return java.util.Collections.singletonMap("error", "Comments blocked by owner of this blog entry.");
            }

            boolean result = commentDao.add(comment);

            try {
                User pf = new UserImpl(et.getUsername());

                if (et.getEmailComments()) {
                    QueueMail mail = new QueueMail();
                    mail.setFromAddress(settings.getMailFrom());
                    mail.setToAddress(pf.getEmailAddress());
                    mail.setBody(comment.getUsername() + " said: \n"
                            + "Subject: " + comment.getSubject() + "\n"
                            + comment.getBody() + "\n\nIn response to:\n"
                            + "http://www.justjournal.com/comment/index.jsp?id="
                            + comment.getEid() + "\n\n"
                            + "From here, you can:\n\n"
                            + "View all comments to this entry: "
                            + "http://www.justjournal.com/comment/index.jsp?id="
                            + comment.getEid() + "\n\n"
                            + "Reply at the webpage: http://www.justjournal.com/comment/add.jsp?id="
                            + comment.getEid()
                            + "\n\n-- JustJournal.com\n\n"
                            + "(If you would prefer not to get these updates," +
                            " edit the entry to disable comment notifications.)\n");

                    mail.setSubject("JustJournal: Comment Notification");
                    mail.setPurpose("comment_notify");
                    mail.send();
                }
            } catch (Exception e) {
                log.error("Could not send mail: " + e.getMessage());
            }
            if (!result) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return java.util.Collections.singletonMap("error", "Error adding comment.");
            }
            return java.util.Collections.singletonMap("id", Integer.toString(comment.getId()));
        } catch (Exception e) {
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return java.util.Collections.singletonMap("error", "Error adding comment");
        }
    }
    */

}
