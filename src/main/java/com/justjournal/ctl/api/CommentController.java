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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.PARAM_ID;
import static com.justjournal.core.Constants.PATH_ENTRY;
import static com.justjournal.core.Constants.PATH_USERS;

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.model.*;
import com.justjournal.model.api.CommentTo;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.SettingsRepository;
import com.justjournal.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.justjournal.utility.StringUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/comment")
public class CommentController {

  private final CommentRepository commentDao;
  private final EntryRepository entryDao;
  private final UserRepository userRepository;
  private final SettingsRepository settingsRepository;
  private final QueueMailRepository queueMailRepository;

  @Autowired
  public CommentController(
      final CommentRepository commentRepository,
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
  public ResponseEntity<CommentTo> getById(@PathVariable(PARAM_ID) final Integer id) {
    Optional<CommentTo> comment = commentDao.findById(id).map(Comment::toCommentTo);
    return comment.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CommentTo> getComments(
      @RequestParam(Constants.PARAM_ENTRY_ID) final Integer entryId,
      final HttpServletResponse response) {
    final Entry entry = entryDao.findById(entryId).orElse(null);

    if (entry == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return Collections.emptyList();
    }

    try {
      if (new ArrayList<>(entry.getUser().getJournals()).get(0).isOwnerViewOnly()
          || entry.getAllowComments() == PrefBool.N
          || entry.getSecurity() == Security.PRIVATE) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      }
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    return commentDao.findByEntryId(entryId).stream()
        .map(Comment::toCommentTo)
            .toList();
  }

  @DeleteMapping(value = "{id}")
  public Map<String, String> delete(
      @PathVariable(PARAM_ID) final int id,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    try {
      final Comment comment = commentDao.findById(id).orElse(null);
      if (comment == null) throw new IllegalArgumentException(PARAM_ID);

      if (comment.getUser().getId() == Login.currentLoginId(session)) commentDao.deleteById(id);

      return java.util.Collections.singletonMap(PARAM_ID, Integer.toString(comment.getId()));
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Could not delete the comment.");
    }
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> post(
      @RequestBody final CommentTo comment,
      final HttpSession session,
      final HttpServletResponse response) {
    return put(comment, session, response);
  }

  @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> put(
      @RequestBody final CommentTo commentTo,
      final HttpSession session,
      final HttpServletResponse response) {
    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    try {
      final User user = userRepository.findById(Login.currentLoginId(session)).orElse(null);
      final Entry et = entryDao.findById(commentTo.getEid()).orElse(null);

      if (et == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return ErrorHandler.modelError("No entry for this comment");
      }

      if (et.getAllowComments().equals(PrefBool.N)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return ErrorHandler.modelError("Comments blocked by owner of this blog entry.");
      }

      Comment comment =
          Comment.builder()
              .id(commentTo.getId())
              .body(commentTo.getBody())
              .date(commentTo.getDate())
              .eid(commentTo.getEid())
              .format(FormatType.valueOf(commentTo.getFormat()))
              .build();

      // new case
      if (comment.getId() == 0) {
        comment.setUser(user);
        comment.setDate(new Date());
        comment.setEntry(et);
        // grammarly countermeasure
        comment.setSubject(StringUtil.stripNonPrintableCharacters(comment.getSubject()));
        comment.setBody(StringUtil.stripNonPrintableCharacters(comment.getBody()));
        try {
          commentDao.save(comment);
        } catch (final Exception e) {
          log.error("Could not add comment", e);
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          return ErrorHandler.modelError("Error adding comment");
        }
      } else {
        final Comment c = commentDao.findById(comment.getId()).orElse(null);

        if (c == null) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          return ErrorHandler.modelError("No comment found");
        }

        if (c.getEntry().getId() != et.getId()) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          return ErrorHandler.modelError(
              "Error saving comment. Entry id does not match original on comment.");
        }
        c.setUser(user);
        // grammarly countermeasure
        c.setBody(StringUtil.stripNonPrintableCharacters(comment.getBody()));
        c.setSubject(StringUtil.stripNonPrintableCharacters(comment.getSubject()));
        try {
          commentDao.save(c);
        } catch (final Exception e) {
          log.error("Could not update comment", e);
          return ErrorHandler.modelError("Error editing comment");
        }
      }

      queueMail(user, et, comment);

      return java.util.Collections.singletonMap("id", Integer.toString(comment.getId()));
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Error adding comment");
    }
  }

  private void queueMail(final User user, final Entry et, final Comment comment) {
    try {
      final User pf = et.getUser();
      final Settings mailfrom = settingsRepository.findByName("mailFrom");
      final Settings baseuri = settingsRepository.findByName("baseuri");

      final String entryUrl =
          baseuri + PATH_USERS + et.getUser().getUsername() + PATH_ENTRY + et.getId();

      // TODO: should we allow the user making the comment to disable email notifications?
      if (user != null && et.getEmailComments().equals(PrefBool.Y)) {
        final QueueMail mail = new QueueMail();
        if (mailfrom != null) mail.setFrom(mailfrom.getValue());
        else mail.setFrom("root@localhost");
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
    return user.getUsername()
        + " said: \n"
        + "Subject: "
        + comment.getSubject()
        + "\n"
        + comment.getBody()
        + "\n\nIn response to:\n"
        + entryUrl
        + "\n\n"
        + "From here, you can:\n\n"
        + "View all comments to this entry: "
        + entryUrl
        + "\n\n"
        + "Reply at the webpage: http://www.justjournal.com/#!/comment/"
        + comment.getEid()
        + "\n\n-- JustJournal.com\n\n"
        + "(If you would prefer not to get these updates,"
        + " edit the entry to disable comment notifications.)\n";
  }
}
