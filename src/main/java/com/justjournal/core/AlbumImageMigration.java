package com.justjournal.core;

import com.justjournal.services.AlbumImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Copies pictures still stored in the database to MinIO. Runs shortly after startup, then daily to
 * retry any that failed. The database copies are kept; user_images.migrated records which were
 * copied and checked.
 */
@Slf4j
@Component
@Profile("!test")
public class AlbumImageMigration {

  private final AlbumImageService albumImageService;

  public AlbumImageMigration(final AlbumImageService albumImageService) {
    this.albumImageService = albumImageService;
  }

  @Scheduled(fixedDelay = 1000 * 60 * 60 * 24, initialDelay = 1000 * 60 * 2)
  public void run() {
    try {
      albumImageService.migrateBlobs();
    } catch (final RuntimeException e) {
      log.error("Could not copy pictures to MinIO", e);
    }
  }
}
