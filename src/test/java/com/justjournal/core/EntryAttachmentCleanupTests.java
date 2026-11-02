package com.justjournal.core;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.services.EntryAttachmentService;
import org.junit.jupiter.api.Test;

class EntryAttachmentCleanupTests {

  @Test
  void removesOrphans() {
    final EntryAttachmentService service = mock(EntryAttachmentService.class);
    when(service.removeOrphans()).thenReturn(2);

    new EntryAttachmentCleanup(service).run();

    verify(service).removeOrphans();
  }

  @Test
  void aFailureIsLoggedNotThrown() {
    final EntryAttachmentService service = mock(EntryAttachmentService.class);
    when(service.removeOrphans()).thenThrow(new IllegalStateException("database down"));

    new EntryAttachmentCleanup(service).run();

    verify(service).removeOrphans();
  }
}
