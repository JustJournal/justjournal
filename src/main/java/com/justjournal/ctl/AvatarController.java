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
package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.PARAM_ID;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.AvatarSource;
import com.justjournal.model.ModerationStatus;
import com.justjournal.services.ImageModerationService;
import com.justjournal.services.ImageService;
import com.justjournal.services.ImageStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** @author Lucas Holt */
@Slf4j
@Controller
@RequestMapping("/Avatar")
public class AvatarController {

  private final ImageStorageService imageStorageService;

  private final ImageService imageService;

  private final ImageModerationService imageModerationService;

  public AvatarController(
      ImageStorageService imageStorageService,
      ImageService imageService,
      ImageModerationService imageModerationService) {
    this.imageStorageService = imageStorageService;
    this.imageService = imageService;
    this.imageModerationService = imageModerationService;
  }

  /** Told to a user whose avatar the content filter refused. */
  static final String REJECTED_MESSAGE =
      "This image can't be used as an avatar. Nudity and sexual content aren't allowed.";

  /** Told to a user whose avatar is kept but not shown until someone has looked at it. */
  static final String REVIEW_MESSAGE =
      "Your avatar was saved. The content filter wasn't sure about it, so it will be shown to"
          + " others once it has been reviewed.";

  /** Largest avatar file accepted for upload. */
  static final long MAX_UPLOAD_BYTES = 100 * 1024;

  /** How long browsers and proxies may keep an avatar before checking for a new one. */
  static final Duration CACHE_TIME = Duration.ofHours(1);

  /**
   * The avatar image, in the format it was stored in. 404 if the user has no avatar, 403 if they
   * have turned it off or the content filter hasn't approved it, except for the user themselves.
   */
  @GetMapping("/{id}")
  public ResponseEntity<Resource> getByPath(
      @PathVariable(PARAM_ID) final int id, final HttpSession session) throws ServiceException {
    final boolean shown;
    try {
      shown = imageStorageService.isShowAvatar(id) && imageStorageService.isAvatarApproved(id);
    } catch (final IllegalArgumentException e) {
      throw new NotFoundException("No such user");
    }
    final Integer loggedIn = (Integer) session.getAttribute(LOGIN_ATTRID);
    final boolean owner = loggedIn != null && loggedIn == id;
    if (!shown && !owner) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore()).build();
    }

    final ImageStorageService.StoredAvatar avatar = imageStorageService.downloadAvatar(id);
    MediaType type = MediaType.IMAGE_JPEG;
    try {
      if (avatar.mimeType() != null && !avatar.mimeType().isBlank()) {
        type = MediaType.parseMediaType(avatar.mimeType());
      }
    } catch (final InvalidMediaTypeException e) {
      log.warn("Avatar for user {} has an invalid mime type {}", id, avatar.mimeType());
    }

    final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    return ResponseEntity.ok()
        .contentType(type)
        // a hidden avatar shown to its owner must not be cached for anyone else
        .cacheControl(shown ? CacheControl.maxAge(CACHE_TIME).cachePublic() : CacheControl.noStore())
        .headers(
            headers -> {
              if (shown) headers.setExpires(now.plus(CACHE_TIME));
            })
        .body(new InputStreamResource(avatar.stream()));
  }

  @PostMapping(value = "")
  public ResponseEntity processUpload(
      @RequestParam MultipartFile file, HttpSession session) {

    final Integer userIDasi = (Integer) session.getAttribute(LOGIN_ATTRID);
    int userID = 0;
    if (userIDasi != null) {
      userID = userIDasi;
    }

    /* Make sure we are logged in */
    if (userID < 1) {
      throw new ForbiddenException("You must be logged in to upload an avatar");
    }

    final String contentType = file.getContentType();
    if (contentType == null || !(contentType.equals(MediaType.IMAGE_JPEG_VALUE) ||
            contentType.equals(MediaType.IMAGE_PNG_VALUE) || contentType.equals(MediaType.IMAGE_GIF_VALUE) ||
            contentType.equals("image/tiff") || contentType.equals("image/bmp"))) {
      throw new BadRequestException(
          "Invalid file type. Only JPEG, PNG, GIF, TIFF and BMP images are allowed.");
    }

    final long sizeInBytes = file.getSize();
    // must be large enough
    if (file.isEmpty() || sizeInBytes < 500) {
      throw new BadRequestException("Invalid file");
    }
    if (sizeInBytes > MAX_UPLOAD_BYTES) {
      throw new BadRequestException("Avatars must be 100 KB or smaller.");
    }

    final ModerationStatus status;
    try {
      final ImageService.EncodedImage avatar = imageService.encodeAvatar(file.getBytes());

      final ImageModerationService.Result verdict = imageModerationService.moderate(file.getBytes());
      if (verdict.rejected()) {
        log.info("Avatar upload by user {} rejected by the content filter: {}", userID, verdict.category());
        throw new BadRequestException(REJECTED_MESSAGE);
      }
      status = verdict.status();

      imageStorageService.uploadAvatar(
          userID, avatar.mimeType(), AvatarSource.UPLOAD, status, new ByteArrayInputStream(avatar.data()));
      imageStorageService.setShowAvatar(userID, true);

    } catch (final IllegalStateException | IllegalArgumentException e) {
      log.error(e.getMessage(), e);
      throw new BadRequestException("Invalid file");
    } catch (final IOException | ServiceException e) {
      log.error(e.getMessage(), e);
      return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    final URI location = URI.create("/Avatar/" + userID);
    if (status == ModerationStatus.REVIEW) {
      log.info("Avatar upload by user {} held for review", userID);
      return ResponseEntity.accepted()
          .location(location)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("message", REVIEW_MESSAGE));
    }
    return ResponseEntity.created(location).build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<String> delete(@PathVariable(PARAM_ID) final int id, final HttpSession session) throws ServiceException {

    // Retreive user id
    final Integer userIDasi = (Integer) session.getAttribute(LOGIN_ATTRID);
    // convert Integer to int type
    int userID = 0;
    if (userIDasi != null) {
      userID = userIDasi;
    }

    /* Make sure we are logged in */
    if (userID < 1 || userID!= id) {
      throw new ForbiddenException("You must be logged in to delete an avatar");
    }

    imageStorageService.deleteAvatar(userID);
    return ResponseEntity.noContent().build();
  }

  /** Whether the user has an avatar image, and whether it is shown on their journal. */
  public record AvatarPreference(boolean available, boolean show) {}

  @GetMapping(value = "/preference", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AvatarPreference> getPreference(final HttpSession session)
      throws ServiceException {
    final int userID = loggedInUser(session);
    return ResponseEntity.ok(
        new AvatarPreference(
            imageStorageService.hasAvatarImage(userID), imageStorageService.isShowAvatar(userID)));
  }

  @PutMapping(value = "/preference", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AvatarPreference> setPreference(
      @RequestBody final AvatarPreference preference, final HttpSession session)
      throws ServiceException {
    final int userID = loggedInUser(session);
    final boolean available = imageStorageService.hasAvatarImage(userID);
    if (preference.show() && !available) {
      throw new BadRequestException("Upload an avatar before turning it on.");
    }

    imageStorageService.setShowAvatar(userID, preference.show());
    return ResponseEntity.ok(new AvatarPreference(available, preference.show()));
  }

  private static int loggedInUser(final HttpSession session) {
    final Integer userID = (Integer) session.getAttribute(LOGIN_ATTRID);
    if (userID == null || userID < 1) {
      throw new ForbiddenException("You must be logged in to change avatar settings");
    }
    return userID;
  }
}
