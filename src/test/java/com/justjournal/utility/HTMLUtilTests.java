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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;


/**
 * @author Lucas Holt
 */
class HTMLUtilTests {

    @Test
    void testConvertCharacterEntities() {
        String result = HTMLUtil.convertCharacterEntities("Me &amp; Joe are&nbsp; &lt; &gt;");
        Assertions.assertEquals("Me & Joe are  < >", result);
    }

    @Test
    void testStripHtmlTags() {
        String result =
                HTMLUtil.stripHTMLTags(
                        "<html><body><p>I am a redneck</p> <p><a" + " href=\"foo\">foo</a></body></html>");
        Assertions.assertEquals("I am a redneck foo", result);
    }

    @Test
    void testTextFromHTML() {
        String result = HTMLUtil.textFromHTML("<p>Me &amp; Joe are&nbsp; &lt; &gt;</p>");
        Assertions.assertEquals("Me & Joe are  < >", result);
    }

    @Test
    void testUriToLinkHTTPS() {
        String result = HTMLUtil.uriToLink("https://www.justjournal.com/users/jjsite ");
        Assertions.assertEquals("<a href=\"https://www.justjournal.com/users/jjsite\">https://www.justjournal.com/users/jjsite</a>", result);
    }

    @Test
    void testUriToLinkHttp() {
        String result = HTMLUtil.uriToLink("http://www.justjournal.com/users/jjsite ");
        Assertions.assertEquals("<a href=\"http://www.justjournal.com/users/jjsite\">http://www.justjournal.com/users/jjsite</a>", result);
    }

    @Test
    void testUriToLinkFtp() {
        String result = HTMLUtil.uriToLink("ftp://ftp.midnightbsd.org/ ");
        Assertions.assertEquals("<a href=\"ftp://ftp.midnightbsd.org/\">ftp://ftp.midnightbsd.org/</a>", result);
    }

    @Test
    void testGetURIsSingle() {
        List<String> result =
                HTMLUtil.getURIs(
                        "I am a url pattern https://www.justjournal.com/users/jjsite and i like" + " it.");
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals("https://www.justjournal.com/users/jjsite", result.get(0));
    }

    @Test
    void testDetermineMimeType_valid() {
        var result = HTMLUtil.determineMimeType("application/xhtml+xml", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.0");
        Assertions.assertEquals("application/xhtml+xml", result);
    }

    @Test
    void testDetermineMimeType_w3c() {
        var result = HTMLUtil.determineMimeType("text/html", "W3C_Validator");
        Assertions.assertEquals("application/xhtml+xml", result);
    }

    @Test
    void testDetermineMimeType_nullAccept() {
        var result = HTMLUtil.determineMimeType(null, "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.0");
        Assertions.assertEquals("text/html", result);
    }

    @Test
    void testDetermineMimeType_nullUA() {
        var result = HTMLUtil.determineMimeType("application/xhtml+xml", null);
        Assertions.assertEquals("application/xhtml+xml", result);
    }

    @Test
    void testCleanClosesUnbalancedTags() {
        var result = HTMLUtil.clean("<p>hello <textarea>oops", false);
        Assertions.assertTrue(result.contains("</textarea>"), result);
        Assertions.assertFalse(result.contains("<html"), result);
    }

    @Test
    void testCleanClosesComment() {
        var result = HTMLUtil.clean("<p>hello</p><!-- never closed", false);
        Assertions.assertTrue(result.contains("hello"), result);
        Assertions.assertTrue(!result.contains("<!--") || result.contains("-->"), result);
    }

    @Test
    void testCleanKeepsTextOfUnknownTags() {
        var result = HTMLUtil.clean("<p>Hello</p><music>Jazz</music>", false);
        Assertions.assertTrue(result.contains("<p>Hello</p>"), result);
        Assertions.assertTrue(result.contains("Jazz"), result);
        Assertions.assertFalse(result.contains("<music>"), result);
    }

    @Test
    void testCleanEmpty() {
        Assertions.assertEquals("", HTMLUtil.clean(null, false));
        Assertions.assertEquals("", HTMLUtil.clean("", false));
    }
}
