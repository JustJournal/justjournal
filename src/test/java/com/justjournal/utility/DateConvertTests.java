/*-
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2023 Lucas Holt
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

import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DateConvertTests {

    /**
     * Parse in a fixed zone so the expected values don't depend on the machine's time zone; they
     * were written for midnight US Eastern.
     */
    private static Date parse(String date) throws ParseException {
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        return sdf.parse(date);
    }

    @Test
    void testEncode3339() throws ParseException {
        Date dateObject = parse("2014-02-11");

        var result = DateConvert.encode3339(dateObject);
        assertEquals("2014-02-11T05:00:00.000Z", result);

        result = DateConvert.encode3339();
        assertNotNull(result);
    }

    @Test
    void testDecode3339() throws ParseException {
        var result = DateConvert.decode3339("2014-02-11T05:00:00.000Z");
        assertNotNull(result);
    }

    @Test
    void testEncode822() throws ParseException {
        Date dateObject = parse("2014-02-11");

        var result = DateConvert.encode822(dateObject);
        assertEquals("Tue, 11 Feb 2014 05:00:00 +0000", result);
    }

    @Test
    void testEncode822Tz() throws ParseException {
        Date dateObject = parse("2014-02-11");

        var result = DateConvert.encode822(dateObject, TimeZone.getTimeZone("EST"));
        assertEquals("Tue, 11 Feb 2014 00:00:00 -0500", result);
    }
}
