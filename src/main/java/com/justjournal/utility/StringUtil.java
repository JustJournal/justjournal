/*
Copyright (c) 2003, 2005, 2008 Lucas Holt
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

package com.justjournal.utility;


import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A generalized string manipulation library.
 *
 * @author Lucas Holt
 * @version $Id: StringUtil.java,v 1.7 2011/05/29 22:32:59 laffer1 Exp $
 * @since 1.0
 * <p/>
 * Sun Jun 01 2003
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StringUtil {
    
    /**
     * Deletes multiple instances of a specific character in a string
     *
     * @param origin  Original string to modify
     * @param delChar Character to remove from the string
     * @return Modified string
     */
    public
    static String deleteChar(final String origin, final char delChar) {
        final int len = origin.length();
        char[] val = origin.toCharArray();
        char[] buf = new char[len];
        int j = 0;

        for (int i = 0; i < len; ++i) {
            if (val[i] != delChar) {
                buf[j++] = val[i];
            }
        }

        return new String(buf, 0, j);
    }


    /**
     * Replace a certain character in a string with a new substring.
     *
     * @param base Original string to modify
     * @param ch   Character to replace
     * @param str  Modified string after completion.
     * @return The new string
     */
    public static String replace(final String base, final char ch, final String str) {
        return (base.indexOf(ch) < 0) ? base :
                replace(base, String.valueOf(ch), new String[]{str});
    }


    /**
     * Replace certain characters in a string with a new substring.
     *
     * @param base  The original string
     * @param delim The delimiter
     * @param str   the substring
     * @return Modified string after operations.
     */
    public static String replace(final String base, final String delim, final String[] str) {
        final int len = base.length();
        final StringBuilder result = new StringBuilder();

        for (int i = 0; i < len; i++) {
            final char ch = base.charAt(i);
            final int k = delim.indexOf(ch);

            if (k >= 0) {
                result.append(str[k]);
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Checks e-mail addresses for invalid characters. If the address is invalid, false is returned.
     * <p/>
     * Does not support TLD emails at this time. (me@com, foo@google, bar@coke)
     * Does not support internationalized (unicode) domains yet
     *
     * @param address an email address to check
     * @return true if the address is valid.
     */
    public static boolean isEmailValid(final String address) {
        if (address == null)
            return false;

        // based on http://emailregex.com/
        final Pattern p = Pattern.compile("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])");
        final Matcher m = p.matcher(address);

        return m.matches();
    }

    /**
     * Checks a string to find non alpha numeric characters. If found, it returns false.
     *
     * @param input A string to check for alphanumeric characters.
     * @return boolean indicating alphanumeric status
     */
    public static boolean isAlphaNumeric(final String input) {
        if (input == null)
            return false;
        
        final Pattern p = Pattern.compile("[\\w]+");
        final Matcher m = p.matcher(input);

        return m.matches();
    }

    /**
     * Checks a string to find non alpha characters. if found, it returns false.
     *
     * @param input a string to check for alpha chars.
     * @return boolean indicating alpha status
     */
    public static boolean isAlpha(final String input) {
        if (input == null)
            return false;

        final Pattern p = Pattern.compile("[A-Za-z]+");
        final Matcher m = p.matcher(input);

        return m.matches();
    }

    /**
     * Check the length of the string str using the minimum and maximum values provided.
     *
     * @param str       string to check
     * @param minLength the minimum length
     * @param maxLength the max length
     * @return true if the str is between the constraints, false if it violates them.
     */
    public static boolean lengthCheck(final String str, final int minLength, final int maxLength) {
        if (str == null)
            return false;

        final int len = str.length();
        return len >= minLength && len <= maxLength;
    }

}
