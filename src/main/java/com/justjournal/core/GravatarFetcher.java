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
package com.justjournal.core;


import com.github.natche.gravatarjavaclient.avatar.GravatarAvatarRequest;
import com.github.natche.gravatarjavaclient.enums.GravatarDefaultImageType;
import com.github.natche.gravatarjavaclient.enums.GravatarRating;
import com.github.natche.gravatarjavaclient.enums.GravatarUseJpgSuffix;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.AvatarSource;
import com.justjournal.model.User;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.ImageService;
import com.justjournal.services.ImageStorageService;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;

/** @author Lucas Holt */
@Slf4j
@Component
public class GravatarFetcher {

  private static final int GRAVATAR_FETCH_PAUSE_MS = 10000;

  final ImageStorageService imageStorageService;

  private final ImageService imageService;

  final UserPicRepository userPicRepository;

  private final UserRepository userRepository;

  private final TransactionTemplate transactionTemplate;

  public GravatarFetcher(ImageStorageService imageStorageService, ImageService imageService, UserPicRepository userPicRepository, UserRepository userRepository, TransactionTemplate transactionTemplate) {
    this.imageStorageService = imageStorageService;
    this.imageService = imageService;
    this.userPicRepository = userPicRepository;
    this.userRepository = userRepository;
    this.transactionTemplate = transactionTemplate;
  }

  private byte[] getGravatar(final String email) {

    GravatarAvatarRequest request = GravatarAvatarRequest.fromEmail(email)
            .setSize(100)
            .setRating(GravatarRating.R)
            .setShouldAppendJpgSuffix(GravatarUseJpgSuffix.True)
            .setDefaultImageType(GravatarDefaultImageType._404);

    var image = request.getBufferedImage();

    try {
      return bufferedImageToByteArray(image, "jpg");
    } catch (IOException e) {
      log.error("Error converting BufferedImage to byte array", e);
      return new byte[0];
    }
  }

  private byte[] bufferedImageToByteArray(BufferedImage image, String formatName) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, formatName, baos);
    return baos.toByteArray();
  }

  private void upload(
      final int userId, final String type, final AvatarSource source, final byte[] image) {
    if (image == null || image.length == 0) {
      log.info("Avatar is null or 0 bytes for user id: {}", userId);
      return;
    }

    try {
      imageStorageService.uploadAvatar(userId, type, source, new ByteArrayInputStream(image));
    } catch (final Exception e) {
      log.warn("Could not upload avatar for user id: {}", userId, e);
    }
  }

  @Scheduled(fixedDelay = 1000 * 60 * 60 * 24, initialDelay = 30000)
  public void run() throws ServiceException {
    transactionTemplate.execute(new TransactionCallbackWithoutResult() {
      protected void doInTransactionWithoutResult(@NotNull TransactionStatus status) {
        for (final User user : userRepository.findAll()) {
          try {
            if (!userPicRepository.existsById(user.getId())) {
              upload(
                      user.getId(),
                      MediaType.IMAGE_JPEG_VALUE,
                      AvatarSource.GRAVATAR,
                      getGravatar(user.getUserContact().getEmail()));

              Thread.sleep(GRAVATAR_FETCH_PAUSE_MS);
            }
          } catch (final Exception e) {
            log.warn("Could not find for user {}", user.getUsername(), e);
          }

        }
      }
    });
  }
}
