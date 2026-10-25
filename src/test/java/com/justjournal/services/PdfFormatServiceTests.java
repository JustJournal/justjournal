package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.justjournal.core.UserContext;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.Journal;
import com.justjournal.model.Location;
import com.justjournal.model.Mood;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

@ExtendWith(MockitoExtension.class)
class PdfFormatServiceTests {

  @Mock private EntryRepository entryRepository;

  @Mock private MarkdownService markdownService;

  @InjectMocks private PdfFormatService pdfFormatService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setUsername("testuser");

    final Journal journal = new Journal();
    journal.setName("Test Journal");
    journal.setUser(user);
    user.getJournals().add(journal);
  }

  private Entry entry(final String subject, final String body, final Security security) {
    final Location location = new Location();
    location.setTitle("Home");
    final Mood mood = new Mood();
    mood.setTitle("Happy");

    final Entry entry = new Entry();
    // JPA hands back java.sql.Timestamp, whose toString() is what the formatter parses
    entry.setDate(Timestamp.valueOf("2026-10-24 14:30:00"));
    entry.setSubject(subject);
    entry.setBody(body);
    entry.setFormat(FormatType.TEXT);
    entry.setSecurity(security);
    entry.setLocation(location);
    entry.setMood(mood);
    entry.setMusic("Some Song");
    entry.setUser(user);
    return entry;
  }

  @Test
  void write_shouldProducePdfWithPublicEntries_whenNotOwner() throws Exception {
    when(entryRepository.findByUsernameAndSecurity("testuser", Security.PUBLIC))
        .thenReturn(List.of(entry("Hello PDF", "First entry body", Security.PUBLIC)));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    pdfFormatService.write(new UserContext(user, null), out);

    final byte[] pdf = out.toByteArray();
    assertTrue(pdf.length > 0);
    assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));

    try (PdfReader reader = new PdfReader(pdf)) {
      assertEquals(1, reader.getNumberOfPages());
      final String text = new PdfTextExtractor(reader).getTextFromPage(1);
      assertTrue(text.contains("Test Journal"), text);
      assertTrue(text.contains("Hello PDF"), text);
      assertTrue(text.contains("First entry body"), text);
      assertTrue(text.contains("Security: Public"), text);
      assertTrue(text.contains("Mood: Happy"), text);
    }

    verify(entryRepository, never()).findByUsername(anyString());
  }

  @Test
  void write_shouldIncludePrivateEntries_whenOwner() throws Exception {
    when(entryRepository.findByUsername("testuser"))
        .thenReturn(List.of(entry("Secret", "Private body", Security.PRIVATE)));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    pdfFormatService.write(new UserContext(user, user), out);

    try (PdfReader reader = new PdfReader(out.toByteArray())) {
      final String text = new PdfTextExtractor(reader).getTextFromPage(1);
      assertTrue(text.contains("Private body"), text);
      assertTrue(text.contains("Security: Private"), text);
    }
  }

  @Test
  void write_shouldFormatPlainJavaUtilDate() throws Exception {
    final Entry entry = entry("Plain date", "Body", Security.PUBLIC);
    // not a java.sql.Timestamp: the old toString() round-trip failed to parse this
    final Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-10-24 14:30");
    entry.setDate(date);
    when(entryRepository.findByUsernameAndSecurity("testuser", Security.PUBLIC))
        .thenReturn(List.of(entry));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    pdfFormatService.write(new UserContext(user, null), out);

    try (PdfReader reader = new PdfReader(out.toByteArray())) {
      final String text = new PdfTextExtractor(reader).getTextFromPage(1);
      // the service formats with the default locale, so build the expectation the same way
      assertTrue(text.contains(new SimpleDateFormat("EEE, d MMM yyyy").format(date)), text);
      assertTrue(text.contains(new SimpleDateFormat("h:mm a").format(date)), text);
    }
  }

  @Test
  void write_shouldThrowNotFound_whenNoEntries() {
    when(entryRepository.findByUsernameAndSecurity("testuser", Security.PUBLIC))
        .thenReturn(List.of());

    final UserContext userContext = new UserContext(user, null);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    assertThrows(NotFoundException.class, () -> pdfFormatService.write(userContext, out));
  }

  @Test
  void write_shouldKeepTextOutsideLatin1() throws Exception {
    when(entryRepository.findByUsernameAndSecurity("testuser", Security.PUBLIC))
        .thenReturn(
            List.of(entry("Kočić – Ελληνικά", "Русский текст and a star ★", Security.PUBLIC)));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    pdfFormatService.write(new UserContext(user, null), out);

    final String raw = out.toString(StandardCharsets.ISO_8859_1);
    assertTrue(raw.contains("NotoSans-Regular"), "Noto Sans not embedded");
    assertTrue(raw.contains("NotoSansSymbols2"), "symbol fallback not used for the star");

    try (PdfReader reader = new PdfReader(out.toByteArray())) {
      final String text = new PdfTextExtractor(reader).getTextFromPage(1);
      assertTrue(text.contains("Kočić – Ελληνικά"), text);
      assertTrue(text.contains("Русский текст"), text);
      assertTrue(text.contains("★"), text);
    }
  }
}
