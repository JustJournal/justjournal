/*
 * Copyright (c) 2003-2026 Lucas Holt
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

import static com.justjournal.core.Constants.PATH_ENTRY;
import static com.justjournal.core.Constants.PATH_USERS;

import com.justjournal.model.Entry;
import com.justjournal.model.PrefBool;
import com.justjournal.model.QueueMail;
import com.justjournal.model.Settings;
import com.justjournal.model.User;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.repository.SettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tells a writer by mail that someone commented on their entry, when the entry asks for it. Used
 * for comments from users here and replies from other fediverse servers alike. Nothing here
 * throws: a mail that can't be queued is logged and the comment stands.
 */
@Slf4j
@Service
public class CommentMailService {

  private final SettingsRepository settingsRepository;
  private final QueueMailRepository queueMailRepository;

  public CommentMailService(
      final SettingsRepository settingsRepository,
      final QueueMailRepository queueMailRepository) {
    this.settingsRepository = settingsRepository;
    this.queueMailRepository = queueMailRepository;
  }

  /**
   * Queue the mail, if the entry has comment mail turned on.
   *
   * @param entry the entry commented on
   * @param author who commented, as the mail names them
   * @param subject the comment's subject
   * @param body the comment as plain text
   */
  public void commentAdded(
      final Entry entry, final String author, final String subject, final String body) {
    try {
      if (entry.getEmailComments() != PrefBool.Y) return;
      final User owner = entry.getUser();
      final Settings mailfrom = settingsRepository.findByName("mailFrom");
      final Settings baseuri = settingsRepository.findByName("baseuri");

      final String entryUrl =
          (baseuri == null ? "" : baseuri.getValue())
              + PATH_USERS
              + owner.getUsername()
              + PATH_ENTRY
              + entry.getId();

      final QueueMail mail = new QueueMail();
      if (mailfrom != null) mail.setFrom(mailfrom.getValue());
      else mail.setFrom("root@localhost");
      mail.setTo(owner.getUserContact().getEmail());
      mail.setBody(mailBody(author, subject, body, entryUrl, entry.getId()));

      mail.setSubject("JustJournal: Comment Notification");
      mail.setPurpose("comment_notify");
      queueMailRepository.save(mail);
    } catch (final Exception e) {
      log.error("Could not send mail: " + e.getMessage());
    }
  }

  private static String mailBody(
      final String author,
      final String subject,
      final String body,
      final String entryUrl,
      final int entryId) {
    // TODO: site name should be stored in settings not hard coded
    return author
        + " said: \n"
        + "Subject: "
        + subject
        + "\n"
        + body
        + "\n\nIn response to:\n"
        + entryUrl
        + "\n\n"
        + "From here, you can:\n\n"
        + "View all comments to this entry: "
        + entryUrl
        + "\n\n"
        + "Reply at the webpage: http://www.justjournal.com/comment/"
        + entryId
        + "\n\n-- JustJournal.com\n\n"
        + "(If you would prefer not to get these updates,"
        + " edit the entry to disable comment notifications.)\n";
  }
}
