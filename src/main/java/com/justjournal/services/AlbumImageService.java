package com.justjournal.services;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.UserImage;
import com.justjournal.repository.UserImageRepository;
import com.justjournal.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pictures in users' photo albums. Pictures and their thumbnails are stored in MinIO. Pictures
 * uploaded before that are still in the user_images.image column until {@link #migrateBlobs()}
 * copies them; until then they are read from there.
 */
@Slf4j
@Service
public class AlbumImageService {

  /** Thumbnails fit in a square this many pixels wide. */
  public static final int THUMBNAIL_SIZE = 400;

  /** MinIO's error code for an object that doesn't exist. */
  private static final String NO_SUCH_KEY = "NoSuchKey";

  @Setter
  @Value("${bucket.image:jjimages}")
  private String imageBucket;

  private final ImageStorageService storage;
  private final MinioClient minioClient;
  private final ImageService imageService;
  private final ImageModerationService moderation;
  private final UserImageRepository userImageRepository;
  private final UserRepository userRepository;
  private final JdbcTemplate jdbcTemplate;

  /** Told to a user whose picture the content filter refused. */
  public static final String REJECTED_MESSAGE =
      "This picture can't be posted. Nudity and sexual content aren't allowed.";

  public AlbumImageService(
      final ImageStorageService storage,
      final MinioClient minioClient,
      final ImageService imageService,
      final ImageModerationService moderation,
      final UserImageRepository userImageRepository,
      final UserRepository userRepository,
      final JdbcTemplate jdbcTemplate) {
    this.storage = storage;
    this.minioClient = minioClient;
    this.imageService = imageService;
    this.moderation = moderation;
    this.userImageRepository = userImageRepository;
    this.userRepository = userRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  /** A picture's data and mime type. */
  public record Picture(InputStream stream, String mimeType) {}

  /** What a {@link #migrateBlobs()} run did. */
  public record MigrationResult(int copied, int failed) {}

  /**
   * Store a new picture and its thumbnail, once the content filter has passed it. Nothing is
   * saved if storing either fails.
   *
   * @throws IllegalArgumentException if the data isn't a JPEG, PNG or GIF, or is too large
   * @throws BadRequestException if the content filter refused the picture
   */
  public UserImage upload(final int ownerId, final String title, final byte[] data)
      throws IOException, ServiceException {
    final ImageService.ImageInfo info = imageService.describe(data);
    final byte[] thumbnail = imageService.thumbnail(data, THUMBNAIL_SIZE);

    // the thumbnail is already small and the right way up; the model doesn't need more
    final ImageModerationService.Result verdict = moderation.moderate(thumbnail);
    if (verdict.rejected()) {
      log.info("Picture upload by user {} rejected by the content filter: {}", ownerId, verdict.category());
      throw new BadRequestException(REJECTED_MESSAGE);
    }

    final String name = ownerId + "/" + UUID.randomUUID();
    final String filename = name + "." + info.extension();
    final String thumbnailName = name + "_thumb.jpg";
    put(filename, info.mimeType(), data);
    try {
      put(thumbnailName, MediaType.IMAGE_JPEG_VALUE, thumbnail);
    } catch (final ServiceException e) {
      remove(filename);
      throw e;
    }

    final UserImage image = new UserImage();
    image.setUser(userRepository.getReferenceById(ownerId));
    image.setTitle(title);
    image.setMimeType(info.mimeType());
    image.setModified(new Date());
    image.setFilename(filename);
    image.setThumbnail(thumbnailName);
    image.setSize((long) data.length);
    image.setModerationStatus(verdict.status());
    try {
      return userImageRepository.saveAndFlush(image);
    } catch (final RuntimeException e) {
      remove(filename);
      remove(thumbnailName);
      log.error("Could not save picture for user {}", ownerId, e);
      throw new ServiceException("Unable to save picture");
    }
  }

  /**
   * @param viewerId the logged in user, or 0; a picture the content filter hasn't approved is
   *     only there for its owner
   * @throws NotFoundException if there is no such picture, or the viewer may not see it
   */
  public Picture picture(final int id, final int viewerId) throws ServiceException {
    final UserImage image = visible(id, viewerId);
    if (image.getFilename() != null) {
      return new Picture(get(image.getFilename()), image.getMimeType());
    }
    return new Picture(new ByteArrayInputStream(blob(id)), image.getMimeType());
  }

  /**
   * A JPEG thumbnail.
   *
   * @param viewerId the logged in user, or 0
   * @throws NotFoundException if there is no such picture, or the viewer may not see it
   */
  public Picture thumbnail(final int id, final int viewerId) throws ServiceException, IOException {
    final UserImage image = visible(id, viewerId);
    if (image.getThumbnail() != null) {
      return new Picture(get(image.getThumbnail()), MediaType.IMAGE_JPEG_VALUE);
    }
    // not copied to MinIO yet
    final byte[] data = image.getFilename() != null ? get(image.getFilename()).readAllBytes() : blob(id);
    try {
      return new Picture(
          new ByteArrayInputStream(imageService.thumbnail(data, THUMBNAIL_SIZE)),
          MediaType.IMAGE_JPEG_VALUE);
    } catch (final IllegalArgumentException e) {
      throw new NotFoundException("Picture " + id + " can't be shown as a thumbnail");
    }
  }

  /**
   * Delete one of the user's pictures.
   *
   * @throws NotFoundException if there is no such picture
   * @throws ForbiddenException if it belongs to someone else
   */
  @Transactional
  public void delete(final int id, final int ownerId) {
    final UserImage image = find(id);
    if (image.getUser() == null || image.getUser().getId() != ownerId) {
      throw new ForbiddenException("You can only delete your own pictures");
    }

    jdbcTemplate.update("DELETE FROM user_images_album_map WHERE image_id = ?", id);
    userImageRepository.delete(image);
    userImageRepository.flush();
    afterCommit(() -> removeFiles(image));
  }

  /** Delete all of a user's pictures, for when their account is deleted. */
  @Transactional
  public void deleteAll(final int ownerId) {
    final List<UserImage> images =
        jdbcTemplate.query(
            "SELECT id, filename, thumbnail FROM user_images WHERE owner = ?",
            (rs, row) -> {
              final UserImage image = new UserImage();
              image.setId(rs.getInt("id"));
              image.setFilename(rs.getString("filename"));
              image.setThumbnail(rs.getString("thumbnail"));
              return image;
            },
            ownerId);

    jdbcTemplate.update("DELETE FROM user_images_album_map WHERE owner = ?", ownerId);
    jdbcTemplate.update("DELETE FROM user_images WHERE owner = ?", ownerId);
    afterCommit(() -> images.forEach(this::removeFiles));
  }

  /** Files are removed once the rows are gone for good, not before a rollback could undo it. */
  private static void afterCommit(final Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  /**
   * Copy pictures stored in the database to MinIO, with a thumbnail. Each copy is checked against
   * the stored size before the row is marked as migrated. The database copy is kept.
   */
  public MigrationResult migrateBlobs() {
    final List<Integer> ids =
        jdbcTemplate.queryForList(
            "SELECT id FROM user_images WHERE image IS NOT NULL AND migrated IS NULL ORDER BY id",
            Integer.class);

    int copied = 0;
    final List<Integer> failed = new ArrayList<>();
    for (final int id : ids) {
      try {
        migrate(id);
        copied++;
      } catch (final Exception e) {
        log.warn("Could not copy picture {} to MinIO", id, e);
        failed.add(id);
      }
    }
    if (!ids.isEmpty()) {
      log.info(
          "Pictures copied to MinIO: {} of {}{}",
          copied,
          ids.size(),
          failed.isEmpty() ? "" : ", failed: " + failed);
    }
    return new MigrationResult(copied, failed.size());
  }

  void migrate(final int id) throws Exception {
    final Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT owner, mimetype, image FROM user_images WHERE id = ?", id);
    final byte[] data = (byte[]) row.get("image");
    if (data == null || data.length == 0) throw new IllegalStateException("no image data");

    String mimeType = row.get("mimetype") == null ? "" : row.get("mimetype").toString().trim();
    String extension = "bin";
    byte[] thumbnail = null;
    try {
      final ImageService.ImageInfo info = imageService.describe(data);
      mimeType = info.mimeType();
      extension = info.extension();
      thumbnail = imageService.thumbnail(data, THUMBNAIL_SIZE);
    } catch (final IllegalArgumentException e) {
      // copy it as it is; it's shown as before, without a thumbnail
      log.warn("Picture {} isn't a readable JPEG, PNG or GIF: {}", id, e.getMessage());
    }

    // named after the row, so a retry replaces a partial copy
    final String name = row.get("owner") + "/picture_" + id;
    final String filename = name + "." + extension;
    put(filename, mimeType, data);
    final long stored =
        minioClient
            .statObject(StatObjectArgs.builder().bucket(imageBucket).object(filename).build())
            .size();
    if (stored != data.length) {
      throw new IllegalStateException("stored " + stored + " bytes of " + data.length);
    }

    String thumbnailName = null;
    if (thumbnail != null) {
      thumbnailName = name + "_thumb.jpg";
      put(thumbnailName, MediaType.IMAGE_JPEG_VALUE, thumbnail);
    }

    // modified = modified: the column updates itself on every change otherwise
    jdbcTemplate.update(
        "UPDATE user_images SET filename = ?, thumbnail = ?, size = ?, mimetype = ?,"
            + " migrated = NOW(), modified = modified WHERE id = ?",
        filename,
        thumbnailName,
        data.length,
        mimeType,
        id);
  }

  private UserImage find(final int id) {
    return userImageRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("No picture " + id));
  }

  /** The picture, if it is approved or the viewer owns it; otherwise it doesn't exist for them. */
  private UserImage visible(final int id, final int viewerId) {
    final UserImage image = find(id);
    if (image.getModerationStatus() != ModerationStatus.APPROVED
        && (image.getUser() == null || image.getUser().getId() != viewerId)) {
      throw new NotFoundException("No picture " + id);
    }
    return image;
  }

  private byte[] blob(final int id) {
    final List<byte[]> data =
        jdbcTemplate.query(
            "SELECT image FROM user_images WHERE id = ?", (rs, row) -> rs.getBytes(1), id);
    if (data.isEmpty() || data.get(0) == null) throw new NotFoundException("No picture " + id);
    return data.get(0);
  }

  private void put(final String name, final String mimeType, final byte[] data)
      throws ServiceException {
    try {
      storage.uploadFile(imageBucket, name, mimeType, new ByteArrayInputStream(data));
    } catch (final Exception e) {
      log.error("Could not store picture {}", name, e);
      throw new ServiceException("Unable to store picture");
    }
  }

  private InputStream get(final String name) throws ServiceException {
    try {
      return storage.downloadFile(imageBucket, name);
    } catch (final ErrorResponseException e) {
      if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
        throw new NotFoundException("Picture file missing");
      }
      log.error("Could not fetch picture {}", name, e);
      throw new ServiceException("Unable to fetch picture");
    } catch (final Exception e) {
      log.error("Could not fetch picture {}", name, e);
      throw new ServiceException("Unable to fetch picture");
    }
  }

  private void removeFiles(final UserImage image) {
    if (image.getFilename() != null) remove(image.getFilename());
    if (image.getThumbnail() != null) remove(image.getThumbnail());
  }

  /** Best effort; a leftover file only takes space. */
  private void remove(final String name) {
    try {
      storage.deleteFile(imageBucket, name);
    } catch (final Exception e) {
      log.warn("Could not delete picture file {}", name, e);
    }
  }
}
