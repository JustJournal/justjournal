package com.justjournal;

import com.justjournal.model.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CalTests {

    @Test
    void testInitializeWithEmptyEntries() {
        Collection<Entry> emptyEntries = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> new Cal(emptyEntries));
    }

    @Test
    void testCalculateEntryCountsWithLeapYear() {
        // Create a list of entries with dates in a leap year
        List<Entry> entries = new ArrayList<>();
        entries.add(createEntry("2020-02-28"));
        entries.add(createEntry("2020-02-29"));
        entries.add(createEntry("2020-03-01"));

        Cal cal = new Cal(entries);

        // Check if the monthList has the correct number of months
        assertEquals(2, cal.monthList.size());

        // Check February (index 1)
        CalMonth february = cal.monthList.get(0);
        assertEquals(1, february.monthid);
        assertEquals(29, february.getStorage().length);
        assertEquals(1, february.getStorage()[27]); // Feb 28
        assertEquals(1, february.getStorage()[28]); // Feb 29

        // Check March (index 2)
        CalMonth march = cal.monthList.get(1);
        assertEquals(2, march.monthid);
        assertEquals(31, march.getStorage().length);
        assertEquals(1, march.getStorage()[0]); // Mar 1
    }

    private Entry createEntry(String dateString) {
        Entry entry = new Entry();
        try {
            entry.setDate(new SimpleDateFormat("yyyy-MM-dd").parse(dateString));
        } catch (ParseException e) {
            fail("Failed to parse date: " + dateString);
        }
        return entry;
    }

    @Test
    void testRenderFullCalendar() {
        // Create a sample entry
        Entry entry = new Entry();
        entry.setDate(new Date()); // Set to current date

        // Create a Cal instance with a single entry
        Cal cal = new Cal(Collections.singletonList(entry));

        // Render the calendar
        String renderedCalendar = cal.render();

        // Assert that the rendered calendar contains expected HTML elements
        assertTrue(renderedCalendar.contains("<!-- Calendar Output -->"));
        assertTrue(renderedCalendar.contains("<table class=\"fullcalendar\""));
        assertTrue(renderedCalendar.contains("<caption>"));
        assertTrue(renderedCalendar.contains("<thead>"));
        assertTrue(renderedCalendar.contains("<th class=\"fullcalendarth\">"));
        assertTrue(renderedCalendar.contains("<tbody>"));
        assertTrue(renderedCalendar.contains("<td class=\"fullcalendarrow\">"));
        assertTrue(renderedCalendar.contains("<td class=\"fullcalendarsub\""));
        assertTrue(renderedCalendar.contains("View Subjects</a>"));

        // Assert that all days of the week are present
        for (String day : new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}) {
            assertTrue(renderedCalendar.contains(day));
        }

        // Assert that the calendar contains a link for the day of the entry
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        String entryDateString = sdf.format(entry.getDate());
        assertTrue(renderedCalendar.contains("href=\"" + entryDateString + "\""));
    }

    @Test
    void testRenderMiniCalendar() {
        // Create a sample entry
        Entry entry = new Entry();
        entry.setDate(new Date()); // Set to current date

        // Create a Cal instance with a single entry
        Cal cal = new Cal(Collections.singletonList(entry));
        cal.setBaseUrl("/users/testuser/"); // Set a base URL for testing

        // Render the mini calendar
        String renderedMiniCalendar = cal.renderMini();

        // Assert that the rendered mini calendar contains expected HTML elements
        assertTrue(renderedMiniCalendar.contains("<!-- Calendar Output -->"));
        assertTrue(renderedMiniCalendar.contains("<table class=\"minicalendar\""));
        assertTrue(renderedMiniCalendar.contains("<caption>"));
        assertTrue(renderedMiniCalendar.contains("<thead>"));
        assertTrue(renderedMiniCalendar.contains("<th class=\"minicalendarth\">"));
        assertTrue(renderedMiniCalendar.contains("<tbody>"));
        assertTrue(renderedMiniCalendar.contains("<td class=\"minicalendarrow\">"));
        assertTrue(renderedMiniCalendar.contains("<td class=\"minicalendarsub\""));
        assertTrue(renderedMiniCalendar.contains("View Subjects</a>"));

        // Assert that all days of the week are present in their abbreviated form
        for (String day : new String[]{"S", "M", "T", "W", "R", "F", "S"}) {
            assertTrue(renderedMiniCalendar.contains(day));
        }

        // Assert that the mini calendar contains a link for the day of the entry
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        String entryDateString = sdf.format(entry.getDate());
        assertTrue(renderedMiniCalendar.contains("href=\"/users/testuser/" + entryDateString + "\""));

        // Assert that the "View Subjects" link is present with the correct URL
        assertTrue(renderedMiniCalendar.contains("href=\"/users/testuser/" + entryDateString.substring(0, 7) + "\">View Subjects</a>"));
    }

    @Test
    void testCalculateFirstDayOfWeekForEachMonth() {
        // Create a list of entries with specific dates
        List<Entry> entries = new ArrayList<>();
        entries.add(createEntry("2023-01-01")); // Sunday
        entries.add(createEntry("2023-02-01")); // Wednesday
        entries.add(createEntry("2023-03-01")); // Wednesday
        entries.add(createEntry("2023-04-01")); // Saturday

        Cal cal = new Cal(entries);

        // Check if the monthList has the correct number of months
        assertEquals(4, cal.monthList.size());

        // Check the first day of the week for each month
        assertEquals(1, cal.monthList.get(0).getFirstDayInWeek()); // January starts on Sunday (1)
        assertEquals(4, cal.monthList.get(1).getFirstDayInWeek()); // February starts on Wednesday (4)
        assertEquals(4, cal.monthList.get(2).getFirstDayInWeek()); // March starts on Wednesday (4)
        assertEquals(7, cal.monthList.get(3).getFirstDayInWeek()); // April starts on Saturday (7)
    }
}
