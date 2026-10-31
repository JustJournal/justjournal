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
package com.justjournal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.justjournal.utility.DateConvert;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** DateTimeBean holds a date to the minute and formats it for feeds and blog pages. */
class DateTimeBeanTests {

  @Test
  void defaultsToTheLastMinuteBeforeTheEpoch() {
    final DateTimeBean bean = new DateTimeBean();

    assertEquals(1969, bean.getYear());
    assertEquals(12, bean.getMonth());
    assertEquals(31, bean.getDay());
    assertEquals(23, bean.getHour());
    assertEquals(59, bean.getMinutes());
    assertEquals("1969-12-31 23:59", bean.toString());
  }

  @Test
  void settersValidateTheirRanges() {
    final DateTimeBean bean = new DateTimeBean();
    assertThrows(IllegalArgumentException.class, () -> bean.setDay(0));
    assertThrows(IllegalArgumentException.class, () -> bean.setDay(32));
    assertThrows(IllegalArgumentException.class, () -> bean.setMonth(0));
    assertThrows(IllegalArgumentException.class, () -> bean.setMonth(13));
    assertThrows(IllegalArgumentException.class, () -> bean.setYear(1969));
    assertThrows(IllegalArgumentException.class, () -> bean.setHour(-1));
    assertThrows(IllegalArgumentException.class, () -> bean.setHour(25));
    assertThrows(IllegalArgumentException.class, () -> bean.setMinutes(-1));
    assertThrows(IllegalArgumentException.class, () -> bean.setMinutes(60));

    bean.setYear(2024);
    bean.setMonth(3);
    bean.setDay(5);
    bean.setHour(24);
    bean.setMinutes(7);

    assertEquals(0, bean.getHour());
    assertEquals("2024-03-05 00:07", bean.toString());
  }

  @Test
  void setFromADate_readsEveryField_andToDateGivesItBack() {
    final Date date = new GregorianCalendar(2024, Calendar.MARCH, 20, 10, 30).getTime();

    final DateTimeBean bean = new DateTimeBean(date);

    assertEquals(2024, bean.getYear());
    assertEquals(3, bean.getMonth());
    assertEquals(20, bean.getDay());
    assertEquals(10, bean.getHour());
    assertEquals(30, bean.getMinutes());
    assertEquals(date, bean.toDate());
  }

  @Test
  void setFromAMysqlDate_dropsTheSeconds() throws Exception {
    final DateTimeBean bean = new DateTimeBean();

    bean.set("2024-03-20 10:30:15");

    assertEquals("2024-03-20 10:30", bean.toString());
    assertThrows(java.text.ParseException.class, () -> bean.set("not a date"));
  }

  @Test
  void add_movesTheDateByACalendarField() {
    final DateTimeBean bean = new DateTimeBean(new GregorianCalendar(2024, Calendar.MARCH, 20, 10, 30).getTime());

    bean.add(Calendar.DAY_OF_MONTH, 12);
    assertEquals("2024-04-01 10:30", bean.toString());

    bean.add(Calendar.HOUR_OF_DAY, -11);
    assertEquals("2024-03-31 23:30", bean.toString());
  }

  @Test
  void feedDatesUseTheSharedConverters() {
    final DateTimeBean bean = new DateTimeBean(new GregorianCalendar(2024, Calendar.MARCH, 20, 10, 30).getTime());

    assertEquals(DateConvert.encode822(bean.toDate()), bean.toPubDate());
    assertEquals(DateConvert.encode3339(bean.toDate()), bean.toRFC3339());
  }

  @Test
  void blogDateAndTime_areFormattedInUtc() {
    final DateTimeBean bean = new DateTimeBean(new GregorianCalendar(2024, Calendar.MARCH, 20, 15, 5).getTime());
    final Date local = new GregorianCalendar(2024, Calendar.MARCH, 20, 15, 5).getTime();
    final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
    timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    assertEquals(dateFormat.format(local), bean.toBlogDate());
    assertEquals(timeFormat.format(local), bean.toBlogTime());
    assertTrue(bean.toBlogDate().matches("\\p{L}+, \\d{1,2} \\p{L}+\\.? 2024"), bean.toBlogDate());
    assertTrue(bean.toBlogTime().matches("\\d{1,2}:\\d{2} \\S+"), bean.toBlogTime());
  }
}
