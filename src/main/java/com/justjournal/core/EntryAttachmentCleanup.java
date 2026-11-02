package com.justjournal.core;

import com.justjournal.services.EntryAttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes post pictures that were uploaded but never saved with a post, and pictures and links
 * left behind when their post went away some way that didn't clean up after it.
 */
@Slf4j
@Component
public class EntryAttachmentCleanup {

  private final EntryAttachmentService entryAttachmentService;

  public EntryAttachmentCleanup(final EntryAttachmentService entryAttachmentService) {
    this.entryAttachmentService = entryAttachmentService;
  }

  @Scheduled(fixedDelay = 1000 * 60 * 60, initialDelay = 1000 * 60 * 10)
  public void run() {
    try {
      final int removed = entryAttachmentService.removeOrphans();
      if (removed > 0) log.info("Removed {} post attachments that no post uses", removed);
    } catch (final RuntimeException e) {
      log.error("Could not remove unused post attachments", e);
    }
  }
}
