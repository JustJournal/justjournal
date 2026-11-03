package com.justjournal.services;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.services.activitypub.DeliveryService;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration: finding accounts, and disabling or enabling them. A disabled account
 * can't log in, its open sessions are logged out, and its journal, feeds, pictures and fediverse
 * actor are hidden; nothing is deleted, so enabling it restores all of that.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class AdminAccountService {

  static final int PAGE_SIZE = 50;

  /** Cache {@link UserService#getPublicMembers()} fills. */
  static final String MEMBERS_CACHE = "members";

  private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(10);

  private final UserRepository userRepository;

  private final DisabledAccounts disabledAccounts;

  private final DeliveryService deliveryService;

  private final RecentBlogsRepository recentBlogsRepository;

  private final CacheManager cacheManager;

  public AdminAccountService(
      final UserRepository userRepository,
      final DisabledAccounts disabledAccounts,
      final DeliveryService deliveryService,
      final RecentBlogsRepository recentBlogsRepository,
      final CacheManager cacheManager) {
    this.userRepository = userRepository;
    this.disabledAccounts = disabledAccounts;
    this.deliveryService = deliveryService;
    this.recentBlogsRepository = recentBlogsRepository;
    this.cacheManager = cacheManager;
  }

  /**
   * One page of accounts by username.
   *
   * @param query text the username contains, ignoring case; blank for every account
   * @param page page number, from 0
   */
  @Transactional(readOnly = true)
  public Page<User> search(final String query, final int page) {
    final PageRequest request =
        PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Order.asc("username")));
    final String text = query == null ? "" : query.trim();
    if (text.isEmpty()) return userRepository.findAll(request);
    return userRepository.findByUsernameContainingIgnoreCase(text, request);
  }

  /**
   * Disable an account. What is still queued for the fediverse from it is dropped; nothing is sent
   * to its followers, so enabling it later picks up where it left off.
   *
   * @param userId account to disable
   * @param admin administrator doing it, for the log
   * @return the account
   * @throws NotFoundException if there is no such account
   */
  @Transactional
  public User disable(final int userId, final String admin) {
    final User user = find(userId);
    if (user.isDisabled()) return user;

    user.setDisabled(true);
    userRepository.save(user);
    final int dropped = deliveryService.cancelPending(user);
    log.info(
        "Admin {} disabled account {} ({}); dropped {} queued deliveries",
        admin,
        user.getUsername(),
        user.getId(),
        dropped);
    StoredImageFiles.afterCommit(this::forgetPublicCopies);
    return user;
  }

  /**
   * Enable a disabled account again.
   *
   * @param userId account to enable
   * @param admin administrator doing it, for the log
   * @return the account
   * @throws NotFoundException if there is no such account
   */
  @Transactional
  public User enable(final int userId, final String admin) {
    final User user = find(userId);
    if (!user.isDisabled()) return user;

    user.setDisabled(false);
    userRepository.save(user);
    log.info("Admin {} enabled account {} ({})", admin, user.getUsername(), user.getId());
    StoredImageFiles.afterCommit(this::forgetPublicCopies);
    return user;
  }

  private User find(final int userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("No account " + userId));
  }

  /**
   * Drop what was worked out while the account was in its old state: the in-memory list of
   * disabled accounts sessions and journal addresses are checked against, the member list, and
   * the recent posts feed.
   */
  private void forgetPublicCopies() {
    disabledAccounts.invalidate();

    try {
      final Cache members = cacheManager.getCache(MEMBERS_CACHE);
      if (members != null) members.clear();
    } catch (final RuntimeException e) {
      log.error("Could not clear the member list cache", e);
    }

    try {
      recentBlogsRepository.delete().block(REDIS_TIMEOUT);
    } catch (final RuntimeException e) {
      log.error("Could not clear the recent posts feed; it runs out within minutes", e);
    }
  }
}
