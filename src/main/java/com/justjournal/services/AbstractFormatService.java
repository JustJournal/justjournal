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

import com.justjournal.core.UserContext;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Entry;
import com.justjournal.model.Security;
import com.justjournal.repository.EntryRepository;
import com.justjournal.utility.HTMLUtil;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.openpdf.text.*;
import org.openpdf.text.Font;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.FontSelector;
import org.openpdf.text.pdf.PdfContentByte;

/** @author Lucas Holt */
@Slf4j
public abstract class AbstractFormatService {

  /**
   * Embedded Unicode fonts, so text outside latin1 isn't dropped as it was with the built-in PDF
   * fonts. Characters missing from Noto Sans fall back to Noto Sans Symbols 2.
   */
  private static final BaseFont TEXT_FONT = loadFont("/fonts/NotoSans-Regular.ttf");

  private static final BaseFont SYMBOL_FONT = loadFont("/fonts/NotoSansSymbols2-Regular.ttf");

  private final EntryRepository entryRepository;

  private final MarkdownService markdownService;

  protected AbstractFormatService(EntryRepository entryRepository, MarkdownService markdownService) {
    this.entryRepository = entryRepository;
    this.markdownService = markdownService;
  }

  private static BaseFont loadFont(final String path) {
    try (InputStream in = AbstractFormatService.class.getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing font " + path);
      return BaseFont.createFont(
          path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
    } catch (final IOException e) {
      throw new UncheckedIOException("Could not load font " + path, e);
    }
  }

  /** Text in the given size, each character in the first font that has it. */
  static Phrase text(final String value, final float size) {
    final FontSelector selector = new FontSelector();
    selector.addFont(new Font(TEXT_FONT, size));
    selector.addFont(new Font(SYMBOL_FONT, size));
    return selector.process(value == null ? "" : value);
  }

  /** As {@link #text}, drawn with the given render mode, like a {@link Chunk} would be. */
  private static Phrase text(
      final String value, final float size, final int renderMode, final float width, final Color color) {
    final Phrase phrase = text(value, size);
    for (final Element element : phrase) {
      if (element instanceof Chunk chunk) chunk.setTextRenderMode(renderMode, width, color);
    }
    return phrase;
  }

  private void validate(final UserContext uc, final Document document) throws ServiceException {
    if (uc == null || document == null) {
      throw new IllegalArgumentException("Invalid input parameters.");
    }

    if (uc.getBlogUser() == null) {
      throw new ServiceException("user not found.");
    }

    if (uc.getBlogUser().getJournals().isEmpty()) {
      throw new ServiceException("No journals found.");
    }
  }

  public void format(final UserContext uc, final Document document) throws ServiceException {
    validate(uc, document);

    var blogUser = uc.getBlogUser();
    var journals = blogUser.getJournals();
    var journalName = journals.stream().findFirst();

    final Color blue = new Color(0x00, 0x00, 0xFF);
    final Color lightBlue = new Color(0xF, 0xF2, 0xF2);

    try {
      document.open();
      document.add(new Paragraph(""));

      if (journalName.isPresent() && StringUtils.isNotBlank(journalName.get().getName())) {
        document.add(
            text(
                journalName.get().getName(),
                Font.DEFAULTSIZE,
                PdfContentByte.TEXT_RENDER_MODE_STROKE,
                0.4f,
                blue));
        document.add(Chunk.NEWLINE);
      }

      final java.util.List<Entry> entries;

      if (uc.isAuthBlog()) {
        entries = entryRepository.findByUsername(blogUser.getUsername());
      } else {
        entries = entryRepository.findByUsernameAndSecurity(
                blogUser.getUsername(), Security.PUBLIC);
      }
      if (entries.isEmpty()) {
        throw new NotFoundException();
      }

      final SimpleDateFormat formatmydate = new SimpleDateFormat("EEE, d MMM yyyy");
      final SimpleDateFormat formatmytime = new SimpleDateFormat("h:mm a");
      String lastDate = "";
      String curDate;

      for (final Entry o : entries) {
        final Date currentDate = o.getDate();

        curDate = formatmydate.format(currentDate);

        if (curDate.compareTo(lastDate) != 0) {
          document.add(new Paragraph(text(curDate, 14.0F)));
          lastDate = curDate;
        }

        document.add(new Paragraph(text(formatmytime.format(currentDate), 12.0F)));
        document.add(Chunk.NEWLINE);
        document.add(
            text(
                o.getSubject(),
                Font.DEFAULTSIZE,
                PdfContentByte.TEXT_RENDER_MODE_FILL,
                0.3F,
                lightBlue));
        document.add(Chunk.NEWLINE);

        switch (o.getFormat()) {
          case HTML:
            document.add(new Paragraph(text(HTMLUtil.textFromHTML(o.getBody()), 11.0F)));
            break;
          case MARKDOWN:
            document.add(
                new Paragraph(text(markdownService.convertToText(o.getBody()), 11.0F)));
            break;
          case TEXT: /* falls through */
          default:
              document.add(new Paragraph(text(o.getBody(), 11.0F)));
        }
        document.add(Chunk.NEWLINE);

        if (o.getSecurity() == Security.PRIVATE)
          document.add(new Paragraph(text("Security: " + "Private", 8.0F)));
        else if (o.getSecurity() == Security.FRIENDS)
          document.add(new Paragraph(text("Security: " + "Friends", 8.0F)));
        else document.add(new Paragraph(text("Security: " + "Public", 8.0F)));

        document.add(text("Location: " + o.getLocation().getTitle(), 8.0F));
        document.add(Chunk.NEWLINE);
        document.add(text("Mood: " + o.getMood().getTitle(), 8.0F));
        document.add(Chunk.NEWLINE);
        document.add(text("Music: " + o.getMusic(), 8.0F));
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
      }
    } catch (final DocumentException e) {
      log.error("Failed to render document (PDF) {}", e.getMessage(), e);
    }
  }
}
