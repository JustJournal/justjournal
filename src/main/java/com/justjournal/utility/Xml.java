/*
Copyright (c) 2003-2006, 2010 Lucas Holt
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


/**
 * XML Utilities
 *
 * @author Lucas Holt
 * @version $Id: Xml.java,v 1.9 2010/02/05 03:11:24 laffer1 Exp $ User: laffer1 Date: Sep 24, 2003 Time: 11:39:50 AM
 */
public final class Xml {

    /**
     * converts characters that are special in xml to their equivalents.
     * <p/>
     * This does not alter elements that may be part of DTDs like &nbsp; (formerly) but &nbsp; is converted to a plain
     * space.
     * <p/>
     * It currently does not handle numerical escapes as defined in XML either. &#xA0; etc
     *
     * @param input dirty xml unescaped document
     * @return A string with xml friendly escaped sequences.
     */
    public
    static String cleanString(final String input) {
        if (input == null) return "";

        String work = input;

        // warning, if this is already correct,
        // the &amp replacement could really screw things
        // up.  Need to somehow verify the document is ok
        // or at least that & is alone?

        /* This really sucks, but if &nbsp; is used, it will really screw things up.  This is the most common
           character to get added to an XML document we can't control.  This also assumes latin1.
         */
        if (input.contains("&nbsp;"))
            work = work.replaceAll("&nbsp;", " ");

        work = StringUtil.replace(work, '&', "&amp;");
        work = StringUtil.replace(work, '"', "&quot;");
        work = StringUtil.replace(work, '<', "&lt;");
        work = StringUtil.replace(work, '>', "&gt;");
        // bastards.. this is valid in XML but not in HTML work = StringUtil.replace(work, '\'', "&apos;");
        work = StringUtil.replace(work, '\'', "&#39;");

        return work;
    }
}
