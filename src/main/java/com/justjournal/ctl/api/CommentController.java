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
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.model.Comment;
import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.model.QueueMail;
import com.justjournal.model.Settings;
import com.justjournal.model.User;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.SettingsRepository;
import com.justjournal.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.justjournal.core.Constants.PARAM_ID;
import static com.justjournal.core.Constants.PATH_ENTRY;
import static com.justjournal.core.Constants.PATH_USERS;

@Slf4j
@RestController
@RequestMapping("/api/comment")
public class CommentController {

    private CommentRepository commentDao;
    private EntryRepository entryDao;
    private UserRepository userRepository;
    private SettingsRepository settingsRepository;
    private QueueMailRepository queueMailRepository;

    @Autowired
    public CommentController(final CommentRepository commentRepository,
                             final EntryRepository entryRepository,
                             final SettingsRepository settingsRepository,
                             final UserRepository userRepository,
                             final QueueMailRepository queueMailRepository) {
        this.userRepository = userRepository;
        this.commentDao = commentRepository;
        this.entryDao = entryRepository;
        this.settingsRepository = settingsRepository;
        this.queueMailRepository = queueMailRepository;
    }

    @GetMapping("{id}")
    public ResponseEntity<Comment> getById(@PathVariable(PARAM_ID) final Integer id) {
        Comment comment = commentDao.findById(id).orElse(null);

        if (comment != null)
            return ResponseEntity.ok(comment);

        return ResponseEntity.notFound().build();
    }

    @GetMapping(produces =  MediaType.APPLICATION_JSON_VALUE)
    public List<Comment> getComments(@RequestParam(Constants.PARAM_ENTRY_ID) final Integer entryId,
                                     final HttpServletResponse response) {
        final Entry entry = entryDao.findById(entryId).orElse(null);

        if (entry == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return Collections.emptyList();
        }

        try {
            if (new ArrayList<>(entry.getUser().getJournals()).get(0).isOwnerViewOnly() ||
                    entry.getAllowComments() == PrefBool.N ||
                    entry.getSecurity().getId() == 0) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
        } catch (final Exception e) {
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        return commentDao.findByEntryId(entryId);
    }

    @DeleteMapping(value = "{id}")
    public Map<String, String> delete(@PathVariable(PARAM_ID) final int id, final HttpSession session,
                                      final HttpServletResponse response) {

        if (!Login.isAuthenticated(session)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
        }

        try {
            final Comment comment = commentDao.findById(id).orElse(null);
            if (comment == null)
                throw new IllegalArgumentException(PARAM_ID);

            if (comment.getUser().getId() == Login.currentLoginId(session))
                commentDao.deleteById(id);

            return java.util.Collections.singletonMap(PARAM_ID, Integer.toString(comment.getId()));
        } catch (final Exception e) {
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ErrorHandler.modelError(  "Could not delete the comment.");
        }
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> post(@RequestBody final Comment comment, final HttpSession session,
                                    final HttpServletResponse response) {
        return put(comment, session, response);
    }

    @PutMapping(produces =  MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> put(@RequestBody final Comment comment, final HttpSession session,
                                   final HttpServletResponse response) {
        if (!Login.isAuthenticated(session)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
        }

        try {
            final User user = userRepository.findById(Login.currentLoginId(session)).orElse(null);
            final Entry et = entryDao.findById(comment.getEid()).orElse(null);

            if (et == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return ErrorHandler.modelError(  "No entry for this comment");
            }

            if (et.getAllowComments().equals(PrefBool.N)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return ErrorHandler.modelError(  "Comments blocked by owner of this blog entry.");
            }

            // new case
            if (comment.getId() == 0) {
                comment.setUser(user);
                comment.setDate(new Date());
                comment.setEntry(et);
                try {
                    commentDao.save(comment);
                } catch (final Exception e) {
                    log.error("Could not add comment", e);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return ErrorHandler.modelError(  "Error adding comment");
                }
            } else {
                final Comment c = commentDao.findById(comment.getId()).orElse(null);

                if (c == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return ErrorHandler.modelError(  "No comment found");
                }

                if (c.getEntry().getId() != et.getId()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return ErrorHandler.modelError(  "Error saving comment. Entry id does not match original on comment.");
                }
                c.setUser(user);
                c.setBody(comment.getBody());
                c.setSubject(comment.getSubject());
                try {
                    commentDao.save(c);
                } catch (final Exception e) {
                    log.error("Could not update comment", e);
                    return ErrorHandler.modelError(  "Error editing comment");
                }
            }

            queueMail(user, et, comment);
            
            return java.util.Collections.singletonMap("id", Integer.toString(comment.getId()));
        } catch (final Exception e) {
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ErrorHandler.modelError(  "Error adding comment");
        }
    }

    private void queueMail(final User user, final Entry et, final Comment comment) {
        try {
            final User pf = et.getUser();
            final Settings mailfrom = settingsRepository.findByName("mailFrom");
            final Settings baseuri = settingsRepository.findByName("baseuri");

            final String entryUrl = baseuri + PATH_USERS + et.getUser().getUsername() + PATH_ENTRY + et.getId();

            // TODO: should we allow the user making the comment to disable email notifications?
            if (user != null && et.getEmailComments().equals(PrefBool.Y)) {
                final QueueMail mail = new QueueMail();
                if (mailfrom != null)
                    mail.setFrom(mailfrom.getValue());
                else
                    mail.setFrom("root@localhost");
                mail.setTo(pf.getUserContact().getEmail());
                mail.setBody(generateMailBody(user, comment, entryUrl));

                mail.setSubject("JustJournal: Comment Notification");
                mail.setPurpose("comment_notify");
                queueMailRepository.save(mail);
            }
        } catch (final Exception e) {
            log.error("Could not send mail: " + e.getMessage());
        }
    }

    private String generateMailBody(final User user, final Comment comment, final String entryUrl) {
        // TODO: site name should be stored in settings not hard coded
        return user.getUsername() + " said: \n"
                + "Subject: " + comment.getSubject() + "\n"
                + comment.getBody() + "\n\nIn response to:\n"
                + entryUrl + "\n\n"
                + "From here, you can:\n\n"
                + "View all comments to this entry: "
                + entryUrl + "\n\n"
                + "Reply at the webpage: http://www.justjournal.com/#!/comment/"
                + comment.getEid()
                + "\n\n-- JustJournal.com\n\n"
                + "(If you would prefer not to get these updates," +
                " edit the entry to disable comment notifications.)\n";
    }
}
