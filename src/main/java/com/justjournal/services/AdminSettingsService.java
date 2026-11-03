package com.justjournal.services;

import com.justjournal.core.Settings;
import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.repository.SettingsRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lets the administrator change the value of an existing row in the settings table. Rows are never
 * created, renamed or removed here. A saved change is applied to the running site straight away.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class AdminSettingsService {

  /** Length of the settings.value column. */
  public static final int MAX_VALUE_LENGTH = 100;

  /** Settings the loader reads with {@code Integer.parseInt}, lower case. */
  private static final Set<String> INTEGER_SETTINGS = Set.of("mailport", "tzoffset");

  /** Settings the loader reads as {@code true}/{@code false}, lower case. */
  private static final Set<String> BOOLEAN_SETTINGS =
      Set.of(
          "siteenable",
          "sitesearch",
          "sitedirectory",
          "siterss",
          "mailenable",
          "commentenable",
          "commentmailenable",
          "commentenableanonymous",
          "tzlocalize",
          "tzusegmt",
          "userallownew");

  /** Name fragments marking a setting whose value must never reach the browser or the log. */
  private static final List<String> SECRET_MARKERS = List.of("pass", "secret", "key", "token");

  private final SettingsRepository settingsRepository;

  private final Settings settings;

  public AdminSettingsService(
      final SettingsRepository settingsRepository, final Settings settings) {
    this.settingsRepository = settingsRepository;
    this.settings = settings;
  }

  /** A settings row as the admin page shows it; a secret's value is always null. */
  public record SettingView(Integer id, String name, String value, boolean secret) {}

  /** @return true if the setting's value is a credential that must not be shown */
  public static boolean isSecret(final String name) {
    if (name == null) return false;
    final String lower = name.toLowerCase(Locale.ROOT);
    return SECRET_MARKERS.stream().anyMatch(lower::contains);
  }

  /** @return every settings row sorted by name, with secret values left out */
  @Transactional(readOnly = true)
  public List<SettingView> list() {
    return settingsRepository.findAll(Sort.by("name")).stream()
        .map(
            row -> {
              final boolean secret = isSecret(row.getName());
              return new SettingView(
                  row.getId(), row.getName(), secret ? null : row.getValue(), secret);
            })
        .toList();
  }

  /**
   * Change the value of one existing setting. The value is trimmed; a blank value for a secret
   * setting keeps the current one.
   *
   * @param id the settings row
   * @param value the new value
   * @return the setting's name
   * @throws NotFoundException if there is no such row
   * @throws BadRequestException if the value isn't valid for the setting
   */
  @Transactional
  public String update(final int id, final String value) {
    final com.justjournal.model.Settings row =
        settingsRepository.findById(id).orElseThrow(() -> new NotFoundException("No such setting"));
    final String name = row.getName();

    if (value == null) throw new BadRequestException("A value is required for " + name + ".");
    final String trimmed = value.trim();

    if (isSecret(name) && trimmed.isEmpty()) {
      log.info("Admin left setting {} unchanged", name);
      return name;
    }

    row.setValue(validate(name, trimmed));
    settingsRepository.save(row);
    log.info("Admin changed setting {}", name);
    reloadAfterCommit();
    return name;
  }

  /** @return the value to store, or throws if the loader couldn't read it */
  private static String validate(final String name, final String value) {
    if (value.length() > MAX_VALUE_LENGTH) {
      throw new BadRequestException(
          name + " can be at most " + MAX_VALUE_LENGTH + " characters long.");
    }
    final String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (INTEGER_SETTINGS.contains(key)) {
      try {
        Integer.parseInt(value);
      } catch (final NumberFormatException e) {
        throw new BadRequestException(name + " must be a whole number.");
      }
      return value;
    }
    if (BOOLEAN_SETTINGS.contains(key)) {
      if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
        return value.toLowerCase(Locale.ROOT);
      }
      throw new BadRequestException(name + " must be true or false.");
    }
    return value;
  }

  /** Re-read the settings once the change is committed, so the site never runs on a rollback. */
  private void reloadAfterCommit() {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      settings.reload();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            settings.reload();
          }
        });
  }
}
