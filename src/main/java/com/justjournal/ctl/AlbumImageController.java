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
import static com.justjournal.core.Constants.PARAM_TITLE;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.UserImage;
import com.justjournal.services.AlbumImageService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pictures in the user's photo album: showing them and their thumbnails, and uploading and
 * deleting them by their owner.
 *
 * @author Lucas Holt
 */
@Slf4j
@RequestMapping("/AlbumImage")
@Controller
public class AlbumImageController {

  private static final int MIN_FILE_SIZE = 500;

  /** Largest picture accepted; more than a phone camera photo needs. */
  static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

  /** Longest title the database holds. */
  static final int MAX_TITLE_LENGTH = 150;

  /** A picture never changes; a deleted one may be shown from a cache for this long. */
  static final Duration CACHE_TIME = Duration.ofDays(1);

  private final AlbumImageService albumImageService;

  public AlbumImageController(final AlbumImageService albumImageService) {
    this.albumImageService = albumImageService;
  }

  @GetMapping(value = "/{id}/thumbnail")
  public ResponseEntity<Resource> getThumbnail(@PathVariable(PARAM_ID) final int id)
      throws ServiceException, IOException {
    return send(albumImageService.thumbnail(id));
  }

  @GetMapping(value = "/{id}")
  public ResponseEntity<Resource> getByPath(@PathVariable(PARAM_ID) final int id)
      throws ServiceException {
    return get(id);
  }

  /** The form linked from the pictures RSS feed. */
  @GetMapping(value = "")
  public ResponseEntity<Resource> get(@RequestParam(PARAM_ID) final int id)
      throws ServiceException {
    if (id < 1) throw new BadRequestException("Invalid picture id");
    return send(albumImageService.picture(id));
  }

  private static ResponseEntity<Resource> send(final AlbumImageService.Picture picture) {
    MediaType type = MediaType.APPLICATION_OCTET_STREAM;
    try {
      if (picture.mimeType() != null && !picture.mimeType().isBlank()) {
        type = MediaType.parseMediaType(picture.mimeType().trim());
      }
    } catch (final InvalidMediaTypeException e) {
      log.warn("Picture has an invalid mime type {}", picture.mimeType());
    }
    return ResponseEntity.ok()
        .contentType(type)
        .cacheControl(CacheControl.maxAge(CACHE_TIME).cachePublic())
        .body(new InputStreamResource(picture.stream()));
  }

  @PostMapping(value = "")
  public ResponseEntity<Void> upload(
      @RequestPart("file") final MultipartFile file,
      @RequestParam(value = PARAM_TITLE, defaultValue = "untitled") final String title,
      final HttpSession session)
      throws IOException, ServiceException {
    final int userId = loggedInUser(session);

    if (file.getSize() > MAX_UPLOAD_BYTES) {
      throw new MaxUploadSizeExceededException(MAX_UPLOAD_BYTES);
    }
    if (file.isEmpty() || file.getSize() < MIN_FILE_SIZE) {
      throw new BadRequestException("File is empty or too small");
    }
    final String cleanTitle = title.isBlank() ? "untitled" : title.trim();
    if (cleanTitle.length() > MAX_TITLE_LENGTH) {
      throw new BadRequestException("Title must be " + MAX_TITLE_LENGTH + " characters or less");
    }

    final UserImage image;
    try {
      image = albumImageService.upload(userId, cleanTitle, file.getBytes());
    } catch (final IllegalArgumentException e) {
      throw new BadRequestException(
          "Unsupported picture. Upload a JPEG, PNG or GIF image of up to 100 megapixels.");
    }
    log.info("Picture {} uploaded by user {}", image.getId(), userId);
    return ResponseEntity.created(URI.create("/AlbumImage/" + image.getId())).build();
  }

  @DeleteMapping(value = "/{id}")
  public ResponseEntity<Void> delete(@PathVariable(PARAM_ID) final int id, final HttpSession session) {
    final int userId = loggedInUser(session);
    if (id < 1) throw new BadRequestException("Invalid picture id");

    albumImageService.delete(id, userId);
    return ResponseEntity.noContent().build();
  }

  private static int loggedInUser(final HttpSession session) {
    final Integer userId = (Integer) session.getAttribute(LOGIN_ATTRID);
    if (userId == null || userId < 1) {
      throw new ForbiddenException("You must be logged in to change pictures");
    }
    return userId;
  }
}
