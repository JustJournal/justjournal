package com.justjournal.services;

import com.justjournal.repository.UserRepository;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Which accounts an administrator has disabled, for checks that run on every request: logging out
 * an open session of a disabled account, and answering 404 for its journal. Disabled accounts are
 * few, so all of them are kept in memory and read again from the database every {@link #TTL}, or
 * straight away once {@link #invalidate()} is called after an account is disabled or enabled. On
 * a second server the change shows up within the TTL.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class DisabledAccounts {

  static final Duration TTL = Duration.ofSeconds(30);

  private record Snapshot(Set<Integer> ids, Set<String> usernames, long loadedAt) {}

  private final UserRepository userRepository;

  private volatile Snapshot snapshot;

  public DisabledAccounts(final UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /** @return true if the user id belongs to a disabled account */
  public boolean isDisabled(final int userId) {
    return userId > 0 && current().ids().contains(userId);
  }

  /** @return true if the username, in any case, belongs to a disabled account */
  public boolean isDisabled(final String username) {
    return username != null
        && !username.isEmpty()
        && current().usernames().contains(username.toLowerCase(Locale.ROOT));
  }

  /**
   * Mark what is known as out of date, so the next check reads the database again. It is kept
   * only in case that read fails.
   */
  public void invalidate() {
    final Snapshot known = snapshot;
    if (known != null) snapshot = new Snapshot(known.ids(), known.usernames(), 0);
  }

  private Snapshot current() {
    final Snapshot known = snapshot;
    if (fresh(known)) return known;
    return load();
  }

  private static boolean fresh(final Snapshot known) {
    return known != null && System.currentTimeMillis() - known.loadedAt() < TTL.toMillis();
  }

  private synchronized Snapshot load() {
    // another thread may have loaded it while this one waited
    final Snapshot stale = snapshot;
    if (fresh(stale)) return stale;

    final long now = System.currentTimeMillis();
    try {
      final Set<Integer> ids = new HashSet<>();
      final Set<String> usernames = new HashSet<>();
      for (final Object[] row : userRepository.findDisabledAccounts()) {
        ids.add(((Number) row[0]).intValue());
        usernames.add(((String) row[1]).toLowerCase(Locale.ROOT));
      }
      final Snapshot loaded = new Snapshot(Set.copyOf(ids), Set.copyOf(usernames), now);
      snapshot = loaded;
      return loaded;
    } catch (final RuntimeException e) {
      // keep using what was known for another TTL rather than failing every request while the
      // database is away; logins still check the account itself
      log.error("Could not read the disabled accounts", e);
      if (stale == null) return new Snapshot(Set.of(), Set.of(), now);
      final Snapshot kept = new Snapshot(stale.ids(), stale.usernames(), now);
      snapshot = kept;
      return kept;
    }
  }
}
