package com.justjournal.services;

import static com.justjournal.services.EntryAccessServiceTests.FRIEND;
import static com.justjournal.services.EntryAccessServiceTests.OWNER;
import static com.justjournal.services.EntryAccessServiceTests.STRANGER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryImage;
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.repository.EntryImageRepository;
import com.justjournal.repository.EntryRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EntryImageServiceTests {

  private ImageStorageService storage;
  private EntryImageRepository entryImageRepository;
  private EntryRepository entryRepository;
  private ImageModerationService moderation;
  private EntryImageService service;

  @BeforeEach
  void setUp() {
    storage = mock(ImageStorageService.class);
    entryImageRepository = mock(EntryImageRepository.class);
    entryRepository = mock(EntryRepository.class);
    moderation = mock(ImageModerationService.class);
    verdict(ModerationStatus.APPROVED);
    final StoredImageFiles files = new StoredImageFiles(storage);
    files.setBucket("pics");
    service =
        new EntryImageService(
            files,
            new ImageService(),
            moderation,
            entryImageRepository,
            entryRepository,
            new EntryAccessService());
  }

  private void verdict(final ModerationStatus status) {
    when(moderation.moderate(any(), any()))
        .thenReturn(new ImageModerationService.Result(status, status.name().toLowerCase(), "test"));
  }

  private static byte[] jpeg(final int width, final int height) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out);
    return out.toByteArray();
  }

  /** Picture 7 by user 5, attached to the post given, or to none. */
  private EntryImage stored(final Entry entry, final ModerationStatus status) {
    final EntryImage image = new EntryImage();
    image.setId(7);
    image.setOwnerId(OWNER);
    image.setMimeType("image/png");
    image.setFilename("entry/5/a.png");
    image.setDisplay("entry/5/a_display.jpg");
    image.setModerationStatus(status);
    if (entry != null) {
      image.setEntryId(entry.getId());
      when(entryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
    }
    when(entryImageRepository.findById(7)).thenReturn(Optional.of(image));
    return image;
  }

  private static Entry post(final Security security) {
    return EntryAccessServiceTests.entry(security);
  }

  @Test
  void upload_storesPictureAndDisplayImage() throws Exception {
    final byte[] photo = jpeg(2048, 1536);
    when(entryImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    final EntryImage saved = service.upload(5, photo, "  A beach  ");

    assertEquals("image/jpeg", saved.getMimeType());
    assertEquals(photo.length, saved.getSize());
    assertEquals(5, saved.getOwnerId());
    assertNull(saved.getEntryId());
    assertEquals("A beach", saved.getAltText());
    assertEquals(ModerationStatus.APPROVED, saved.getModerationStatus());
    assertTrue(saved.getFilename().matches("entry/5/[0-9a-f-]{36}\\.jpg"), saved.getFilename());
    assertEquals(saved.getFilename().replace(".jpg", "_display.jpg"), saved.getDisplay());
    // the size of what is shown on the post
    assertEquals(1024, saved.getWidth());
    assertEquals(768, saved.getHeight());

    final ArgumentCaptor<InputStream> original = ArgumentCaptor.forClass(InputStream.class);
    verify(storage).uploadFile(eq("pics"), eq(saved.getFilename()), eq("image/jpeg"), original.capture());
    assertArrayEquals(photo, original.getValue().readAllBytes());

    final ArgumentCaptor<InputStream> display = ArgumentCaptor.forClass(InputStream.class);
    verify(storage).uploadFile(eq("pics"), eq(saved.getDisplay()), eq("image/jpeg"), display.capture());
    assertEquals(1024, ImageIO.read(display.getValue()).getWidth());
  }

  @Test
  void upload_filterSeesDisplayImageAndOriginal() throws Exception {
    when(entryImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    service.upload(5, jpeg(2048, 1024), null);

    final ArgumentCaptor<byte[]> judged = ArgumentCaptor.forClass(byte[].class);
    final ArgumentCaptor<byte[]> kept = ArgumentCaptor.forClass(byte[].class);
    verify(moderation).moderate(judged.capture(), kept.capture());
    assertEquals(1024, ImageIO.read(new ByteArrayInputStream(judged.getValue())).getWidth());
    assertEquals(2048, ImageIO.read(new ByteArrayInputStream(kept.getValue())).getWidth());
  }

  @Test
  void upload_blankAlt_none() throws Exception {
    when(entryImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    assertNull(service.upload(5, jpeg(100, 100), "  ").getAltText());
  }

  @Test
  void upload_altTooLong_refused() {
    assertThrows(
        BadRequestException.class, () -> service.upload(5, jpeg(100, 100), "x".repeat(256)));

    verify(entryImageRepository, never()).saveAndFlush(any());
  }

  @Test
  void upload_unsure_savedForReview() throws Exception {
    verdict(ModerationStatus.REVIEW);
    when(entryImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    final EntryImage saved = service.upload(5, jpeg(100, 100), null);

    assertEquals(ModerationStatus.REVIEW, saved.getModerationStatus());
    assertEquals("review", saved.getModerationCategory());
    verify(storage).uploadFile(eq("pics"), eq(saved.getFilename()), anyString(), any());
  }

  @Test
  void upload_rejected_refusedAndNothingStored() throws Exception {
    verdict(ModerationStatus.REJECTED);

    final BadRequestException e =
        assertThrows(BadRequestException.class, () -> service.upload(5, jpeg(100, 100), null));

    assertEquals(AlbumImageService.REJECTED_MESSAGE, e.getMessage());
    verify(storage, never()).uploadFile(any(), any(), any(), any());
    verify(entryImageRepository, never()).saveAndFlush(any());
  }

  @Test
  void upload_notAPicture_nothingStored() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> service.upload(5, new byte[1000], null));

    verify(storage, never()).uploadFile(any(), any(), any(), any());
  }

  @Test
  void upload_displayImageFails_originalRemovedAndNothingSaved() throws Exception {
    doThrow(new IOException("down"))
        .when(storage)
        .uploadFile(eq("pics"), argThat(name -> name.endsWith("_display.jpg")), anyString(), any());

    assertThrows(ServiceException.class, () -> service.upload(5, jpeg(100, 100), null));

    verify(storage)
        .deleteFile(eq("pics"), argThat(name -> name.endsWith(".jpg") && !name.endsWith("_display.jpg")));
    verify(entryImageRepository, never()).saveAndFlush(any());
  }

  @Test
  void upload_saveFails_bothFilesRemoved() throws Exception {
    when(entryImageRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("db"));

    assertThrows(ServiceException.class, () -> service.upload(5, jpeg(100, 100), null));

    verify(storage).deleteFile(eq("pics"), argThat(name -> name.endsWith("_display.jpg")));
    verify(storage).deleteFile(eq("pics"), argThat(name -> !name.endsWith("_display.jpg")));
  }

  @Test
  void upload_tooManyWaiting_refusedBeforeAnythingElse() throws Exception {
    when(entryImageRepository.countByOwnerIdAndEntryIdIsNull(5)).thenReturn(10L);

    final BadRequestException e =
        assertThrows(BadRequestException.class, () -> service.upload(5, jpeg(100, 100), null));

    assertEquals(EntryImageService.TOO_MANY_PENDING_MESSAGE, e.getMessage());
    verify(moderation, never()).moderate(any(), any());
    verify(storage, never()).uploadFile(any(), any(), any(), any());
  }

  @Test
  void upload_underTheLimit_accepted() throws Exception {
    when(entryImageRepository.countByOwnerIdAndEntryIdIsNull(5)).thenReturn(9L);
    when(entryImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    service.upload(5, jpeg(100, 100), null);

    verify(entryImageRepository).saveAndFlush(any());
  }

  @Test
  void visible_owner_always() {
    final EntryImage unattached = stored(null, ModerationStatus.REVIEW);

    assertTrue(service.visible(unattached, null, OWNER));
    assertTrue(service.visible(unattached, post(Security.PRIVATE), OWNER));
  }

  @Test
  void visible_approvedOnPublicPost_everyone() {
    final Entry post = post(Security.PUBLIC);
    final EntryImage image = stored(post, ModerationStatus.APPROVED);

    assertTrue(service.visible(image, post, 0));
    assertTrue(service.visible(image, post, FRIEND));
    assertTrue(service.visible(image, post, STRANGER));
  }

  @Test
  void visible_friendsOnlyPost_friendOnly() {
    final Entry post = post(Security.FRIENDS);
    final EntryImage image = stored(post, ModerationStatus.APPROVED);

    assertFalse(service.visible(image, post, 0));
    assertTrue(service.visible(image, post, FRIEND));
    assertFalse(service.visible(image, post, STRANGER));
  }

  @Test
  void visible_privatePost_onlyOwner() {
    final Entry post = post(Security.PRIVATE);
    final EntryImage image = stored(post, ModerationStatus.APPROVED);

    assertFalse(service.visible(image, post, 0));
    assertFalse(service.visible(image, post, FRIEND));
    assertTrue(service.visible(image, post, OWNER));
  }

  @Test
  void visible_draftOrOwnerViewOnly_onlyOwner() {
    final Entry draft = EntryAccessServiceTests.entry(Security.PUBLIC, PrefBool.Y, false);
    final Entry hidden = EntryAccessServiceTests.entry(Security.PUBLIC, PrefBool.N, true);

    assertFalse(service.visible(stored(draft, ModerationStatus.APPROVED), draft, 0));
    assertFalse(service.visible(stored(hidden, ModerationStatus.APPROVED), hidden, FRIEND));
  }

  @Test
  void visible_awaitingReview_onlyOwner() {
    final Entry post = post(Security.PUBLIC);
    final EntryImage image = stored(post, ModerationStatus.REVIEW);

    assertFalse(service.visible(image, post, 0));
    assertFalse(service.visible(image, post, FRIEND));
  }

  @Test
  void visible_unattached_onlyOwner() {
    final EntryImage image = stored(null, ModerationStatus.APPROVED);

    assertFalse(service.visible(image, null, 0));
    // a post given that isn't the one the picture is on doesn't count
    assertFalse(service.visible(image, post(Security.PUBLIC), STRANGER));
  }

  @Test
  void visible_someoneElsesPost_hidden() {
    final Entry post = post(Security.PUBLIC);
    final EntryImage image = stored(post, ModerationStatus.APPROVED);
    image.setOwnerId(9);

    assertFalse(service.visible(image, post, 0));
  }

  @Test
  void publicCacheable_onlyApprovedOnPublicPost() {
    final Entry open = post(Security.PUBLIC);
    assertTrue(service.publicCacheable(stored(open, ModerationStatus.APPROVED), open));
    assertFalse(service.publicCacheable(stored(open, ModerationStatus.REVIEW), open));

    final Entry friends = post(Security.FRIENDS);
    assertFalse(service.publicCacheable(stored(friends, ModerationStatus.APPROVED), friends));

    final Entry draft = EntryAccessServiceTests.entry(Security.PUBLIC, PrefBool.Y, false);
    assertFalse(service.publicCacheable(stored(draft, ModerationStatus.APPROVED), draft));

    assertFalse(service.publicCacheable(stored(null, ModerationStatus.APPROVED), null));
  }

  @Test
  void display_publicPost_cacheable() throws Exception {
    stored(post(Security.PUBLIC), ModerationStatus.APPROVED);
    final InputStream stream = new ByteArrayInputStream(new byte[] {1});
    when(storage.downloadFile("pics", "entry/5/a_display.jpg")).thenReturn(stream);

    final EntryImageService.Picture picture = service.display(7, 0);

    assertEquals(stream, picture.stream());
    assertEquals("image/jpeg", picture.mimeType());
    assertTrue(picture.publicCacheable());
  }

  @Test
  void original_ownerOnPrivatePost_notCacheable() throws Exception {
    stored(post(Security.PRIVATE), ModerationStatus.APPROVED);
    when(storage.downloadFile("pics", "entry/5/a.png"))
        .thenReturn(new ByteArrayInputStream(new byte[] {1}));

    final EntryImageService.Picture picture = service.original(7, OWNER);

    assertEquals("image/png", picture.mimeType());
    assertFalse(picture.publicCacheable());
  }

  @Test
  void original_ownerAwaitingReview_notCacheable() throws Exception {
    stored(post(Security.PUBLIC), ModerationStatus.REVIEW);
    when(storage.downloadFile("pics", "entry/5/a.png"))
        .thenReturn(new ByteArrayInputStream(new byte[] {1}));

    assertFalse(service.original(7, OWNER).publicCacheable());
  }

  @Test
  void original_hidden_notFound() throws Exception {
    stored(post(Security.PRIVATE), ModerationStatus.APPROVED);

    assertThrows(NotFoundException.class, () -> service.original(7, STRANGER));
    assertThrows(NotFoundException.class, () -> service.display(7, 0));
    verify(storage, never()).downloadFile(any(), any());
  }

  @Test
  void original_postGone_onlyOwner() throws Exception {
    final EntryImage image = stored(null, ModerationStatus.APPROVED);
    image.setEntryId(30);
    when(entryRepository.findById(30)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.original(7, 0));
  }

  @Test
  void original_missing_notFound() {
    when(entryImageRepository.findById(7)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.original(7, OWNER));
  }

  @Test
  void delete_owner_rowAndFilesRemoved() throws Exception {
    final EntryImage image = stored(null, ModerationStatus.APPROVED);

    service.delete(7, OWNER);

    verify(entryImageRepository).delete(image);
    verify(storage).deleteFile("pics", "entry/5/a.png");
    verify(storage).deleteFile("pics", "entry/5/a_display.jpg");
  }

  @Test
  void delete_someoneElses_forbidden() throws Exception {
    stored(null, ModerationStatus.APPROVED);

    assertThrows(ForbiddenException.class, () -> service.delete(7, STRANGER));

    verify(entryImageRepository, never()).delete(any());
    verify(storage, never()).deleteFile(any(), any());
  }

  @Test
  void delete_missing_notFound() {
    when(entryImageRepository.findById(anyInt())).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.delete(7, OWNER));
  }

  @Test
  void readOriginalForModeration_ignoresVisibility() throws Exception {
    final EntryImage image = stored(post(Security.PRIVATE), ModerationStatus.REVIEW);
    when(storage.downloadFile("pics", "entry/5/a.png"))
        .thenReturn(new ByteArrayInputStream(new byte[] {4, 2}));

    assertArrayEquals(new byte[] {4, 2}, service.readOriginalForModeration(image));
  }
}
