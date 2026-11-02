package com.justjournal.core;

import com.justjournal.model.EntryLink;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.repository.EntryLinkRepository;
import com.justjournal.services.LinkPreviewService;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fetches the previews of links attached to posts, a batch a minute. Each link is claimed first,
 * so two servers never fetch the same one; a claim that is never finished runs out after a while
 * and the link is tried again.
 */
@Slf4j
@Component
public class LinkPreviewRefresh {

  static final Duration LEASE = Duration.ofMinutes(10);

  private final EntryLinkRepository entryLinkRepository;
  private final LinkPreviewService linkPreviewService;

  @Setter
  @Value("${link-preview.enabled:false}")
  private boolean enabled;

  @Setter
  @Value("${link-preview.batch-size:10}")
  private int batchSize = 10;

  public LinkPreviewRefresh(
      final EntryLinkRepository entryLinkRepository, final LinkPreviewService linkPreviewService) {
    this.entryLinkRepository = entryLinkRepository;
    this.linkPreviewService = linkPreviewService;
  }

  @Scheduled(fixedDelay = 1000 * 60, initialDelay = 1000 * 60)
  public void run() {
    if (!enabled) return;
    final Date now = new Date();
    final List<EntryLink> due =
        entryLinkRepository.findByPreviewStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            LinkPreviewStatus.PENDING, now, PageRequest.of(0, batchSize));
    final Date lease = new Date(now.getTime() + LEASE.toMillis());
    for (final EntryLink link : due) {
      if (entryLinkRepository.claim(link.getId(), now, lease) != 1) continue;
      try {
        linkPreviewService.refresh(link.getId());
      } catch (final RuntimeException e) {
        log.error("Could not fetch the preview for link {}", link.getId(), e);
      }
    }
  }
}
