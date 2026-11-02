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
package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.PARAM_ID;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.EntryImage;
import com.justjournal.model.ModerationStatus;
import com.justjournal.services.EntryImageService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
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
 * Pictures attached to blog posts: uploading one before the post is saved, showing it and its
 * display image, and deleting it by its owner.
 *
 * @author Lucas Holt
 */
@Slf4j
@RequestMapping("/EntryImage")
@Controller
public class EntryImageController {

  private static final int MIN_FILE_SIZE = 500;

  /** Largest picture accepted; more than a phone camera photo needs. */
  static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

  /**
   * A picture never changes, but its post can become private; a shared cache may show it until
   * this runs out.
   */
  static final Duration CACHE_TIME = Duration.ofHours(1);

  /** Told to a user whose picture is kept but not shown until someone has looked at it. */
  static final String REVIEW_MESSAGE =
      "Your picture was saved. The content filter wasn't sure about it, so only you can see it on"
          + " your post until it has been reviewed.";

  private final EntryImageService entryImageService;

  public EntryImageController(final EntryImageService entryImageService) {
    this.entryImageService = entryImageService;
  }

  @GetMapping(value = "/{id}")
  public ResponseEntity<Resource> get(@PathVariable(PARAM_ID) final int id, final HttpSession session)
      throws ServiceException {
    return send(entryImageService.original(id, viewer(session)));
  }

  @GetMapping(value = "/{id}/display")
  public ResponseEntity<Resource> getDisplay(
      @PathVariable(PARAM_ID) final int id, final HttpSession session) throws ServiceException {
    return send(entryImageService.display(id, viewer(session)));
  }

  private static ResponseEntity<Resource> send(final EntryImageService.Picture picture) {
    MediaType type = MediaType.APPLICATION_OCTET_STREAM;
    try {
      if (picture.mimeType() != null && !picture.mimeType().isBlank()) {
        type = MediaType.parseMediaType(picture.mimeType().trim());
      }
    } catch (final InvalidMediaTypeException e) {
      log.warn("Post picture has an invalid mime type {}", picture.mimeType());
    }
    return ResponseEntity.ok()
        .contentType(type)
        // only a picture everyone may see can be kept by shared caches
        .cacheControl(
            picture.publicCacheable()
                ? CacheControl.maxAge(CACHE_TIME).cachePublic()
                : CacheControl.noStore())
        .body(new InputStreamResource(picture.stream()));
  }

  /**
   * Upload a picture for a post that is about to be saved.
   *
   * @return 201 with the picture's id and the address of its display image, or 202 with a message
   *     too when the picture is held for review
   */
  @PostMapping(value = "")
  public ResponseEntity<Map<String, Object>> upload(
      @RequestPart("file") final MultipartFile file,
      @RequestParam(value = "alt", required = false) final String alt,
      final HttpSession session)
      throws IOException, ServiceException {
    final int userId = loggedInUser(session);

    if (file.getSize() > MAX_UPLOAD_BYTES) {
      throw new MaxUploadSizeExceededException(MAX_UPLOAD_BYTES);
    }
    if (file.isEmpty() || file.getSize() < MIN_FILE_SIZE) {
      throw new BadRequestException("File is empty or too small");
    }
    if (alt != null && alt.trim().length() > EntryImageService.MAX_ALT_LENGTH) {
      throw new BadRequestException(
          "Alt text must be " + EntryImageService.MAX_ALT_LENGTH + " characters or less");
    }

    final EntryImage image;
    try {
      image = entryImageService.upload(userId, file.getBytes(), alt);
    } catch (final IllegalArgumentException e) {
      throw new BadRequestException(
          "Unsupported picture. Upload a JPEG, PNG or GIF image of up to 100 megapixels.");
    }
    log.info("Post picture {} uploaded by user {}", image.getId(), userId);
    final URI location = URI.create("/EntryImage/" + image.getId());
    final String url = "/EntryImage/" + image.getId() + "/display";
    if (image.getModerationStatus() == ModerationStatus.REVIEW) {
      log.info("Post picture {} held for review", image.getId());
      return ResponseEntity.accepted()
          .location(location)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("id", image.getId(), "url", url, "message", REVIEW_MESSAGE));
    }
    return ResponseEntity.created(location)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("id", image.getId(), "url", url));
  }

  @DeleteMapping(value = "/{id}")
  public ResponseEntity<Void> delete(@PathVariable(PARAM_ID) final int id, final HttpSession session) {
    final int userId = loggedInUser(session);
    if (id < 1) throw new BadRequestException("Invalid picture id");

    entryImageService.delete(id, userId);
    return ResponseEntity.noContent().build();
  }

  /** The logged in user, or 0 for a reader who isn't logged in. */
  private static int viewer(final HttpSession session) {
    final Integer userId = (Integer) session.getAttribute(LOGIN_ATTRID);
    return userId == null ? 0 : userId;
  }

  private static int loggedInUser(final HttpSession session) {
    final Integer userId = (Integer) session.getAttribute(LOGIN_ATTRID);
    if (userId == null || userId < 1) {
      throw new ForbiddenException("You must be logged in to change pictures");
    }
    return userId;
  }
}
