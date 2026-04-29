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


import com.justjournal.utility.DateConvert;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;

/**
 * Represent a date in just journal. Allows the conversion of dates between java Date objects and
 * MySQL representations of dates.
 *
 * <p>TODO: implement time zones completely. Add items to interface.
 *
 * @author Lucas Holt
 * @version $Id: DateTimeBean.java,v 1.11 2012/06/23 18:15:31 laffer1 Exp $
 * @since 1.0
 */
@Slf4j
public final class DateTimeBean implements DateTime {

  // default is epoch -1 sec
  private int day = 31;
  private int month = 12;
  private int year = 1969;
  private int hour = 23;
  private int minutes = 59;
  private final TimeZone tz = TimeZone.getTimeZone("UTC");

  public DateTimeBean() {
    super();
  }

  // extra case we have a java date already
  public DateTimeBean(final java.util.Date date) {
    super();
    this.set(date);
  }

  /**
   * Add to a date using fields just like Calendar
   *
   * @param field
   * @param count
   */
  public void add(int field, int count) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(this.toDate());
    calendar.add(field, count);
    this.set(calendar.getTime());
  }

  @Override
  public int getDay() {
    return this.day;
  }

  @Override
  public void setDay(final int day) {
    if (day < 1) throw new IllegalArgumentException("illegal day");

    if (day > 31) throw new IllegalArgumentException("illegal day");

    this.day = day;
  }

  @Override
  public int getMonth() {
    return this.month;
  }

  @Override
  public void setMonth(final int month) {
    if (month < 1) throw new IllegalArgumentException("illegal month");

    if (month > 12) throw new IllegalArgumentException("illegal month");

    this.month = month;
  }

  @Override
  public int getYear() {
    return this.year;
  }

  @Override
  public void setYear(final int year) {
    if (year < 1970) throw new IllegalArgumentException("illegal year");

    this.year = year;
  }

  @Override
  public int getHour() {
    return this.hour;
  }

  @Override
  public void setHour(int hour) {
    if (hour == 24) hour = 0;

    if (hour < 0) throw new IllegalArgumentException("illegal hour");

    if (hour > 23) throw new IllegalArgumentException("illegal hour");

    this.hour = hour;
  }

  @Override
  public int getMinutes() {
    return this.minutes;
  }

  @Override
  public void setMinutes(final int minutes) {
    if (minutes < 0) throw new IllegalArgumentException("illegal minutes");

    if (minutes > 59) throw new IllegalArgumentException("illegal minutes");

    this.minutes = minutes;
  }

  @Override
  public void set(final String mysqlDate) throws java.text.ParseException {

    log.debug("Set date by mysql date: " + mysqlDate);

    final java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    final java.util.Date myDate = fmt.parse(mysqlDate);
    final java.util.GregorianCalendar calendarg =
        new java.util.GregorianCalendar(java.util.TimeZone.getDefault());

    calendarg.setTime(myDate);
    year = calendarg.get(Calendar.YEAR);
    month = calendarg.get(Calendar.MONTH) + 1; // months are zero based in java!  DUMB!
    day = calendarg.get(Calendar.DAY_OF_MONTH);
    hour = calendarg.get(Calendar.HOUR_OF_DAY);
    minutes = calendarg.get(Calendar.MINUTE);

    log.trace("Internal values: " + year + "-" + month + "-" + day + " " + hour + ":" + minutes);
  }

  @Override
  public void set(final java.util.Date date) {
    final java.util.GregorianCalendar calendarg =
        new java.util.GregorianCalendar(java.util.TimeZone.getDefault());

    calendarg.setTime(date);
    year = calendarg.get(Calendar.YEAR);
    month = calendarg.get(Calendar.MONTH) + 1; // not sure about this one
    day = calendarg.get(Calendar.DAY_OF_MONTH);
    hour = calendarg.get(Calendar.HOUR_OF_DAY);
    minutes = calendarg.get(Calendar.MINUTE);

    log.trace("Internal values: " + year + "-" + month + "-" + day + " " + hour + ":" + minutes);
  }

  @Override
  public String toPubDate() {
    return DateConvert.encode822(toDate());
  }

  @Override
  public String toRFC3339() {
    return DateConvert.encode3339(toDate());
  }

  public String toBlogDate() {
    final java.util.GregorianCalendar cal =
        new java.util.GregorianCalendar(year, month - 1, day, hour, minutes);
    final SimpleDateFormat formatmydate = new SimpleDateFormat("EEE, d MMM yyyy");
    formatmydate.setTimeZone(tz);
    return formatmydate.format(cal.getTime());
  }

  public String toBlogTime() {
    final java.util.GregorianCalendar cal =
        new java.util.GregorianCalendar(year, month - 1, day, hour, minutes);
    final SimpleDateFormat formatmytime = new SimpleDateFormat("h:mm a");
    formatmytime.setTimeZone(tz);
    return formatmytime.format(cal.getTime());
  }

  /**
   * Get the date represented by this instance with the time zone set.
   *
   * @return java Date with tz
   */
  @Override
  public Date toDate() {
    final java.util.GregorianCalendar cal =
        new java.util.GregorianCalendar(year, month - 1, day, hour, minutes);
    cal.setTimeZone(TimeZone.getDefault());
    return cal.getTime();
  }

  /**
   * Retrieves the raw date without time zone set
   *
   * @return raw date
   */
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    // 2004-01-04 14:24:18

    sb.append(Integer.toString(year));
    sb.append("-");

    if (month < 10) sb.append("0");

    sb.append(Integer.toString(month));
    sb.append("-");

    if (day < 10) sb.append("0");

    sb.append(Integer.toString(day));
    sb.append(" ");

    if (hour < 10) sb.append("0");

    sb.append(Integer.toString(hour));
    sb.append(":");

    if (minutes < 10) sb.append("0");

    sb.append(Integer.toString(minutes));

    return sb.toString();
  }
}
