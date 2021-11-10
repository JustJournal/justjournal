/*
Copyright (c) 2003-2021, Lucas Holt
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
package com.justjournal.services;


import com.justjournal.core.UserContext;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Entry;
import com.justjournal.model.Journal;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.SecurityRepository;
import com.justjournal.utility.HTMLUtil;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfContentByte;
import java.awt.*;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** @author Lucas Holt */
@Service
@Slf4j
public class AbstractFormatService {

  @Autowired private EntryRepository entryRepository;

  @Autowired private SecurityRepository securityRepository;

  public void format(final UserContext uc, final Document document) throws ServiceException {
    final Font helvetica14 = new Font(Font.HELVETICA, 14.0F);
    final Font helvetica12 = new Font(Font.HELVETICA, 12.0F);
    final Font helvetica8 = new Font(Font.HELVETICA, 8.0F);
    final Font times11 = new Font(Font.TIMES_ROMAN, 11.0F);
    final Color blue = new Color(0x00, 0x00, 0xFF);
    final Color lightBlue = new Color(0xF, 0xF2, 0xF2);

    try {
      document.open();
      document.add(new Paragraph(""));
      Chunk chunk =
          new Chunk(new ArrayList<Journal>(uc.getBlogUser().getJournals()).get(0).getName());
      chunk.setTextRenderMode(PdfContentByte.TEXT_RENDER_MODE_STROKE, 0.4f, blue);
      document.add(chunk);
      document.add(Chunk.NEWLINE);

      final java.util.List<Entry> entries;

      if (uc.isAuthBlog()) entries = entryRepository.findByUsername(uc.getBlogUser().getUsername());
      else
        entries =
            entryRepository.findByUsernameAndSecurity(
                uc.getBlogUser().getUsername(), securityRepository.findByName("public"));

      // Format the current time.
      final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm");
      final SimpleDateFormat formatmydate = new SimpleDateFormat("EEE, d MMM yyyy");
      final SimpleDateFormat formatmytime = new SimpleDateFormat("h:mm a");
      String lastDate = "";
      String curDate;

      for (final Entry o : entries) {
        // Parse the previous string back into a Date.
        final ParsePosition pos = new ParsePosition(0);
        final Date currentDate = formatter.parse(o.getDate().toString(), pos);

        curDate = formatmydate.format(currentDate);

        if (curDate.compareTo(lastDate) != 0) {
          document.add(new Paragraph(curDate, helvetica14));
          lastDate = curDate;
        }

        document.add(new Paragraph(formatmytime.format(currentDate), helvetica12));
        document.add(Chunk.NEWLINE);
        chunk = new Chunk(o.getSubject());
        chunk.setTextRenderMode(PdfContentByte.TEXT_RENDER_MODE_FILL, 0.3F, lightBlue);
        document.add(chunk);
        document.add(Chunk.NEWLINE);

        document.add(new Paragraph(HTMLUtil.textFromHTML(o.getBody()), times11));
        document.add(Chunk.NEWLINE);

        if (o.getSecurity().getId() == 0)
          document.add(new Paragraph("Security: " + "Private", helvetica8));
        else if (o.getSecurity().getId() == 1)
          document.add(new Paragraph("Security: " + "Friends", helvetica8));
        else document.add(new Paragraph("Security: " + "Public", helvetica8));

        document.add(new Chunk("Location: " + o.getLocation().getTitle(), helvetica8));
        document.add(Chunk.NEWLINE);
        document.add(new Chunk("Mood: " + o.getMood().getTitle(), helvetica8));
        document.add(Chunk.NEWLINE);
        document.add(new Chunk("Music: " + o.getMusic(), helvetica8));
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
      }
    } catch (final DocumentException e) {
      log.error(e.getMessage(), e);
    }
  }
}
