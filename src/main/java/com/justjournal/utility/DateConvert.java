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
package com.justjournal.utility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Date format utilities
 *
 * @author Lucas Holt
 */
public final class DateConvert {
  private static final int TZ_8601_OFFSET = 6;

  private DateConvert() {}

  private static SimpleDateFormat getDateFormat822() {
    return new SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US);
  }

  private static SimpleDateFormat getDateFormat3339() {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
  }

  private static SimpleDateFormat getDateFormat8601() {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
  }

  @NotNull
  public static String encode822() {
    SimpleDateFormat df822 = getDateFormat822();
    df822.setTimeZone(TimeZone.getTimeZone("GMT"));
    return df822.format(new Date());
  }

  @NotNull
  public static String encode822(@NotNull final Date data) {
    SimpleDateFormat df822 = getDateFormat822();
    df822.setTimeZone(TimeZone.getTimeZone("GMT"));
    return df822.format(data);
  }

  @NotNull
  public static String encode822(@NotNull final Date data, @NotNull final TimeZone tz) {
    SimpleDateFormat df822 = getDateFormat822();

    df822.setTimeZone(tz);
    return df822.format(data);
  }

  /*
  I was working on an Atom (http://www.w3.org/2005/Atom) parser and discovered that I
  could not parse dates in the format defined by RFC 3339 using the  SimpleDateFormat
  class. The  reason was the ':' in the time  zone. This code strips out the colon if
  it's there and tries four different formats on the resulting string depending on if
  it has a  time zone, or if it has a  fractional second part.  There is a probably a
  better way  to do this, and a more proper way.  But this is a really small addition
  to a  codebase  (You don't  need a jar, just throw  this  function in  some  static
  Utility class if you have one).

  Feel free to use this in your code, but I'd appreciate it if you keep this note  in
  the code if you distribute it.  Thanks!

  For  people  who might  be  googling: The date  format  parsed  by  this  goes  by:
  atomDateConstruct,  xsd:dateTime,  RFC3339  and  is compatable with: ISO.8601.1988,
  W3C.NOTE-datetime-19980827  and  W3C.REC-xmlschema-2-20041028   (that  I  know  of)


  Copyright 2007, Chad Okere (ceothrow1 at gmail dotcom)
  OMG NO WARRENTY EXPRESSED OR IMPLIED!!!1
  */
  @Nullable
  public static Date decode3339(@NotNull String input) throws java.text.ParseException {
    Date d;

    // if there is no time zone, we don't need to do any special parsing.
    if (input.endsWith("Z")) {
      try {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // spec for RFC3339
        d = s.parse(input);
      } catch (final java.text.ParseException pe) { // try again with optional decimals
        SimpleDateFormat s =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"); // spec for RFC3339 with fractional seconds
        s.setLenient(true);
        d = s.parse(input);
      }
      return d;
    }

    // step one, split off the timezone.
    String firstpart = input.substring(0, input.lastIndexOf('-'));
    String secondpart = input.substring(input.lastIndexOf('-'));

    // step two, remove the colon from the timezone offset
    secondpart =
        secondpart.substring(0, secondpart.indexOf(':'))
            + secondpart.substring(secondpart.indexOf(':') + 1);
    input = firstpart + secondpart;
    SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"); // spec for RFC3339
    try {
      d = s.parse(input);
    } catch (java.text.ParseException pe) { // try again with optional decimals
      s =
          new SimpleDateFormat(
              "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ"); // spec for RFC3339 (with fractional
      // seconds)
      s.setLenient(true);
      d = s.parse(input);
    }
    return d;
  }

  @NotNull
  public static String encode3339() {
    final SimpleDateFormat df3339 = getDateFormat3339();
    df3339.setTimeZone(TimeZone.getTimeZone("UTC"));
    return df3339.format(new Date());
  }

  @NotNull
  public static String encode3339(@NotNull final Date data) {
    final SimpleDateFormat df3339 = getDateFormat3339();
    df3339.setTimeZone(TimeZone.getTimeZone("UTC"));
    return df3339.format(data);
  }

  @Nullable
  public static Date decode8601(@NotNull final String input) throws java.text.ParseException {
    final SimpleDateFormat df8601 = getDateFormat8601();
    String date = input;
    if (date.endsWith("Z")) {
      date = date.substring(0, date.length() - 1) + "GMT-00:00";
    } else {
      date =
          date.substring(0, date.length() - TZ_8601_OFFSET)
              + "GMT"
              + date.substring(date.length() - TZ_8601_OFFSET);
    }

    return df8601.parse(date);
  }

  @NotNull
  public static String encode8601() {
    return encode8601(new Date());
  }

  @NotNull
  public static String encode8601(@NotNull final Date data) {
    final SimpleDateFormat df8601 = getDateFormat8601();
    df8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    final String s = df8601.format(data);

    String result =
        s.substring(0, s.length() - 9) + s.substring(s.length() - TZ_8601_OFFSET);
    result = result.replace("UTC", "+00:00");

    return result;
  }
}
