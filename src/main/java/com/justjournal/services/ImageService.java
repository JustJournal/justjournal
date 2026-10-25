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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;

/**
 * Image manipulation service
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class ImageService {
  private static final int AVATAR_MAX_WIDTH = 100;

  public BufferedImage resizeAvatar(final byte[] data) throws IOException {
    return scaleImageAsSquare(convertByteArrayToBufferedImage(data), AVATAR_MAX_WIDTH);
  }

  public byte[] convertBufferedImageToJpeg(final BufferedImage bufferedImage) throws IOException {
    BufferedImage rgb = bufferedImage;
    // JPEG has no alpha channel, and ImageIO writes nothing for an image that has one
    if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB) {
      rgb =
          new BufferedImage(
              bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
      final Graphics2D g = rgb.createGraphics();
      g.drawImage(bufferedImage, 0, 0, Color.WHITE, null);
      g.dispose();
    }

    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    if (!ImageIO.write(rgb, "jpg", baos)) {
      throw new IOException("No JPEG writer available");
    }
    return baos.toByteArray();
  }

  /**
   * @throws IllegalArgumentException if the data isn't an image format ImageIO can read
   */
  public BufferedImage convertByteArrayToBufferedImage(final byte[] data) throws IOException {
    final ByteArrayInputStream bis = new ByteArrayInputStream(data);
    final BufferedImage image = ImageIO.read(bis);
    if (image == null) {
      throw new IllegalArgumentException("Unsupported image format");
    }
    return image;
  }

  public BufferedImage scaleImageAsSquare(final BufferedImage bufferedImage, final int boundSize) {
    final int origWidth = bufferedImage.getWidth();
    final int origHeight = bufferedImage.getHeight();
    final double scale;

    if (origHeight > origWidth) scale = boundSize / (double) origHeight;
    else scale = boundSize / (double) origWidth;

    // Don't scale up small images.
    if (scale > 1.0) return bufferedImage;

    final int scaledWidth = (int) (scale * origWidth);
    final int scaledHeight = (int) (scale * origHeight);

    final Image scaledImage =
        bufferedImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);

    final BufferedImage scaledBI =
        new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);

    final Graphics2D g = scaledBI.createGraphics();

    g.drawImage(scaledImage, 0, 0, null);

    g.dispose();

    return scaledBI;
  }
}
