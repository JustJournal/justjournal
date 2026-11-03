package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The disabled accounts are read once and kept until invalidated or out of date. */
class DisabledAccountsTests {

  private UserRepository userRepository;
  private DisabledAccounts disabledAccounts;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    disabledAccounts = new DisabledAccounts(userRepository);
  }

  private static List<Object[]> rows(final Object[]... rows) {
    return new ArrayList<>(List.of(rows));
  }

  @Test
  void knowsAccountsByIdAndUsernameInAnyCase() {
    when(userRepository.findDisabledAccounts()).thenReturn(rows(new Object[] {7, "Mallory"}));

    assertTrue(disabledAccounts.isDisabled(7));
    assertTrue(disabledAccounts.isDisabled("mallory"));
    assertTrue(disabledAccounts.isDisabled("MALLORY"));
    assertFalse(disabledAccounts.isDisabled(5));
    assertFalse(disabledAccounts.isDisabled("ada"));
    assertFalse(disabledAccounts.isDisabled(0));
    assertFalse(disabledAccounts.isDisabled(null));
    assertFalse(disabledAccounts.isDisabled(""));

    // one read serves every check
    verify(userRepository, times(1)).findDisabledAccounts();
  }

  @Test
  void invalidate_readsTheDatabaseAgain() {
    when(userRepository.findDisabledAccounts())
        .thenReturn(rows())
        .thenReturn(rows(new Object[] {7, "mallory"}));

    assertFalse(disabledAccounts.isDisabled(7));
    assertFalse(disabledAccounts.isDisabled(7));

    disabledAccounts.invalidate();

    assertTrue(disabledAccounts.isDisabled(7));
    verify(userRepository, times(2)).findDisabledAccounts();
  }

  @Test
  void databaseFailure_keepsWhatWasKnown() {
    when(userRepository.findDisabledAccounts())
        .thenReturn(rows(new Object[] {7, "mallory"}))
        .thenThrow(new IllegalStateException("db"));
    assertTrue(disabledAccounts.isDisabled(7));

    disabledAccounts.invalidate();

    assertTrue(disabledAccounts.isDisabled(7));
    // and isn't asked again on every check while the database is away
    assertTrue(disabledAccounts.isDisabled(7));
    verify(userRepository, times(2)).findDisabledAccounts();
  }

  @Test
  void databaseFailure_beforeAnythingIsKnown_treatsNobodyAsDisabled() {
    when(userRepository.findDisabledAccounts()).thenThrow(new IllegalStateException("db"));

    assertFalse(disabledAccounts.isDisabled(7));
  }
}
