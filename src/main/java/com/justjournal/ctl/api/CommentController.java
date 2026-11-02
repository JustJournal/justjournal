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

import com.justjournal.Login;
import com.justjournal.core.Constants;
import com.justjournal.ctl.error.ErrorHandler;
import com.justjournal.model.*;
import com.justjournal.model.api.CommentTo;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.RemoteReplyRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.CommentMailService;
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
  private final CommentMailService commentMailService;
  private final RemoteReplyRepository remoteReplyRepository;

  @Autowired
  public CommentController(
      final CommentRepository commentRepository,
      final EntryRepository entryRepository,
      final UserRepository userRepository,
      final CommentMailService commentMailService,
      final RemoteReplyRepository remoteReplyRepository) {
    this.userRepository = userRepository;
    this.commentDao = commentRepository;
    this.entryDao = entryRepository;
    this.commentMailService = commentMailService;
    this.remoteReplyRepository = remoteReplyRepository;
  }

  @GetMapping("{id}")
  public ResponseEntity<CommentTo> getById(
      @PathVariable(PARAM_ID) final Integer id, final HttpSession session) {
    final Optional<Comment> comment = commentDao.findById(id);
    if (comment.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    final Comment c = comment.get();
    final int loginId = Login.currentLoginId(session);
    final boolean author = loginId > 0 && c.getUser() != null && c.getUser().getId() == loginId;

    try {
      if (!author && !canViewComments(c.getEntry(), session)) {
        return ResponseEntity.status(HttpServletResponse.SC_FORBIDDEN).build();
      }
    } catch (final Exception e) {
      log.error(e.getMessage());
      return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok(c.toCommentTo());
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CommentTo> getComments(
      @RequestParam(Constants.PARAM_ENTRY_ID) final Integer entryId,
      final HttpSession session,
      final HttpServletResponse response) {
    final Entry entry = entryDao.findById(entryId).orElse(null);

    if (entry == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return Collections.emptyList();
    }

    try {
      if (!canViewComments(entry, session)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return Collections.emptyList();
      }
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return Collections.emptyList();
    }

    final List<CommentTo> comments = new ArrayList<>();
    for (final Comment comment : commentDao.findByEntryId(entryId)) {
      comments.add(comment.toCommentTo());
    }
    // replies from other fediverse servers follow; remote marks them, their ids are their own
    for (final RemoteReply reply :
        remoteReplyRepository.findByEntryIdOrderByPublishedAscIdAsc(entryId)) {
      comments.add(CommentTo.of(reply));
    }
    return comments;
  }

  /**
   * The entry owner can always see comments. Everyone else is blocked when the journal is owner
   * view only, comments are disabled, the entry is private, or the entry is friends only and they
   * are not on the owner's friends list.
   */
  private boolean canViewComments(final Entry entry, final HttpSession session) {
    final User owner = entry.getUser();
    final int loginId = Login.currentLoginId(session);

    if (loginId > 0 && owner.getId() == loginId) return true;

    if (owner.getJournals().stream().findFirst().map(Journal::isOwnerViewOnly).orElse(false)
        || entry.getAllowComments() == PrefBool.N
        || entry.getSecurity() == Security.PRIVATE) {
      return false;
    }

    if (entry.getSecurity() == Security.FRIENDS) {
      return loginId > 0
          && owner.getFriends().stream().anyMatch(f -> f.getFriend().getId() == loginId);
    }

    return true;
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

  /**
   * Delete a reply from another fediverse server. Only the owner of the entry it replies to may.
   *
   * @param id the reply's id, as the comment list gives it for a remote comment
   */
  @DeleteMapping(value = "remote/{id}")
  public Map<String, String> deleteRemote(
      @PathVariable(PARAM_ID) final int id,
      final HttpSession session,
      final HttpServletResponse response) {

    if (!Login.isAuthenticated(session)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError(Constants.ERR_INVALID_LOGIN);
    }

    final RemoteReply reply = remoteReplyRepository.findById(id).orElse(null);
    if (reply == null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Could not delete the comment.");
    }

    final Entry entry = entryDao.findById(reply.getEntryId()).orElse(null);
    if (entry == null
        || entry.getUser() == null
        || entry.getUser().getId() != Login.currentLoginId(session)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return ErrorHandler.modelError("Could not delete the comment.");
    }

    remoteReplyRepository.delete(reply);
    return java.util.Collections.singletonMap(PARAM_ID, Integer.toString(reply.getId()));
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
              .subject(commentTo.getSubject())
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

      if (user != null) {
        commentMailService.commentAdded(
            et, user.getUsername(), comment.getSubject(), comment.getBody());
      }

      return java.util.Collections.singletonMap("id", Integer.toString(comment.getId()));
    } catch (final Exception e) {
      log.error(e.getMessage());
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return ErrorHandler.modelError("Error adding comment");
    }
  }
}
