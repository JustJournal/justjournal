package com.justjournal.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The entry.modified column is NOT NULL, and Hibernate writes the field's value
 * rather than letting the database default apply.
 */
class EntryTests {

    @Test
    void newEntryHasModified() {
        Assertions.assertNotNull(new Entry().getModified());
    }

    @Test
    void subjectHtmlIsStripped() {
        Entry entry = new Entry();
        entry.setSubject("<b>Tech News:</b> <script>alert(1)</script>Story <textarea>");
        Assertions.assertEquals("Tech News: alert(1)Story", entry.getSubject());
    }

    @Test
    void subjectNullBecomesEmpty() {
        Entry entry = new Entry();
        entry.setSubject(null);
        Assertions.assertEquals("", entry.getSubject());
    }

    @Test
    void plainSubjectUnchanged() {
        Entry entry = new Entry();
        entry.setSubject("Release plans & CVE list");
        Assertions.assertEquals("Release plans & CVE list", entry.getSubject());
    }
}
