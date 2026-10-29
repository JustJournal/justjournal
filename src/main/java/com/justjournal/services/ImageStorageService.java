/*
 * Copyright (c) 2003-2021 Lucas Holt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.justjournal.services;

import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.AvatarSource;
import com.justjournal.model.PrefBool;
import com.justjournal.model.UserPic;
import com.justjournal.model.UserPref;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserPrefRepository;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Optional;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** @author Lucas Holt */
@Slf4j
@Service
public class ImageStorageService {

  @Setter
  @Value("${bucket.avatar:jjavatar}")
  private String avatarBucket;

  @Setter
  @Value("${bucket.image:jjimages}")
  private String imageBucket;

  private final MinioClient minioClient;

  private final UserImageService userImageService;

  private final UserPicRepository userPicRepository;

  private final UserPrefRepository userPrefRepository;

  private static final String USER_ID_ARGUMENT = "userId";

  /** MinIO's error code for an object that doesn't exist. */
  private static final String NO_SUCH_KEY = "NoSuchKey";

  public ImageStorageService(MinioClient minioClient, UserImageService userImageService, UserPicRepository userPicRepository, UserPrefRepository userPrefRepository) {
    this.minioClient = minioClient;
    this.userImageService = userImageService;
    this.userPicRepository = userPicRepository;
    this.userPrefRepository = userPrefRepository;
  }

  public void deleteAvatar(final int userId) throws ServiceException {
    final Optional<UserPref> pref = userPrefRepository.findById(userId);
    if (pref.isEmpty()) {
      throw new IllegalArgumentException(USER_ID_ARGUMENT);
    }

    final Optional<UserPic> userPic = userPicRepository.findById(userId);
    if (userPic.isPresent()) {

      String filename = userPic.get().getFilename();
      if (filename == null) filename = getAvatarFileName(userId, userPic.get().getMimeType());

      try {
        deleteFile(avatarBucket, filename);
        userPicRepository.deleteById(userId);

        pref.get().setShowAvatar(PrefBool.N);
        userPrefRepository.save(pref.get());
      } catch (final Exception e) {
        log.error("Unable to delete avatar file {} for user {}", filename, userId, e);
        throw new ServiceException("Unable to delete avatar");
      }
    }
  }

  /**
   * Store an avatar image. The image is uploaded before the record is saved, so a failed upload
   * leaves any previous avatar as it was.
   */
  public void uploadAvatar(
      final int userId,
      @NonNull final String mimeType,
      @NonNull final AvatarSource source,
      @NonNull final InputStream is)
      throws ServiceException {

    if (userId < 1) throw new IllegalArgumentException(USER_ID_ARGUMENT);

    final String filename = getAvatarFileName(userId, mimeType);
    try {
      uploadFile(avatarBucket, filename, mimeType, is);
    } catch (final Exception e) {
      log.error("Could not upload avatar for user {}", userId, e);
      throw new ServiceException("Unable to save avatar");
    }

    final UserPic entity = userPicRepository.findById(userId).orElseGet(UserPic::new);
    final String previousFilename = entity.getFilename();
    entity.setId(userId);
    entity.setMimeType(mimeType);
    entity.setModified(Calendar.getInstance().getTime());
    entity.setSource(source);
    entity.setFilename(filename);
    userPicRepository.save(entity);

    // a new format has a new name; the old file is no longer used
    if (previousFilename != null && !previousFilename.equals(filename)) {
      try {
        deleteFile(avatarBucket, previousFilename);
      } catch (final Exception e) {
        log.warn("Could not delete old avatar {} for user {}", previousFilename, userId, e);
      }
    }
  }

