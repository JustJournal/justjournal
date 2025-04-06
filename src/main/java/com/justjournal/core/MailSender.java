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


import com.justjournal.model.QueueMail;
import com.justjournal.repository.QueueMailRepository;
import com.justjournal.utility.ForcedAuthenticator;
import java.util.Date;
import java.util.Properties;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Send e-mail notifications for just journal from the mail queue.
 *
 * @author Lucas Holt
 * @version $Id: MailSender.java,v 1.8 2009/03/16 22:10:31 laffer1 Exp $
 */
@Slf4j
@Component
@Profile("!test")
public class MailSender {

  private final QueueMailRepository queueMailRepository;

  private final Settings set;

  public MailSender(QueueMailRepository queueMailRepository, Settings set) {
    this.queueMailRepository = queueMailRepository;
    this.set = set;
  }

  @Scheduled(fixedDelay = 120000, initialDelay = 30000)
  public void send() {
    log.info("MailSender: Init");

    try {
      final Properties props = new Properties();
      props.put("mail.smtp.host", set.getMailHost());
      props.put("mail.smtp.user", set.getMailUser());
      props.put("mail.smtp.auth", "true");

      props.put("mail.smtp.port", set.getMailPort());
      final Session s = Session.getInstance(props, new ForcedAuthenticator(set));

        log.trace("MailSender: {}@{}:{}", set.getMailUser(), set.getMailHost(), set.getMailPort());

      final Iterable<QueueMail> items = queueMailRepository.findAll();

      log.trace("MailSender: Recordset loaded.");

      for (final QueueMail item : items) {
        if (sendMessage(s, item)) {
          queueMailRepository.deleteById(item.getId());
        }
      }
    } catch (final Exception me) {
      log.error("MailSender: Exception", me);
    }
  }

  private boolean sendMessage(final Session s, final QueueMail item) {
    boolean sentok = true;

    try {
      final InternetAddress from = new InternetAddress(item.getFrom());
      final InternetAddress to = new InternetAddress(item.getTo());

      final MimeMessage message = new MimeMessage(s);
      message.setFrom(from);
      message.setRecipient(Message.RecipientType.TO, to);
      message.setSubject(item.getSubject());
      message.setText(item.getBody());
      message.setSentDate(new Date());
      message.saveChanges();

      final Address[] a = {to};
      final Transport t = s.getTransport("smtp");
      t.connect();
      t.sendMessage(message, a);
      t.close();
    } catch (final AddressException e) {
      sentok = false;
      log.error("MailSender: Invalid address. ", e);
    } catch (final MessagingException me) {
      sentok = false;
      log.error("MailSender: Send failed.", me);
    }
    return sentok;
  }
}
