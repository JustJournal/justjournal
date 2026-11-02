package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.model.EntryLink;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.model.ModerationStatus;
import com.justjournal.repository.EntryLinkRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.services.activitypub.ActivityPublisher;
import com.justjournal.services.link.FetchException;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.LinkSafetyService;
import com.justjournal.services.link.NormalizedUrl;
import com.justjournal.services.link.OpenGraphParser;
import com.justjournal.services.link.SafeHttpFetcher;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

class LinkPreviewServiceTests {

  private static final String URL = "https://example.com/story";
  private static final URI IMAGE = URI.create("https://cdn.example.com/card.png");
  private static final NormalizedUrl PAGE =
      new NormalizedUrl(URI.create(URL), URI.create(URL), "example.com");
  private static final NormalizedUrl IMAGE_URL = new NormalizedUrl(IMAGE, IMAGE, "cdn.example.com");
  private static final byte[] ORIGINAL = {1, 2, 3};
  private static final byte[] THUMBNAIL = {4, 5};

  private EntryLinkRepository repository;
  private LinkSafetyService safety;
  private SafeHttpFetcher fetcher;
  private ImageService imageService;
  private ImageModerationService moderation;
  private StoredImageFiles files;
  private RecentBlogsRepository recentBlogs;
  private ActivityPublisher activityPublisher;
  private LinkPreviewService service;
  private EntryLink row;

  @BeforeEach
  void setUp() throws Exception {
    repository = mock(EntryLinkRepository.class);
    safety = mock(LinkSafetyService.class);
    fetcher = mock(SafeHttpFetcher.class);
    imageService = mock(ImageService.class);
    moderation = mock(ImageModerationService.class);
    files = mock(StoredImageFiles.class);
    recentBlogs = mock(RecentBlogsRepository.class);
    activityPublisher = mock(ActivityPublisher.class);
    when(recentBlogs.delete()).thenReturn(Mono.just(true));
    final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    service =
        new LinkPreviewService(
            repository,
            safety,
            fetcher,
            new OpenGraphParser(),
            imageService,
            moderation,
            files,
            new TransactionTemplate(transactionManager),
            recentBlogs,
            activityPublisher);

    row = new EntryLink();
    row.setId(7);
    row.setOwnerId(5);
    row.setEntryId(30);
    row.setUrl(URL);
    when(repository.findById(7)).thenAnswer(i -> Optional.of(row));
    when(safety.checkForSave(URL)).thenReturn(PAGE);
    when(safety.checkHop(IMAGE)).thenReturn(IMAGE_URL);
  }

  private void pageSays(final String head) throws Exception {
    final byte[] html =
        ("<html><head>" + head + "</head><body></body></html>").getBytes(StandardCharsets.UTF_8);
    when(fetcher.fetch(eq(PAGE), eq(LinkPreviewService.PAGE_TYPES), anyInt()))
        .thenReturn(
            new SafeHttpFetcher.Result(URI.create(URL), "text/html", StandardCharsets.UTF_8, html));
  }

  private void imageIs(final ModerationStatus status, final String category) throws Exception {
    when(fetcher.fetch(eq(IMAGE_URL), eq(LinkPreviewService.IMAGE_TYPES), anyInt()))
        .thenReturn(new SafeHttpFetcher.Result(IMAGE, "image/png", null, ORIGINAL));
    when(imageService.describe(ORIGINAL))
        .thenReturn(new ImageService.ImageInfo("image/png", "png", 1200, 630));
    when(imageService.thumbnail(ORIGINAL, 600)).thenReturn(THUMBNAIL);
    when(imageService.describe(THUMBNAIL))
        .thenReturn(new ImageService.ImageInfo("image/jpeg", "jpg", 600, 315));
    when(moderation.moderate(THUMBNAIL, ORIGINAL))
        .thenReturn(new ImageModerationService.Result(status, category, null));
  }

