package com.justjournal.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.justjournal.model.EntryLink;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.repository.EntryLinkRepository;
import com.justjournal.services.LinkPreviewService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkPreviewRefreshTests {

  private EntryLinkRepository repository;
  private LinkPreviewService service;
  private LinkPreviewRefresh refresh;

  @BeforeEach
  void setUp() {
    repository = mock(EntryLinkRepository.class);
    service = mock(LinkPreviewService.class);
    refresh = new LinkPreviewRefresh(repository, service);
    refresh.setEnabled(true);
  }

  private static EntryLink link(final int id) {
    final EntryLink link = new EntryLink();
    link.setId(id);
    return link;
  }

  @Test
  void fetchesOnlyTheLinksItClaims() {
    when(repository.findByPreviewStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            eq(LinkPreviewStatus.PENDING), any(), any()))
        .thenReturn(List.of(link(1), link(2)));
    when(repository.claim(eq(1), any(), any())).thenReturn(1);
    when(repository.claim(eq(2), any(), any())).thenReturn(0);

    refresh.run();

    verify(service).refresh(1);
    verify(service, never()).refresh(2);
  }

  @Test
  void oneBrokenLinkDoesNotStopTheBatch() {
    when(repository.findByPreviewStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            eq(LinkPreviewStatus.PENDING), any(), any()))
        .thenReturn(List.of(link(1), link(2)));
    when(repository.claim(anyInt(), any(), any())).thenReturn(1);
    doThrow(new IllegalStateException("boom")).when(service).refresh(1);

    refresh.run();

    verify(service).refresh(2);
  }

  @Test
  void doesNothingWhenOff() {
    refresh.setEnabled(false);

    refresh.run();

    verifyNoInteractions(repository, service);
  }
}
