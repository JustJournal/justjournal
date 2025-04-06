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
package com.justjournal;


import com.justjournal.model.Entry;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Storage for calendar months.
 *
 * @author Lucas Holt
 * @version $Id: Cal.java,v 1.12 2012/07/04 18:48:53 laffer1 Exp $
 * @see CalMonth
 */
@Slf4j
public final class Cal {
  public static final int MONTHS_IN_YEAR = 12;
  final List<CalMonth> monthList = new ArrayList<>(MONTHS_IN_YEAR);
  private final SimpleDateFormat shortDate = new SimpleDateFormat("yyyy-MM-dd");
  private final String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
  private final String[] daysSmall = {"S", "M", "T", "W", "R", "F", "S"};
  private final String[] months = {
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
  };
  private final Collection<Entry> entries;
  @Setter
  private String baseUrl = null;

  public Cal(final Collection<Entry> entries) {
    if (CollectionUtils.isEmpty(entries)) {
      // TODO: make this class handle empty entries rather than do this hack in the constructor.
      log.error("Entries collection is empty");
      throw new IllegalArgumentException("Entry collection cannot be null or empty");
    }
    this.entries = entries;
    this.calculateEntryCounts();
  }

  private void calculateEntryCounts() {
    final java.util.GregorianCalendar calendarg = new java.util.GregorianCalendar();
    int month = -1; // first time through we want to fire the change code, this is 0 based.
    int day;
    int year = 0;
    int[] monthPostCt = null;

    try {
      for (Entry entryTo : entries) {
        final java.util.Date currentDate = entryTo.getDate();

        calendarg.setTime(currentDate);
        year = calendarg.get(java.util.Calendar.YEAR);

        // intentionally using -1 for first pass through the list
        if (month == calendarg.get(java.util.Calendar.MONTH)) {
          // month didn't change
          day = calendarg.get(java.util.Calendar.DAY_OF_MONTH);
          if (monthPostCt != null) monthPostCt[day - 1]++;
        } else {
          if (monthPostCt != null) {
            // get first day of month (falls on)
            final ParsePosition pos2 = new ParsePosition(0);
            // "yyyy-MM-dd"
            final java.util.Date baseDate = shortDate.parse(year + "-" + (month + 1) + "-01", pos2);

            monthList.add(new CalMonth(month, monthPostCt, baseDate));
          }

          monthPostCt = new int[calendarg.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)];

          day = calendarg.get(java.util.Calendar.DAY_OF_MONTH);
          monthPostCt[day - 1]++;

          month = calendarg.get(java.util.Calendar.MONTH);
        }
      }

      final ParsePosition pos2 = new ParsePosition(0);
      // "yyyy-MM-dd"
      final java.util.Date baseDate = shortDate.parse(year + "-" + (month + 1) + "-01", pos2);
      final int[] n = monthPostCt;
      monthList.add(new CalMonth(month, n, baseDate));
    } catch (final Exception e) {
      log.debug("Exception raised in calculateEntryCounts", e);
    }
  }

  private void tableRowOpen(final StringBuilder sb) {
    sb.append("<tr>\n");
  }

  private void tableRowClose(final StringBuilder sb) {
    sb.append("</tr>\n");
  }

  private void tableClose(final StringBuilder sb) {
    sb.append("\t</table>\n\n");
  }

  private void tableBodyOpen(final StringBuilder sb) {
    sb.append("\t\t<tbody>\n");
  }

  private void tableBodyClose(final StringBuilder sb) {
    sb.append("\t\t</tbody>\n");
  }

  private void calStartComment(final StringBuilder sb) {
    sb.append("<!-- Calendar Output -->\n");
  }

  public String render() {

    final StringBuilder sb = new StringBuilder();
    CalMonth o;
    final Iterator<CalMonth> itr = monthList.listIterator();

    calStartComment(sb);

    for (int i = 0, n = monthList.size(); i < n; i++) {
      o = itr.next();
      sb.append("<table class=\"fullcalendar\" cellpadding=\"1\" cellspacing=\"1\">\n");

      caption(sb, o.getYear(), o.monthid);

      sb.append("<thead>\n");
      tableRowOpen(sb);
      for (int x = 0; x < 7; x++) {
        sb.append("\t<th class=\"fullcalendarth\">");
        sb.append(days[x]);
        sb.append("</th>\n");
      }
      tableRowClose(sb);
      sb.append("</thead>\n");

      tableBodyOpen(sb);

      int dayinweek;
      boolean blnFirstTime = true; // first time through
      sb.append("<tr>\n");

      if (o.getFirstDayInWeek() > 1)
        sb.append("\t<td class=\"fullcalendaroffrow\" colspan=\"")
            .append(o.getFirstDayInWeek() - 1)
            .append("\"></td>");

      dayinweek = o.getFirstDayInWeek() - 1;

      for (int y = 0; y < o.getStorage().length; y++) {
        if (dayinweek == 0 && !blnFirstTime) {
          tableRowOpen(sb);
        }

        sb.append("\t<td class=\"fullcalendarrow\"><strong>");
        sb.append(y + 1);
        sb.append("</strong><br /><span style=\"float: right;\">");
        if (o.getStorage()[y] == 0) {
          sb.append("&nbsp;");
        } else {
          sb.append("<a href=\"");

          // year
          sb.append(o.getYear());
          sb.append("/");

          // month
          if ((o.monthid + 1) < 10) {
            sb.append("0");
          }
          sb.append(o.monthid + 1);
          sb.append("/");

          // day
          if ((y + 1) < 10) {
            sb.append("0");
          }
          sb.append(y + 1);
          sb.append("\">");
          sb.append(o.getStorage()[y]);
          sb.append("</a>");
        }
        sb.append("</span></td>\n");

        if (dayinweek == 6) {
          tableRowClose(sb);
          dayinweek = 0;
          blnFirstTime = false; // hiding this here makes it execute less.
        } else {
          dayinweek++;
        }
      }

      dayInWeek(sb, dayinweek, false);
      viewSubjectsLink(sb, o, false);
      tableBodyClose(sb);
      tableClose(sb);
    }
    return sb.toString();
  }

  private void viewSubjectsLink(StringBuilder sb, CalMonth o, boolean miniCalendar) {
    String cssClass = miniCalendar? "minicalendarsub" : "fullcalendarsub";

    tableRowOpen(sb);
    sb.append("\t<td class=\"").append(cssClass).append("\" colspan=\"7\"><a href=\"");
    if (miniCalendar)
      sb.append(baseUrl);
    sb.append(o.getYear());
    sb.append("/");
    if ((o.monthid + 1) < 10) {
      sb.append("0");
    }
    sb.append(o.monthid + 1);
    sb.append("\">View Subjects</a></td>\n");
    tableRowClose(sb);
  }

  private void dayInWeek(StringBuilder sb, int dayinweek, boolean miniCalendar) {
    String cssClass = miniCalendar? "minicalendaroffrow" : "fullcalendaroffrow";
    if (dayinweek <= 6 && dayinweek != 0) {
      // this is seven because colspan is 1 based.  why do the
      // extra addition +1
      sb.append("\t<td class=\"").append(cssClass).append("\" colspan=\"")
              .append(7 - dayinweek)
              .append(" \"></td>");
      tableRowClose(sb);
    }
  }

  private void caption(final StringBuilder sb, int year, int month) {
    sb.append("\t\t<caption>");
    sb.append(months[month]);
    sb.append(" ");
    sb.append(year);
    sb.append("</caption>\n");
  }

  public String renderMini() {
    final StringBuilder sb = new StringBuilder();

    calStartComment(sb);

    for (final CalMonth o : monthList) {
      sb.append("\t<table class=\"minicalendar\" cellpadding=\"1\" cellspacing=\"1\">\n");

      caption(sb, o.getYear(), o.monthid);

      sb.append("\t\t<thead>\n\t\t<tr>\n");
      for (int x = 0; x < 7; x++) {
        sb.append("\t\t\t<th class=\"minicalendarth\">");
        sb.append(daysSmall[x]);
        sb.append("</th>\n");
      }
      sb.append("\t\t</tr>\n\t\t</thead>\n");

      int dayinweek;
      boolean blnFirstTime = true; // first time through
      tableBodyOpen(sb);
      tableRowOpen(sb);

      if (o.getFirstDayInWeek() > 1)
        sb.append("\t\t<td class=\"minicalendaroffrow\" colspan=\"")
            .append(o.getFirstDayInWeek() - 1)
            .append("\"></td>\n");

      dayinweek = o.getFirstDayInWeek() - 1;

      for (int y = 0; y < o.getStorage().length; y++) {
        if (dayinweek == 0 && !blnFirstTime) {
          tableRowOpen(sb);
        }

        sb.append("\t\t<td class=\"minicalendarrow\">");

        if (o.getStorage()[y] == 0) {
          sb.append(y + 1);
        } else {
          sb.append("<a href=\"");
          sb.append(baseUrl);

          // year
          sb.append(o.getYear());
          sb.append("/");

          // month
          if ((o.monthid + 1) < 10) {
            sb.append("0");
          }
          sb.append(o.monthid + 1);
          sb.append("/");

          // day
          if ((y + 1) < 10) {
            sb.append("0");
          }
          sb.append(y + 1);
          sb.append("\">");
          sb.append(y + 1);
          sb.append("</a>");
        }
        sb.append("</td>\n");

        if (dayinweek == 6) {
          sb.append("\t\t</tr>\n");
          dayinweek = 0;
          blnFirstTime = false; // hiding this here makes it execute less.
        } else {
          dayinweek++;
        }
      }

      dayInWeek(sb, dayinweek, true);

      viewSubjectsLink(sb, o, true);
      tableBodyClose(sb);
      tableClose(sb);
    }
    return sb.toString();
  }
}