  private static final String CARD =
      "<meta property=\"og:title\" content=\"A story\">"
          + "<meta property=\"og:description\" content=\"What happened\">"
          + "<meta property=\"og:site_name\" content=\"Example\">"
          + "<meta property=\"og:image\" content=\"https://cdn.example.com/card.png\">";

  @Test
  void readyCardWithAJudgedPicture() throws Exception {
    pageSays(CARD);
    imageIs(ModerationStatus.APPROVED, null);

    service.refresh(7);

    assertEquals(LinkPreviewStatus.READY, row.getPreviewStatus());
    assertEquals("A story", row.getTitle());
    assertEquals("What happened", row.getDescription());
    assertEquals("Example", row.getSiteName());
    assertEquals(URL, row.getFinalUrl());
    assertTrue(row.getImageFilename().matches("link/5/[0-9a-f-]{36}\\.jpg"), row.getImageFilename());
    assertEquals(600, row.getImageWidth());
    assertEquals(315, row.getImageHeight());
    assertEquals(ModerationStatus.APPROVED, row.getImageModerationStatus());
    verify(files).put(row.getImageFilename(), "image/jpeg", THUMBNAIL);
    verify(repository).save(row);
    verify(recentBlogs).delete();
    // the link now carries the page's title
    verify(activityPublisher).entryAttachmentsChanged(30);
  }

  @Test
  void pictureTheFilterCouldNotJudgeIsKeptForReview() throws Exception {
    pageSays(CARD);
    imageIs(ModerationStatus.REVIEW, ImageModerationService.CATEGORY_ERROR);

    service.refresh(7);

    assertEquals(LinkPreviewStatus.READY, row.getPreviewStatus());
    assertEquals(ModerationStatus.REVIEW, row.getImageModerationStatus());
    assertEquals(ImageModerationService.CATEGORY_ERROR, row.getImageModerationCategory());
  }

  @Test
  void rejectedPictureLeavesACardWithoutOne() throws Exception {
    pageSays(CARD);
    imageIs(ModerationStatus.REJECTED, "nudity");

    service.refresh(7);

    assertEquals(LinkPreviewStatus.READY, row.getPreviewStatus());
    assertEquals("A story", row.getTitle());
    assertNull(row.getImageFilename());
    verify(files, never()).put(anyString(), anyString(), any());
  }

  @Test
  void pictureOnAnUnsafeHostIsNotFetched() throws Exception {
    pageSays(CARD);
    when(safety.checkHop(IMAGE))
        .thenThrow(LinkRejectedException.of(LinkRejectedException.Reason.PRIVATE_ADDRESS, null));

    service.refresh(7);

    assertEquals(LinkPreviewStatus.READY, row.getPreviewStatus());
    assertNull(row.getImageFilename());
    verify(fetcher, never()).fetch(eq(IMAGE_URL), any(), anyInt());
  }

  @Test
  void hugePictureIsSkipped() throws Exception {
    pageSays(CARD);
    imageIs(ModerationStatus.APPROVED, null);
    when(imageService.describe(ORIGINAL))
        .thenReturn(new ImageService.ImageInfo("image/png", "png", 10_000, 10_000));

    service.refresh(7);

    assertNull(row.getImageFilename());
    verify(moderation, never()).moderate(any(), any());
  }

  @Test
  void pageWithoutCardStillGetsItsTitle() throws Exception {
    pageSays("<title>Plain page</title>");

    service.refresh(7);

    assertEquals(LinkPreviewStatus.READY, row.getPreviewStatus());
    assertEquals("Plain page", row.getTitle());
    assertNull(row.getImageFilename());
  }

