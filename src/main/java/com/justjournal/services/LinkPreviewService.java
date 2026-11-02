package com.justjournal.services;

import com.justjournal.model.EntryLink;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.repository.EntryLinkRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.LinkPreviewData;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.LinkSafetyService;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.OpenGraphParser;
import com.justjournal.services.link.SafeHttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fetches the Open Graph card for a link attached to a post: the page's title, summary and site
 * name, and its preview picture, which is shrunk, checked by the content filter and kept in the
 * image store. Runs in the background, so a slow or broken site never holds up saving a post.
 *
 * <p>The link is checked again before every fetch, and each redirect on the way, so a site that
 * turned bad after the post was saved gets no card. Nothing is written unless the row still holds
 * the same link and is still waiting for its preview.
 */
@Slf4j
@Service
public class LinkPreviewService {

  static final Set<String> PAGE_TYPES = Set.of("text/html", "application/xhtml+xml");
  static final Set<String> IMAGE_TYPES =
      Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_GIF_VALUE);

  /** Wait before each try after a failure that might go away; the last one repeats. */
  static final long[] BACKOFF_MINUTES = {5, 30, 120};

  private static final int MAX_REASON_LENGTH = 255;

  /** Reasons that mean the site itself is known to be bad, rather than just unreachable. */
  private static final Set<LinkRejectedException.Reason> UNSAFE =
      Set.of(
          LinkRejectedException.Reason.BLOCKLISTED,
          LinkRejectedException.Reason.SHORTENER,
          LinkRejectedException.Reason.DBL,
          LinkRejectedException.Reason.SAFE_BROWSING);

  private final EntryLinkRepository entryLinkRepository;
  private final LinkSafetyService linkSafetyService;
  private final SafeHttpFetcher fetcher;
  private final OpenGraphParser parser;
  private final ImageService imageService;
  private final ImageModerationService moderation;
  private final StoredImageFiles files;
  private final TransactionTemplate transactionTemplate;
  private final RecentBlogsRepository recentBlogsRepository;

  @Setter
  @Value("${link-preview.max-attempts:3}")
  private int maxAttempts = 3;

  @Setter
  @Value("${link-preview.max-html-bytes:1048576}")
  private int maxHtmlBytes = 1_048_576;

  @Setter
  @Value("${link-preview.max-image-bytes:10485760}")
  private int maxImageBytes = 10_485_760;

  @Setter
  @Value("${link-preview.max-image-pixels:40000000}")
  private long maxImagePixels = 40_000_000L;

  @Setter
  @Value("${link-preview.image-size:600}")
  private int imageSize = 600;

  public LinkPreviewService(
      final EntryLinkRepository entryLinkRepository,
      final LinkSafetyService linkSafetyService,
      final SafeHttpFetcher fetcher,
      final OpenGraphParser parser,
      final ImageService imageService,
      final ImageModerationService moderation,
      final StoredImageFiles files,
      final TransactionTemplate transactionTemplate,
      final RecentBlogsRepository recentBlogsRepository) {
    this.entryLinkRepository = entryLinkRepository;
    this.linkSafetyService = linkSafetyService;
    this.fetcher = fetcher;
    this.parser = parser;
    this.imageService = imageService;
    this.moderation = moderation;
    this.files = files;
    this.transactionTemplate = transactionTemplate;
    this.recentBlogsRepository = recentBlogsRepository;
  }

  /** The preview picture as stored, before it is written to the link's row. */
  record StoredImage(String filename, int width, int height, ImageModerationService.Result verdict) {}

  /**
   * Fetch the preview for one link, if it is still waiting for one.
   *
   * @param linkId the {@code entry_link} row
   */
  public void refresh(final int linkId) {
    final EntryLink link =
        transactionTemplate.execute(status -> entryLinkRepository.findById(linkId).orElse(null));
    if (link == null || link.getPreviewStatus() != LinkPreviewStatus.PENDING) return;
    final String url = link.getUrl();

    final NormalizedUrl normalized;
    final SafeHttpFetcher.Result page;
    try {
      normalized = linkSafetyService.checkForSave(url);
      page = fetcher.fetch(normalized, PAGE_TYPES, maxHtmlBytes);
    } catch (final LinkRejectedException e) {
      refused(link, e);
      return;
    } catch (final FetchException e) {
      if (e.permanent()) failed(link, e.getMessage());
      else retry(link, e.getMessage());
      return;
    } catch (final IOException | RuntimeException e) {
      retry(link, e.getClass().getSimpleName() + ": " + e.getMessage());
      return;
    }

    final LinkPreviewData data = parser.parse(page.body(), page.charset(), page.finalUri());
    final StoredImage image = data.image() == null ? null : storeImage(link, data.image());

    final boolean written =
        update(
            link,
            current -> {
              current.setFinalUrl(truncate(page.finalUri().toString(), 1024));
              current.setTitle(data.title());
              current.setDescription(data.description());
              current.setSiteName(data.siteName());
              if (image != null) {
                current.setImageFilename(image.filename());
                current.setImageWidth(image.width());
                current.setImageHeight(image.height());
                current.setImageModerationStatus(image.verdict().status());
                current.setImageModerationCategory(image.verdict().category());
              }
              current.setPreviewStatus(LinkPreviewStatus.READY);
              current.setFailureReason(null);
              current.setFetchedAt(new Date());
            });
    if (!written) {
      // the link changed or went away while we were fetching
      if (image != null) files.remove(image.filename());
      return;
    }
    log.info("Link preview {} ready{}", linkId, image == null ? " without a picture" : "");
    recentBlogsRepository.delete().subscribe();
  }

  /**
   * Fetch, shrink, judge and store a page's preview picture. Any problem just means a card with no
   * picture.
   *
   * @return the stored picture, or null if there is none to show
   */
  StoredImage storeImage(final EntryLink link, final URI address) {
    try {
      final NormalizedUrl normalized = linkSafetyService.checkHop(address);
      final byte[] data = fetcher.fetch(normalized, IMAGE_TYPES, maxImageBytes).body();
      final ImageService.ImageInfo info = imageService.describe(data);
      if ((long) info.width() * info.height() > maxImagePixels) {
        log.info("Preview picture for link {} is too large: {}x{}", link.getId(), info.width(), info.height());
        return null;
      }
      final byte[] thumbnail = imageService.thumbnail(data, imageSize);
      final ImageModerationService.Result verdict = moderation.moderate(thumbnail, data);
      if (verdict.rejected()) {
        log.info(
            "Preview picture for link {} rejected by the content filter: {}",
            link.getId(),
            verdict.category());
        return null;
      }
      final ImageService.ImageInfo shown = imageService.describe(thumbnail);
      final String filename = "link/" + link.getOwnerId() + "/" + UUID.randomUUID() + ".jpg";
      files.put(filename, MediaType.IMAGE_JPEG_VALUE, thumbnail);
      return new StoredImage(filename, shown.width(), shown.height(), verdict);
    } catch (final Exception e) {
      log.info("No preview picture for link {}: {}", link.getId(), e.getMessage());
      return null;
    }
  }

  /** The link failed a check: a known bad site is hidden, anything else just gets no card. */
  private void refused(final EntryLink link, final LinkRejectedException e) {
    final boolean unsafe = UNSAFE.contains(e.reason());
    log.info(
        "Link preview {} refused ({}); {}",
        link.getId(),
        e.reason(),
        unsafe ? "hiding the link" : "no preview");
    final boolean written =
        update(
            link,
            current -> {
              current.setPreviewStatus(unsafe ? LinkPreviewStatus.BLOCKED : LinkPreviewStatus.FAILED);
              current.setFailureReason(e.reason().name());
            });
    if (written && unsafe) recentBlogsRepository.delete().subscribe();
  }

  private void failed(final EntryLink link, final String reason) {
    log.info("Link preview {} failed: {}", link.getId(), reason);
    update(
        link,
        current -> {
          current.setPreviewStatus(LinkPreviewStatus.FAILED);
          current.setFailureReason(truncate(reason, MAX_REASON_LENGTH));
        });
  }

  /** A failure that might go away: try again later, or give up after the last attempt. */
  private void retry(final EntryLink link, final String reason) {
    update(
        link,
        current -> {
          final int attempts = current.getAttempts() + 1;
          current.setAttempts(attempts);
          current.setFailureReason(truncate(reason, MAX_REASON_LENGTH));
          if (attempts >= maxAttempts) {
            current.setPreviewStatus(LinkPreviewStatus.FAILED);
            log.info("Link preview {} given up after {} attempts: {}", link.getId(), attempts, reason);
          } else {
            final long minutes = BACKOFF_MINUTES[Math.min(attempts, BACKOFF_MINUTES.length) - 1];
            current.setNextAttemptAt(new Date(System.currentTimeMillis() + minutes * 60_000L));
            log.info("Link preview {} will be tried again in {} minutes: {}", link.getId(), minutes, reason);
          }
        });
  }

  /**
   * Change the link's row, but only if it still holds the same address and still waits for its
   * preview.
   *
   * @return whether the row was changed
   */
  private boolean update(final EntryLink link, final Consumer<EntryLink> change) {
    final Boolean written =
        transactionTemplate.execute(
            status ->
                entryLinkRepository
                    .findById(link.getId())
                    .filter(current -> current.getPreviewStatus() == LinkPreviewStatus.PENDING)
                    .filter(current -> link.getUrl().equals(current.getUrl()))
                    .map(
                        current -> {
                          change.accept(current);
                          entryLinkRepository.save(current);
                          return true;
                        })
                    .orElse(false));
    return Boolean.TRUE.equals(written);
  }

  private static String truncate(final String text, final int max) {
    if (text == null || text.length() <= max) return text;
    return text.substring(0, max);
  }
}
