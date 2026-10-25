package com.justjournal.core;

import static org.mockito.Mockito.*;

import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.EntryStatisticService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsRefreshTests {

  @Mock private UserRepository userRepository;

  @Mock private EntryStatisticService entryStatisticService;

  @InjectMocks private StatisticsRefresh statisticsRefresh;

  private static User user(final String name) {
    final User u = new User();
    u.setUsername(name);
    return u;
  }

  @Test
  void run_computesEachUserSeparately_andKeepsGoingAfterAFailure() {
    final User a = user("alice");
    final User b = user("bobby");
    final User c = user("carol");
    when(userRepository.findAll()).thenReturn(List.of(a, b, c));
    doThrow(new IllegalStateException("boom")).when(entryStatisticService).compute(b);

    statisticsRefresh.run();

    verify(entryStatisticService).compute(a);
    verify(entryStatisticService).compute(b);
    verify(entryStatisticService).compute(c);
  }
}