  /**
   * Turn showing the user's avatar on their journal on or off. Uploading an avatar turns it on;
   * storing a Gravatar or moving an old upload into storage leaves the setting as it is.
   */
  public void setShowAvatar(final int userId, final boolean show) {
    final UserPref pref =
        userPrefRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(USER_ID_ARGUMENT));
    pref.setShowAvatar(show ? PrefBool.Y : PrefBool.N);
    userPrefRepository.save(pref);
  }

  public boolean isShowAvatar(final int userId) {
    return userPrefRepository
        .findById(userId)
        .map(pref -> pref.getShowAvatar() == PrefBool.Y)
        .orElseThrow(() -> new IllegalArgumentException(USER_ID_ARGUMENT));
  }

  /** Whether the user has an avatar image in storage, not just a record of one. */
  public boolean hasAvatarImage(final int userId) throws ServiceException {
    final Optional<UserPic> userPic = userPicRepository.findById(userId);
    if (userPic.isEmpty() || userPic.get().getFilename() == null) return false;

    try {
      minioClient.statObject(
          StatObjectArgs.builder().bucket(avatarBucket).object(userPic.get().getFilename()).build());
      return true;
    } catch (final ErrorResponseException e) {
      if (NO_SUCH_KEY.equals(e.errorResponse().code())) return false;
      log.error("Could not check avatar for user {}", userId, e);
      throw new ServiceException("Unable to check avatar");
    } catch (final Exception e) {
      log.error("Could not check avatar for user {}", userId, e);
      throw new ServiceException("Unable to check avatar");
    }
  }

  /** A stored avatar image and its mime type. */
  public record StoredAvatar(InputStream stream, String mimeType) {}

  /**
   * @throws NotFoundException if the user has no avatar image
   */
  public StoredAvatar downloadAvatar(final int userId) throws ServiceException {
    final UserPic userPic =
        userPicRepository
            .findById(userId)
            .filter(pic -> pic.getFilename() != null)
            .orElseThrow(() -> new NotFoundException("No avatar for user " + userId));

    try {
      return new StoredAvatar(
          downloadFile(avatarBucket, userPic.getFilename()), userPic.getMimeType());
    } catch (final ErrorResponseException e) {
      if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
        throw new NotFoundException("Avatar image missing for user " + userId);
      }
      log.error("Could not fetch avatar for user {}", userId, e);
      throw new ServiceException("Unable to download avatar");
    } catch (final Exception e) {
      log.error("Could not fetch avatar for user {}", userId, e);
      throw new ServiceException("Unable to download avatar");
    }
  }

  protected String getAvatarFileName(final int id, @NonNull final String mimeType) {
    final String name = "avatar_" + id;

    if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
      return name + ".jpg";
    } else if (mimeType.contains("png") || mimeType.contains("ping")) {
      return name + ".png";
    } else if (mimeType.contains("gif")) {
      return name + ".gif";
    }

    return name;
  }

  /**
   * upload a file and will close inputstream upon completion.
   *
   * @param bucketName bucket to store images in
   * @param objectName image name or file name
   * @param mimeType image mime type
   * @param is input stream (preferably a ByteArrayInputStream)
   * @throws IOException
   * @throws MinioException if the storage operation fails
   */
  public void uploadFile(
      @NonNull final String bucketName,
      @NonNull final String objectName,
      @NonNull final String mimeType,
      @NonNull final InputStream is)
      throws IOException, MinioException {

    if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
      log.debug("Make bucket: {}", bucketName);
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }

    log.debug("Upload file bucket: {}  objectName: {}", bucketName, objectName);
    if (is instanceof ByteArrayInputStream)
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                  is, (long) is.available(), null)
              .contentType(mimeType)
              .build());
    else
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(is, null, 10485760L)
              .contentType(mimeType)
              .build());
    is.close();
  }

  public InputStream downloadFile(
      @NonNull final String bucketName, @NonNull final String objectName)
      throws MinioException {

    log.debug("Download file bucket: {}  objectName: {}", bucketName, objectName);
    return minioClient.getObject(
        GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
  }

  public void deleteFile(@NonNull final String bucketName, @NonNull final String objectName)
      throws MinioException {

    log.debug("Delete file bucket: {}  objectName: {}", bucketName, objectName);
    minioClient.removeObject(
        RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
  }
}
