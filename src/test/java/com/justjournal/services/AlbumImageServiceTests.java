package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.User;
import com.justjournal.model.UserImage;
import com.justjournal.repository.UserImageRepository;
import com.justjournal.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AlbumImageServiceTests {

  private ImageStorageService storage;
  private MinioClient minioClient;
  private UserImageRepository userImageRepository;
  private UserRepository userRepository;
  private JdbcTemplate jdbcTemplate;
  private ImageModerationService moderation;
  private AlbumImageService service;

  @BeforeEach
  void setUp() {
    storage = mock(ImageStorageService.class);
    minioClient = mock(MinioClient.class);
    userImageRepository = mock(UserImageRepository.class);
    userRepository = mock(UserRepository.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    moderation = mock(ImageModerationService.class);
    verdict(ModerationStatus.APPROVED);
    service =
        new AlbumImageService(
            storage,
            minioClient,
            new ImageService(),
            moderation,
            userImageRepository,
            userRepository,
            jdbcTemplate);
    service.setImageBucket("pics");
  }

  private void verdict(final ModerationStatus status) {
    when(moderation.moderate(any()))
        .thenReturn(new ImageModerationService.Result(status, status.name().toLowerCase(), "test"));
  }

  private static byte[] jpeg(final int width, final int height) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out);
    return out.toByteArray();
  }

  private static User owner(final int id) {
    final User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    return user;
  }

  private static UserImage image(final int id, final int ownerId, final String filename, final String thumbnail) {
    final UserImage image = new UserImage();
    image.setId(id);
    image.setUser(owner(ownerId));
    image.setMimeType("image/jpeg");
    image.setFilename(filename);
    image.setThumbnail(thumbnail);
    return image;
  }

  @Test
  void upload_storesPictureAndThumbnail() throws Exception {
    final byte[] photo = jpeg(1600, 1200);
    when(userImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    final UserImage saved = service.upload(5, "Beach", photo);

    assertEquals("image/jpeg", saved.getMimeType());
    assertEquals(photo.length, saved.getSize());
    assertTrue(saved.getFilename().matches("5/[0-9a-f-]{36}\\.jpg"), saved.getFilename());
    assertEquals(saved.getFilename().replace(".jpg", "_thumb.jpg"), saved.getThumbnail());

    final ArgumentCaptor<InputStream> stored = ArgumentCaptor.forClass(InputStream.class);
    verify(storage).uploadFile(eq("pics"), eq(saved.getFilename()), eq("image/jpeg"), stored.capture());
    assertArrayEquals(photo, stored.getValue().readAllBytes());

    final ArgumentCaptor<InputStream> thumb = ArgumentCaptor.forClass(InputStream.class);
    verify(storage).uploadFile(eq("pics"), eq(saved.getThumbnail()), eq("image/jpeg"), thumb.capture());
    final BufferedImage thumbnail = ImageIO.read(thumb.getValue());
    assertEquals(AlbumImageService.THUMBNAIL_SIZE, thumbnail.getWidth());
    assertEquals(300, thumbnail.getHeight());
  }

  @Test
  void upload_thumbnailFails_pictureRemovedAndNothingSaved() throws Exception {
    doThrow(new IOException("down"))
        .when(storage)
        .uploadFile(eq("pics"), argThat(name -> name.endsWith("_thumb.jpg")), anyString(), any());

    assertThrows(ServiceException.class, () -> service.upload(5, "Beach", jpeg(100, 100)));

    verify(storage).deleteFile(eq("pics"), argThat(name -> name.endsWith(".jpg") && !name.endsWith("_thumb.jpg")));
    verify(userImageRepository, never()).saveAndFlush(any());
  }

  @Test
  void upload_saveFails_filesRemoved() throws Exception {
    when(userImageRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("db"));

    assertThrows(ServiceException.class, () -> service.upload(5, "Beach", jpeg(100, 100)));

    verify(storage).deleteFile(eq("pics"), argThat(name -> name.endsWith("_thumb.jpg")));
    verify(storage).deleteFile(eq("pics"), argThat(name -> !name.endsWith("_thumb.jpg")));
  }

  @Test
  void upload_filterSeesTheThumbnail() throws Exception {
    when(userImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    final UserImage saved = service.upload(5, "Beach", jpeg(1600, 1200));

    assertEquals(ModerationStatus.APPROVED, saved.getModerationStatus());
    final ArgumentCaptor<byte[]> judged = ArgumentCaptor.forClass(byte[].class);
    verify(moderation).moderate(judged.capture());
    assertEquals(AlbumImageService.THUMBNAIL_SIZE, ImageIO.read(new ByteArrayInputStream(judged.getValue())).getWidth());
  }

  @Test
  void upload_nudity_refusedAndNothingStored() throws Exception {
    verdict(ModerationStatus.REJECTED);

    final BadRequestException e =
        assertThrows(BadRequestException.class, () -> service.upload(5, "Beach", jpeg(100, 100)));

    assertEquals(AlbumImageService.REJECTED_MESSAGE, e.getMessage());
    verify(storage, never()).uploadFile(any(), any(), any(), any());
    verify(userImageRepository, never()).saveAndFlush(any());
  }

  @Test
  void upload_unsure_savedForReview() throws Exception {
    verdict(ModerationStatus.REVIEW);
    when(userImageRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    final UserImage saved = service.upload(5, "Beach", jpeg(100, 100));

    assertEquals(ModerationStatus.REVIEW, saved.getModerationStatus());
    verify(storage).uploadFile(eq("pics"), eq(saved.getFilename()), anyString(), any());
  }

  @Test
  void picture_awaitingReview_hiddenFromOthers() {
    final UserImage stored = image(7, 5, "5/a.jpg", "5/a_thumb.jpg");
    stored.setModerationStatus(ModerationStatus.REVIEW);
    when(userImageRepository.findById(7)).thenReturn(Optional.of(stored));

    assertThrows(NotFoundException.class, () -> service.picture(7, 0));
    assertThrows(NotFoundException.class, () -> service.picture(7, 6));
    assertThrows(NotFoundException.class, () -> service.thumbnail(7, 6));
  }

  @Test
  void picture_awaitingReview_ownerSeesIt() throws Exception {
    final UserImage stored = image(7, 5, "5/a.jpg", "5/a_thumb.jpg");
    stored.setModerationStatus(ModerationStatus.REVIEW);
    when(userImageRepository.findById(7)).thenReturn(Optional.of(stored));
    final InputStream stream = new ByteArrayInputStream(new byte[] {1});
    when(storage.downloadFile("pics", "5/a.jpg")).thenReturn(stream);

    assertEquals(stream, service.picture(7, 5).stream());
  }

  @Test
  void upload_notAPicture_nothingStored() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> service.upload(5, "x", new byte[1000]));

    verify(storage, never()).uploadFile(any(), any(), any(), any());
  }

  @Test
  void picture_inStorage() throws Exception {
    final UserImage stored = image(7, 5, "5/a.jpg", "5/a_thumb.jpg");
    when(userImageRepository.findById(7)).thenReturn(Optional.of(stored));
    final InputStream stream = new ByteArrayInputStream(new byte[] {1});
    when(storage.downloadFile("pics", "5/a.jpg")).thenReturn(stream);

    final AlbumImageService.Picture picture = service.picture(7, 0);

    assertEquals("image/jpeg", picture.mimeType());
    assertEquals(stream, picture.stream());
  }

  @SuppressWarnings("unchecked")
  @Test
  void picture_notCopiedYet_readFromDatabase() throws Exception {
    final UserImage stored = image(7, 5, null, null);
    when(userImageRepository.findById(7)).thenReturn(Optional.of(stored));
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
        .thenReturn(java.util.List.of(new byte[] {9, 8}));

    assertArrayEquals(new byte[] {9, 8}, service.picture(7, 0).stream().readAllBytes());
    verify(storage, never()).downloadFile(any(), any());
  }

  @Test
  void picture_missing_notFound() {
    when(userImageRepository.findById(7)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.picture(7, 0));
  }

  @SuppressWarnings("unchecked")
  @Test
  void thumbnail_notCopiedYet_madeFromDatabaseCopy() throws Exception {
    final UserImage stored = image(7, 5, null, null);
    when(userImageRepository.findById(7)).thenReturn(Optional.of(stored));
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7)))
        .thenReturn(java.util.List.of(jpeg(800, 800)));

    final BufferedImage thumbnail = ImageIO.read(service.thumbnail(7, 0).stream());

    assertEquals(AlbumImageService.THUMBNAIL_SIZE, thumbnail.getWidth());
  }

  @Test
  void delete_owner_rowMapAndFilesRemoved() throws Exception {
    final UserImage image = image(7, 5, "5/a.jpg", "5/a_thumb.jpg");
    when(userImageRepository.findById(7)).thenReturn(Optional.of(image));

    service.delete(7, 5);

    verify(jdbcTemplate).update("DELETE FROM user_images_album_map WHERE image_id = ?", 7);
    verify(userImageRepository).delete(image);
    verify(storage).deleteFile("pics", "5/a.jpg");
    verify(storage).deleteFile("pics", "5/a_thumb.jpg");
  }

  @Test
  void delete_someoneElses_forbidden() throws Exception {
    final UserImage stored = image(7, 5, "5/a.jpg", null);
    when(userImageRepository.findById(7)).thenReturn(Optional.of(stored));

    assertThrows(ForbiddenException.class, () -> service.delete(7, 6));

    verify(userImageRepository, never()).delete(any());
    verify(storage, never()).deleteFile(any(), any());
  }

  @Test
  void delete_missing_notFound() {
    when(userImageRepository.findById(7)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service.delete(7, 5));
  }

  private void blobRow(final int id, final byte[] data) {
    when(jdbcTemplate.queryForList(anyString(), eq(Integer.class))).thenReturn(java.util.List.of(id));
    when(jdbcTemplate.queryForMap(anyString(), eq(id)))
        .thenReturn(Map.of("owner", 5, "mimetype", "image/pjpeg", "image", data));
  }

  private void storedSize(final long size) throws Exception {
    final StatObjectResponse stat = mock(StatObjectResponse.class);
    when(stat.size()).thenReturn(size);
    when(minioClient.statObject(any())).thenReturn(stat);
  }

  @Test
  void migrateBlobs_copiedCheckedAndMarked() throws Exception {
    final byte[] photo = jpeg(1000, 500);
    blobRow(7, photo);
    storedSize(photo.length);

    final AlbumImageService.MigrationResult result = service.migrateBlobs();

    assertEquals(1, result.copied());
    assertEquals(0, result.failed());
    verify(storage).uploadFile(eq("pics"), eq("5/picture_7.jpg"), eq("image/jpeg"), any());
    verify(storage).uploadFile(eq("pics"), eq("5/picture_7_thumb.jpg"), eq("image/jpeg"), any());
    verify(jdbcTemplate)
        .update(
            argThat((String sql) -> sql.contains("migrated = NOW()") && sql.contains("modified = modified")),
            eq("5/picture_7.jpg"),
            eq("5/picture_7_thumb.jpg"),
            eq(photo.length),
            eq("image/jpeg"),
            eq(7));
  }

  @Test
  void migrateBlobs_sizeMismatch_notMarked() throws Exception {
    final byte[] photo = jpeg(100, 100);
    blobRow(7, photo);
    storedSize(photo.length - 1);

    final AlbumImageService.MigrationResult result = service.migrateBlobs();

    assertEquals(0, result.copied());
    assertEquals(1, result.failed());
    verify(jdbcTemplate, never()).update(argThat((String sql) -> sql.startsWith("UPDATE")), any(Object[].class));
  }

  @Test
  void migrateBlobs_unreadableImage_copiedAsItIsWithoutThumbnail() throws Exception {
    final byte[] odd = new byte[600];
    blobRow(7, odd);
    storedSize(odd.length);

    final AlbumImageService.MigrationResult result = service.migrateBlobs();

    assertEquals(1, result.copied());
    verify(storage).uploadFile(eq("pics"), eq("5/picture_7.bin"), eq("image/pjpeg"), any());
    verify(storage, never()).uploadFile(any(), argThat(name -> name.endsWith("_thumb.jpg")), any(), any());
    verify(jdbcTemplate).update(anyString(), eq("5/picture_7.bin"), eq(null), eq(600), eq("image/pjpeg"), eq(7));
    assertNotNull(result);
  }
}
