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

import com.justjournal.services.ImageService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Display individual images in the user's photo album.
 *
 * @author Lucas Holt
 */
@Slf4j
@RequestMapping("/AlbumImage")
@Controller
public class AlbumImageController {

  private static final int MIN_FILE_SIZE = 500;
  private final JdbcTemplate jdbcTemplate;
  private final ImageService imageService;

  @Autowired
  public AlbumImageController(final JdbcTemplate jdbcTemplate, final ImageService imageService) {
    this.jdbcTemplate = jdbcTemplate;
    this.imageService = imageService;
  }

  @GetMapping(value = "/{id}/thumbnail")
  public ResponseEntity<byte[]> getThumbnail(@PathVariable(PARAM_ID) final int id)
      throws IOException {

    final ResponseEntity<byte[]> out = get(id);
    if (out.getStatusCode() != HttpStatus.OK) {
      return out;
    }

    final BufferedImage image = imageService.resizeAvatar(out.getBody());

    final HttpHeaders headers = new HttpHeaders();
    headers.setExpires(180);
    headers.setContentType(MediaType.IMAGE_JPEG);

    return ResponseEntity.ok()
            .headers(headers)
            .body(imageService.convertBufferedImageToJpeg(image));
  }

  @GetMapping(value = "/{id}")
  public ResponseEntity<byte[]> getByPath(@PathVariable(PARAM_ID) final int id) {
    return get(id);
  }

  @GetMapping(value = "")
  public ResponseEntity<byte[]> get(@RequestParam(PARAM_ID) final int id) {
    if (id < 1) {
      return ResponseEntity.badRequest().build();
    }

    try {
      return jdbcTemplate.execute("CALL getalbumimage(?)", (PreparedStatementCallback<ResponseEntity<byte[]>>) ps -> {
        ps.setInt(1, id);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            byte[] imageData = rs.getBytes("image");
            String mimeType = rs.getString("mimetype").trim();

            HttpHeaders headers = new HttpHeaders();
            headers.setExpires(180);
            headers.setContentType(MediaType.parseMediaType(mimeType));

            return ResponseEntity.ok().headers(headers).body(imageData);
          } else {
            return ResponseEntity.notFound().build();
          }
        }
      });
    } catch (DataAccessException e) {
      log.warn("Could not load image: ", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @PostMapping(value = "")
  public ResponseEntity<String> upload(
          @RequestPart("file") MultipartFile file,
          @RequestParam(value = PARAM_TITLE, defaultValue = "untitled") String title,
          HttpSession session)
          throws IOException {

    // Retrieve user id
    Integer userId = Optional.ofNullable((Integer) session.getAttribute(LOGIN_ATTRID)).orElse(0);
    /* Make sure we are logged in */
    if (userId < 1) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // must be large enough
    if (file.isEmpty() || file.getSize() < MIN_FILE_SIZE) {
      return ResponseEntity.badRequest().body("File is empty or too small");
    }

    final String contentType = file.getContentType();
    byte[] data = file.getBytes();

    try {
      int rowsAffected = jdbcTemplate.update(
              "INSERT INTO user_images (owner, title, modified, mimetype, image) VALUES (?, ?, now(), ?, ?)",
              userId, title, contentType, data
      );

      if (rowsAffected == 1) {
        log.info("Image uploaded successfully for user: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
      } else {
        log.warn("Unexpected number of rows affected during image upload: {}", rowsAffected);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error during upload");
      }
    } catch (DataAccessException e) {
      log.error("Error inserting image into database", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Database error during upload");
    }
  }

  @DeleteMapping(value = "/{id}")
  public ResponseEntity<String> delete(@PathVariable(PARAM_ID) final int id, final HttpSession session) {

    if (id < 1) {
      return ResponseEntity.badRequest().body("Invalid image ID");
    }

    // Retrieve user id
    final Integer userIDasi = (Integer) session.getAttribute(LOGIN_ATTRID);
    // convert Integer to int type
    int userID = 0;
    if (userIDasi != null) {
      userID = userIDasi;
    }

    /* Make sure we are logged in */
    if (userID < 1) {
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }

    try {
      String sql = "DELETE FROM user_images WHERE id = ? AND owner = ?";
      int rowsAffected = jdbcTemplate.update(sql, id, userID);

      if (rowsAffected == 0) {
        // No rows were deleted, possibly because the image doesn't exist or doesn't belong to the user
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
      }
    } catch (final DataAccessException dae) {
      log.error(dae.getMessage(), dae);
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    return new ResponseEntity<>(HttpStatus.OK);
  }
}
