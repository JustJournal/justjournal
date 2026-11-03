package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.repository.SettingsRepository;
import com.justjournal.services.AdminSettingsService.SettingView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/** The admin can change existing settings values, which are validated and applied live. */
class AdminSettingsServiceTests {

  private SettingsRepository repository;
  private Settings settings;
  private AdminSettingsService service;

  @BeforeEach
  void setUp() {
    repository = mock(SettingsRepository.class);
    settings = mock(Settings.class);
    service = new AdminSettingsService(repository, settings);
  }

  private com.justjournal.model.Settings row(final int id, final String name, final String value) {
    final com.justjournal.model.Settings row = new com.justjournal.model.Settings();
    row.setId(id);
    row.setName(name);
    row.setValue(value);
    when(repository.findById(id)).thenReturn(Optional.of(row));
    return row;
  }

  private void assertRefused(final int id, final String value) {
    assertThrows(BadRequestException.class, () -> service.update(id, value));
    verify(repository, never()).save(any());
    verify(settings, never()).reload();
  }

  @Test
  void list_isSortedByName_andNeverCarriesASecretValue() {
    final List<com.justjournal.model.Settings> rows =
        List.of(
            row(10, "mailPass", "hunter2"),
            row(26, "apiToken", "abc123token"),
            row(9, "mailHost", "smtp.example.com"));
    when(repository.findAll(any(Sort.class))).thenReturn(rows);

    final List<SettingView> views = service.list();

    verify(repository).findAll(Sort.by("name"));
    assertEquals(new SettingView(10, "mailPass", null, true), views.get(0));
    assertEquals(new SettingView(26, "apiToken", null, true), views.get(1));
    assertEquals(new SettingView(9, "mailHost", "smtp.example.com", false), views.get(2));
    assertFalse(views.toString().contains("hunter2"));
    assertFalse(views.toString().contains("abc123token"));
  }

  @Test
  void secretNames_matchPassSecretKeyOrToken_inAnyCase() {
    assertTrue(AdminSettingsService.isSecret("mailPass"));
    assertTrue(AdminSettingsService.isSecret("CLIENT_SECRET"));
    assertTrue(AdminSettingsService.isSecret("apiKey"));
    assertTrue(AdminSettingsService.isSecret("botToken"));
    assertFalse(AdminSettingsService.isSecret("mailUser"));
    assertFalse(AdminSettingsService.isSecret("baseuri"));
    assertFalse(AdminSettingsService.isSecret(null));
  }

  @Test
  void update_savesTheTrimmedValue_andReloadsTheSettings() {
    final com.justjournal.model.Settings row = row(1, "siteName", "Old");

    assertEquals("siteName", service.update(1, "  New Name  "));

    assertEquals("New Name", row.getValue());
    verify(repository).save(row);
    verify(settings).reload();
  }

  @Test
  void update_ofAnUnknownRow_isNotFound_andCreatesNothing() {
    when(repository.findById(99)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.update(99, "x"));
    verify(repository, never()).save(any());
  }

  @Test
  void update_refusesAMissingValue() {
    row(1, "siteName", "Old");
    assertRefused(1, null);
  }

  @Test
  void update_refusesAValueLongerThanTheColumn() {
    row(1, "siteName", "Old");
    assertRefused(1, "x".repeat(101));
  }

  @Test
  void update_acceptsAValueExactlyTheColumnLength_afterTrimming() {
    final com.justjournal.model.Settings row = row(1, "siteName", "Old");

    service.update(1, " " + "x".repeat(100) + " ");

    assertEquals(100, row.getValue().length());
  }

  @Test
  void update_refusesANonIntegerForIntegerSettings() {
    row(11, "mailPort", "25");
    row(23, "tzOffset", "-5");
    assertRefused(11, "lots");
    assertRefused(23, "1.5");
  }

  @Test
  void update_acceptsAnIntegerForIntegerSettings() {
    final com.justjournal.model.Settings row = row(23, "tzOffset", "-5");

    service.update(23, " -8 ");

    assertEquals("-8", row.getValue());
  }

  @Test
  void update_refusesAnythingButTrueOrFalseForBooleanSettings() {
    row(25, "userAllowNew", "true");
    assertRefused(25, "yes");
    assertRefused(25, "");
  }

  @Test
  void update_storesBooleansInLowerCase() {
    final com.justjournal.model.Settings row = row(25, "userAllowNew", "true");

    service.update(25, "FALSE");

    assertEquals("false", row.getValue());
  }

  @Test
  void update_ofASecretWithABlankValue_keepsTheCurrentValue() {
    final com.justjournal.model.Settings row = row(10, "mailPass", "hunter2");

    assertEquals("mailPass", service.update(10, "   "));

    assertEquals("hunter2", row.getValue());
    verify(repository, never()).save(any());
    verify(settings, never()).reload();
  }

  @Test
  void update_ofASecretWithAValue_replacesIt() {
    final com.justjournal.model.Settings row = row(10, "mailPass", "hunter2");

    service.update(10, "correct horse");

    assertEquals("correct horse", row.getValue());
    verify(repository).save(row);
    verify(settings).reload();
  }

  @Test
  void update_errorsForASecret_neverEchoTheValue() {
    row(10, "mailPass", "hunter2");
    final String tooLong = "s3cret".repeat(20);

    final BadRequestException e =
        assertThrows(BadRequestException.class, () -> service.update(10, tooLong));

    assertFalse(e.getMessage().contains("s3cret"));
  }

  @Test
  void list_ofAnEmptyTable_isEmpty() {
    when(repository.findAll(any(Sort.class))).thenReturn(List.of());

    assertTrue(service.list().isEmpty());
  }
}