  @Test
  void linkNowOnAReputationListIsBlocked() throws Exception {
    when(safety.checkForSave(URL))
        .thenThrow(LinkRejectedException.of(LinkRejectedException.Reason.DBL, "example.com"));

    service.refresh(7);

    assertEquals(LinkPreviewStatus.BLOCKED, row.getPreviewStatus());
    assertEquals("DBL", row.getFailureReason());
    verify(fetcher, never()).fetch(any(), any(), anyInt());
    verify(recentBlogs).delete();
    // the link is no longer shown
    verify(activityPublisher).entryAttachmentsChanged(30);
  }

  @Test
  void redirectToAPrivateAddressGetsNoCard() throws Exception {
    when(fetcher.fetch(eq(PAGE), any(), anyInt()))
        .thenThrow(LinkRejectedException.of(LinkRejectedException.Reason.PRIVATE_ADDRESS, null));

    service.refresh(7);

    assertEquals(LinkPreviewStatus.FAILED, row.getPreviewStatus());
    assertEquals("PRIVATE_ADDRESS", row.getFailureReason());
    // still shown under its address, as before
    verify(activityPublisher, never()).entryAttachmentsChanged(anyInt());
  }

  @Test
  void missingPageFailsForGood() throws Exception {
    when(fetcher.fetch(eq(PAGE), any(), anyInt()))
        .thenThrow(new FetchException(404, true, "Not found"));

    service.refresh(7);

    assertEquals(LinkPreviewStatus.FAILED, row.getPreviewStatus());
    assertEquals(0, row.getAttempts());
  }

  @Test
  void serverErrorIsTriedAgainLater() throws Exception {
    when(fetcher.fetch(eq(PAGE), any(), anyInt()))
        .thenThrow(new FetchException(503, false, "Unavailable"));
    final long before = System.currentTimeMillis();

    service.refresh(7);

    assertEquals(LinkPreviewStatus.PENDING, row.getPreviewStatus());
    assertEquals(1, row.getAttempts());
    final long wait = row.getNextAttemptAt().getTime() - before;
    assertTrue(wait >= 5 * 60_000L && wait < 6 * 60_000L, "waited " + wait);
  }

  @Test
  void backoffGrowsThenGivesUp() throws Exception {
    when(fetcher.fetch(eq(PAGE), any(), anyInt())).thenThrow(new IOException("timed out"));
    final long before = System.currentTimeMillis();
    row.setAttempts(1);

    service.refresh(7);

    assertEquals(2, row.getAttempts());
    final long wait = row.getNextAttemptAt().getTime() - before;
    assertTrue(wait >= 30 * 60_000L && wait < 31 * 60_000L, "waited " + wait);

    service.refresh(7);

    assertEquals(3, row.getAttempts());
    assertEquals(LinkPreviewStatus.FAILED, row.getPreviewStatus());
  }

  @Test
  void linkChangedWhileFetchingDiscardsThePicture() throws Exception {
    pageSays(CARD);
    imageIs(ModerationStatus.APPROVED, null);
    final EntryLink first = new EntryLink();
    first.setId(7);
    first.setOwnerId(5);
    first.setUrl(URL);
    final EntryLink changed = new EntryLink();
    changed.setId(7);
    changed.setUrl("https://example.org/other");
    when(repository.findById(7)).thenReturn(Optional.of(first), Optional.of(changed));

    service.refresh(7);

    verify(repository, never()).save(any());
    verify(files).remove(org.mockito.ArgumentMatchers.startsWith("link/5/"));
    verify(recentBlogs, never()).delete();
    verify(activityPublisher, never()).entryAttachmentsChanged(anyInt());
  }

  @Test
  void linkAlreadyDoneIsLeftAlone() throws Exception {
    row.setPreviewStatus(LinkPreviewStatus.READY);
    row.setFetchedAt(new Date());

    service.refresh(7);

    verify(safety, never()).checkForSave(anyString());
    verify(repository, never()).save(any());
  }

  @Test
  void goneLinkIsLeftAlone() throws Exception {
    when(repository.findById(7)).thenReturn(Optional.empty());

    service.refresh(7);

    verify(safety, never()).checkForSave(anyString());
  }
}
