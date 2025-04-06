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


import lombok.Getter;
import lombok.Setter;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Represents a calendar month of journal entries. Each item stores the number of journal entries on
 * a particular day in the month as an integer.
 *
 * @author Lucas Holt
 * @version $Id: CalMonth.java,v 1.4 2006/07/28 14:01:06 laffer1 Exp $
 */
public final class CalMonth {
  @Setter
  @Getter
  private int[] storage = null;
  int monthid = 0;
  private Date baseDate;
  private final GregorianCalendar calendarG = new java.util.GregorianCalendar();

  /**
   * Creates an instance with all the required properties set.
   *
   * @param monthid the month this object represents
   * @param storage an integer array of # of entries
   * @param baseDate initial date
   */
  public CalMonth(final int monthid, final int[] storage, final Date baseDate) {
    this.storage = storage;
    this.monthid = monthid;
    this.baseDate = baseDate;
    calendarG.setTime(baseDate);
  }

  public int getMonthId() {
    return this.monthid;
  }

  public void setMonthId(final int monthid) {
    this.monthid = monthid;
  }

    /**
   * Get the initial base date
   *
   * @return
   */
  public Date getBaseDate() {
    return this.baseDate;
  }

  /**
   * Set the base date
   *
   * @param baseDate
   */
  public void setBaseDate(final Date baseDate) {
    this.baseDate = baseDate;
  }

  public int getYear() {
    return calendarG.get(Calendar.YEAR);
  }

  public int getFirstDayInWeek() {
    return calendarG.get(Calendar.DAY_OF_WEEK);
  }

  public int getMonth() {
    return calendarG.get(Calendar.MONTH);
  }
}
