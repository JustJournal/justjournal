package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.services.activitypub.DeliveryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;

/** Disabling and enabling accounts, and what is forgotten when they change. */
class AdminAccountServiceTests {

  private UserRepository userRepository;
  private DisabledAccounts disabledAccounts;
  private DeliveryService deliveryService;
  private RecentBlogsRepository recentBlogsRepository;
  private Cache members;
  private AdminAccountService service;
  private User bob;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    disabledAccounts = mock(DisabledAccounts.class);
    deliveryService = mock(DeliveryService.class);
    recentBlogsRepository = mock(RecentBlogsRepository.class);
    final CacheManager cacheManager = mock(CacheManager.class);
    members = mock(Cache.class);
    when(cacheManager.getCache(AdminAccountService.MEMBERS_CACHE)).thenReturn(members);
    when(recentBlogsRepository.delete()).thenReturn(Mono.just(true));
    service =
        new AdminAccountService(
            userRepository, disabledAccounts, deliveryService, recentBlogsRepository, cacheManager);

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    when(userRepository.findById(7)).thenReturn(Optional.of(bob));
  }

  private void verifyPublicCopiesForgotten() {
    verify(disabledAccounts).invalidate();
    verify(members).clear();
    verify(recentBlogsRepository).delete();
  }

  @Test
  void disable_setsTheFlagDropsQueuedDeliveriesAndForgetsCopies() {
    assertSame(bob, service.disable(7, "admin"));

    assertTrue(bob.isDisabled());
    verify(userRepository).save(bob);
    verify(deliveryService).cancelPending(bob);
    verifyPublicCopiesForgotten();
  }

  @Test
  void enable_clearsTheFlagAndForgetsCopies() {
    bob.setDisabled(true);

    assertSame(bob, service.enable(7, "admin"));

    assertFalse(bob.isDisabled());
    verify(userRepository).save(bob);
    verify(deliveryService, never()).cancelPending(any());
    verifyPublicCopiesForgotten();
  }

  @Test
  void disablingADisabledAccount_changesNothing() {
    bob.setDisabled(true);

    service.disable(7, "admin");

    verify(userRepository, never()).save(any());
    verifyNoInteractions(deliveryService, disabledAccounts);
  }

  @Test
  void unknownAccount_isNotFound() {
    when(userRepository.findById(9)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.disable(9, "admin"));
    assertThrows(NotFoundException.class, () -> service.enable(9, "admin"));
    verify(userRepository, never()).save(any());
  }

  @Test
  void redisFailure_doesNotUndoTheChange() {
    when(recentBlogsRepository.delete()).thenReturn(Mono.error(new IllegalStateException("redis")));
    doThrow(new IllegalStateException("redis")).when(members).clear();

    service.disable(7, "admin");

    assertTrue(bob.isDisabled());
    verify(disabledAccounts).invalidate();
  }

  @Test
  void search_blankListsEveryAccountByUsername() {
    final Page<User> all = new PageImpl<>(List.of(bob));
    final PageRequest first =
        PageRequest.of(0, AdminAccountService.PAGE_SIZE, Sort.by(Sort.Order.asc("username")));
    when(userRepository.findAll(first)).thenReturn(all);

    assertSame(all, service.search("  ", -1));
    assertSame(all, service.search(null, 0));
  }

  @Test
  void search_matchesPartOfTheUsername() {
    final Page<User> found = new PageImpl<>(List.of(bob));
    final PageRequest second =
        PageRequest.of(1, AdminAccountService.PAGE_SIZE, Sort.by(Sort.Order.asc("username")));
    when(userRepository.findByUsernameContainingIgnoreCase("bo", second)).thenReturn(found);

    assertSame(found, service.search(" bo ", 1));
  }
}
