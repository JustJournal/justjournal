package com.justjournal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.natche.gravatarjavaclient.enums.GravatarRating;
import com.justjournal.model.AvatarSource;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.ImageService;
import com.justjournal.services.ImageStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class GravatarFetcherTests {

  private ImageStorageService imageStorageService;
  private UserPicRepository userPicRepository;
  private UserRepository userRepository;
  private PlatformTransactionManager transactionManager;

  /** Whether a transaction was open, at each Gravatar fetch. */
  private final List<Boolean> openDuringFetch = new ArrayList<>();

  private int openTransactions;
  private GravatarFetcher fetcher;

  @BeforeEach
  void setUp() {
    imageStorageService = mock(ImageStorageService.class);
    userPicRepository = mock(UserPicRepository.class);
    userRepository = mock(UserRepository.class);
    transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any()))
        .thenAnswer(
            invocation -> {
              openTransactions++;
              return new SimpleTransactionStatus();
            });
    doAnswer(
            invocation -> {
              openTransactions--;
              return null;
            })
        .when(transactionManager)
        .commit(any());

    fetcher =
        new GravatarFetcher(
            imageStorageService,
            new ImageService(),
            userPicRepository,
            userRepository,
            new TransactionTemplate(transactionManager)) {
          @Override
          byte[] getGravatar(final String email) {
            openDuringFetch.add(openTransactions > 0);
            return email.startsWith("none") ? new byte[0] : new byte[] {1, 2, 3};
          }
        };
    fetcher.fetchPauseMs = 0;
  }

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  private User user(final int id, final String email, final boolean hasAvatar) {
    final User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    final UserContact contact = new UserContact();
    contact.setEmail(email);
    when(user.getUserContact()).thenReturn(contact);
    when(userRepository.findById(id)).thenReturn(Optional.of(user));
    when(userPicRepository.existsById(id)).thenReturn(hasAvatar);
    return user;
  }

  @Test
  void run_fetchesOnlyForUsersWithoutAvatar_oneTransactionEach() throws Exception {
    final User withAvatar = user(1, "a@example.com", true);
    final User without = user(2, "b@example.com", false);
    final User noGravatar = user(3, "none@example.com", false);
    when(userRepository.findAll()).thenReturn(List.of(withAvatar, without, noGravatar));

    fetcher.run();

    verify(imageStorageService)
        .uploadAvatar(eq(2), eq("image/jpeg"), eq(AvatarSource.GRAVATAR), any());
    verify(imageStorageService, times(1)).uploadAvatar(anyInt(), any(), any(), any());
    // one to list the users, then one per user
    verify(transactionManager, times(4)).getTransaction(any());
    assertEquals(List.of(false, false), openDuringFetch, "no transaction open while fetching");
  }

  @Test
  void run_oneUserFailing_othersStillFetched() throws Exception {
    final User broken = mock(User.class);
    when(broken.getId()).thenReturn(1);
    when(userPicRepository.existsById(1)).thenThrow(new IllegalStateException("db"));
    final User fine = user(2, "b@example.com", false);
    when(userRepository.findAll()).thenReturn(List.of(broken, fine));

    fetcher.run();

    verify(imageStorageService)
        .uploadAvatar(eq(2), eq("image/jpeg"), eq(AvatarSource.GRAVATAR), any());
  }

  @Test
  void run_interrupted_stops() throws Exception {
    final User first = user(1, "a@example.com", false);
    final User second = user(2, "b@example.com", false);
    when(userRepository.findAll()).thenReturn(List.of(first, second));
    Thread.currentThread().interrupt();

    fetcher.run();

    assertEquals(1, openDuringFetch.size());
    verify(userRepository, never()).findById(2);
    assertTrue(Thread.currentThread().isInterrupted());
  }

  @Test
  void run_noEmail_skipped() throws Exception {
    final User noEmail = user(1, "", false);
    when(userRepository.findAll()).thenReturn(List.of(noEmail));

    fetcher.run();

    assertTrue(openDuringFetch.isEmpty());
  }

  @Test
  void gravatarRequest_asksForPgRatedPicturesOnly() {
    final var request = GravatarFetcher.gravatarRequest("someone@example.com");

    assertEquals(GravatarRating.PG, request.getRating());
    assertTrue(request.getRequestUrl().contains("r=pg"), request.getRequestUrl());
  }
}
