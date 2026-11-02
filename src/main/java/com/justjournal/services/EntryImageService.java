/*
 * Copyright (c) 2003-2026 Lucas Holt
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

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryImage;
import com.justjournal.model.ModerationStatus;
import com.justjournal.repository.EntryImageRepository;
import com.justjournal.repository.EntryRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pictures attached to blog posts. A picture is uploaded on its own, checked by the content
 * filter, and stored in MinIO with a smaller JPEG to show on the post; saving the post attaches
 * it. It is shown to its owner, and to others only once it is approved and attached to a post
 * they may see.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class EntryImageService {

  /** Told to a user who keeps uploading pictures without saving a post with them. */
  public static final String TOO_MANY_PENDING_MESSAGE =
      "You have too many pictures waiting to be added to a post. Save a post with one of them, or"
          + " try again tomorrow.";

  /** Longest alt text the database holds. */
  public static final int MAX_ALT_LENGTH = 255;

  /** The display image fits in a square this many pixels wide. */
  @Setter
  @Value("${entry-attachments.image.display-size:1024}")
  private int displaySize = 1024;

  /** How many pictures a user may have uploaded that no post has taken yet. */
  @Setter
  @Value("${entry-attachments.image.max-pending-per-user:10}")
  private int maxPendingPerUser = 10;

  private final StoredImageFiles files;
  private final ImageService imageService;
  private final ImageModerationService moderation;
  private final EntryImageRepository entryImageRepository;
  private final EntryRepository entryRepository;
  private final EntryAccessService entryAccessService;

  public EntryImageService(
      final StoredImageFiles files,
      final ImageService imageService,
      final ImageModerationService moderation,
      final EntryImageRepository entryImageRepository,
      final EntryRepository entryRepository,
      final EntryAccessService entryAccessService) {
    this.files = files;
    this.imageService = imageService;
    this.moderation = moderation;
    this.entryImageRepository = entryImageRepository;
    this.entryRepository = entryRepository;
    this.entryAccessService = entryAccessService;
  }

  /**
   * A picture's data and mime type.
   *
   * @param publicCacheable whether shared caches may keep it: it is approved and attached to a
   *     post everyone may see. False for an owner's view of a picture awaiting review or on a
   *     private post, which must not be cached for others.
   */
  public record Picture(InputStream stream, String mimeType, boolean publicCacheable) {}

  /**
   * Store a new picture and its display image, once the content filter has passed it. Nothing is
   * saved if storing either fails. The picture isn't attached to a post yet.
   *
   * @param alt what the picture shows, or null; blank counts as none
   * @throws IllegalArgumentException if the data isn't a JPEG, PNG or GIF, or is too large
   * @throws BadRequestException if the content filter refused the picture, the alt text is too
   *     long, or the user has too many pictures waiting for a post
   */
  public EntryImage upload(final int ownerId, final byte[] data, final String alt)
      throws IOException, ServiceException {
    final String altText = alt == null || alt.isBlank() ? null : alt.trim();
    if (altText != null && altText.length() > MAX_ALT_LENGTH) {
      throw new BadRequestException("Alt text must be " + MAX_ALT_LENGTH + " characters or less");
    }
    if (entryImageRepository.countByOwnerIdAndEntryIdIsNull(ownerId) >= maxPendingPerUser) {
      log.info("Picture upload by user {} refused: too many pictures waiting for a post", ownerId);
      throw new BadRequestException(TOO_MANY_PENDING_MESSAGE);
    }

    final ImageService.ImageInfo info = imageService.describe(data);
    final byte[] display = imageService.thumbnail(data, displaySize);

    // the display image is the right way up; the original goes along so animation is noticed
    final ImageModerationService.Result verdict = moderation.moderate(display, data);
    if (verdict.rejected()) {
      log.info(
          "Post picture upload by user {} rejected by the content filter: {}",
          ownerId,
          verdict.category());
      throw new BadRequestException(AlbumImageService.REJECTED_MESSAGE);
    }
    final ImageService.ImageInfo displayInfo = imageService.describe(display);

    final String name = "entry/" + ownerId + "/" + UUID.randomUUID();
    final String filename = name + "." + info.extension();
    final String displayName = name + "_display.jpg";
    files.put(filename, info.mimeType(), data);
    try {
      files.put(displayName, MediaType.IMAGE_JPEG_VALUE, display);
    } catch (final ServiceException e) {
      files.remove(filename);
      throw e;
    }

    final EntryImage image = new EntryImage();
    image.setOwnerId(ownerId);
    image.setMimeType(info.mimeType());
    image.setFilename(filename);
    image.setDisplay(displayName);
    image.setSize(data.length);
    image.setWidth(displayInfo.width());
    image.setHeight(displayInfo.height());
    image.setAltText(altText);
    image.setModerationStatus(verdict.status());
    image.setModerationCategory(verdict.category());
    image.setCreated(new Date());
    try {
      return entryImageRepository.saveAndFlush(image);
    } catch (final RuntimeException e) {
      files.remove(filename);
      files.remove(displayName);
      log.error("Could not save post picture for user {}", ownerId, e);
      throw new ServiceException("Unable to save picture");
    }
  }

  /**
   * The picture as uploaded.
   *
   * @param viewerId the logged in user, or 0
   * @throws NotFoundException if there is no such picture, or the viewer may not see it
   */
  public Picture original(final int id, final int viewerId) throws ServiceException {
    final EntryImage image = find(id);
    final Entry entry = entryOf(image);
    if (!visible(image, entry, viewerId)) throw new NotFoundException("No picture " + id);
    return new Picture(
        files.get(image.getFilename()), image.getMimeType(), publicCacheable(image, entry));
  }

  /**
   * The JPEG shown on the post.
   *
   * @param viewerId the logged in user, or 0
   * @throws NotFoundException if there is no such picture, or the viewer may not see it
   */
  public Picture display(final int id, final int viewerId) throws ServiceException {
    final EntryImage image = find(id);
    final Entry entry = entryOf(image);
    if (!visible(image, entry, viewerId)) throw new NotFoundException("No picture " + id);
    return new Picture(
        files.get(image.getDisplay()), MediaType.IMAGE_JPEG_VALUE, publicCacheable(image, entry));
  }

  /**
   * Whether the viewer may see the picture: they own it, or it is approved and attached to a post
   * they may see.
   *
   * @param entry the post it is attached to, or null
   * @param viewerId the logged in user, or 0
   */
  public boolean visible(final EntryImage image, final Entry entry, final int viewerId) {
    if (viewerId > 0 && image.getOwnerId() == viewerId) return true;
    return image.getModerationStatus() == ModerationStatus.APPROVED
        && attachedTo(image, entry)
        && entryAccessService.canView(entry, viewerId);
  }

  /**
   * Whether shared caches may keep the picture and feeds may carry it: it is approved and attached
   * to a post everyone may see.
   *
   * @param entry the post it is attached to, or null
   */
  public boolean publicCacheable(final EntryImage image, final Entry entry) {
    return image.getModerationStatus() == ModerationStatus.APPROVED
        && attachedTo(image, entry)
        && entryAccessService.isPubliclyVisible(entry);
  }

  /**
   * Delete one of the user's pictures, attached or not. Its files are removed once the
   * transaction commits.
   *
   * @throws NotFoundException if there is no such picture
   * @throws ForbiddenException if it belongs to someone else
   */
  @Transactional
  public void delete(final int id, final int ownerId) {
    final EntryImage image = find(id);
    if (image.getOwnerId() != ownerId) {
      throw new ForbiddenException("You can only delete your own pictures");
    }
    entryImageRepository.delete(image);
    entryImageRepository.flush();
    deleteFiles(image);
  }

  /** Remove the picture's files once the transaction commits, or now if there is none. */
  public void deleteFiles(final EntryImage image) {
    files.removeAfterCommit(image.getFilename(), image.getDisplay());
  }

  /**
   * The picture as uploaded, for the content filter to judge again. Doesn't check who may see it.
   *
   * @throws NotFoundException if the file is missing
   */
  public byte[] readOriginalForModeration(final EntryImage image)
      throws ServiceException, IOException {
    try (InputStream in = files.get(image.getFilename())) {
      return in.readAllBytes();
    }
  }

  private EntryImage find(final int id) {
    return entryImageRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("No picture " + id));
  }

  private Entry entryOf(final EntryImage image) {
    if (image.getEntryId() == null) return null;
    return entryRepository.findById(image.getEntryId()).orElse(null);
  }

  /** The post is the one the picture is attached to, and belongs to the picture's owner. */
  private static boolean attachedTo(final EntryImage image, final Entry entry) {
    return entry != null
        && image.getEntryId() != null
        && image.getEntryId() == entry.getId()
        && entry.getUser() != null
        && entry.getUser().getId() == image.getOwnerId();
  }
}
