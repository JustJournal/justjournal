package com.justjournal.utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.mock;

class ETagTests {

    @Test
    void testWriteFromString_null() {
        var resp = mock(HttpServletResponse.class);
        var etag = new ETag(resp);
        Assertions.assertThrows(IllegalArgumentException.class, () -> etag.writeFromString(null));
    }

    @Test
    void testWrite_null() {
        var resp = mock(HttpServletResponse.class);
        var etag = new ETag(resp);
        Assertions.assertThrows(IllegalArgumentException.class, () -> etag.write(null));
    }

    @Test
    void writeFromString_setsAQuotedMd5Header() {
        var resp = new org.springframework.mock.web.MockHttpServletResponse();

        new ETag(resp).writeFromString("hello");

        Assertions.assertEquals("\"5d41402abc4b2a76b9719d911017c592\"", resp.getHeader("ETag"));
    }

    @Test
    void write_padsThirtyOneCharacterHashes() {
        var resp = new org.springframework.mock.web.MockHttpServletResponse();

        new ETag(resp).write("1234567890123456789012345678901");
        Assertions.assertEquals("\"01234567890123456789012345678901\"", resp.getHeader("ETag"));

        new ETag(resp).write("abc");
        Assertions.assertEquals("\"abc\"", resp.getHeader("ETag"));
    }
}
