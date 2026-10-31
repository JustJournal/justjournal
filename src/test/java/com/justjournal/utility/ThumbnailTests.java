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
package com.justjournal.utility;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** @author Lucas Holt */
class ThumbnailTests {

  private final Thumbnail thumbnail = new Thumbnail();

  @Test
  void testCalcThumbRatio() {
    Double result = thumbnail.calcThumbRatio();
    Assertions.assertEquals(Double.valueOf("1.0"), result);
  }

  @TempDir java.nio.file.Path dir;

  private static java.awt.image.BufferedImage image(final int width, final int height) {
    return new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
  }

  private String png(final String name, final int width, final int height) throws java.io.IOException {
    final java.io.File file = dir.resolve(name).toFile();
    javax.imageio.ImageIO.write(image(width, height), "png", file);
    return file.getPath();
  }

  private java.awt.image.BufferedImage thumbOf(final int width, final int height) throws java.io.IOException {
    final String out = dir.resolve("thumb-" + width + "x" + height + ".png").toString();
    thumbnail.create(png("in-" + width + "x" + height + ".png", width, height), out);
    return javax.imageio.ImageIO.read(new java.io.File(out));
  }

  @Test
  void sizeAndRatiosCanBeChanged() {
    final Thumbnail custom = new Thumbnail(200, 50);
    Assertions.assertEquals(4.0, custom.calcThumbRatio());

    custom.setThumbWidth(300);
    custom.setThumbHeight(100);

    Assertions.assertEquals(300, custom.getThumbWidth());
    Assertions.assertEquals(100, custom.getThumbHeight());
    Assertions.assertEquals(3.0, custom.calcThumbRatio());
    Assertions.assertEquals(2.0, custom.calcImageRatio(image(400, 200)));
  }

  @Test
  void createFitsWideImagesToTheWidth() throws java.io.IOException {
    final java.awt.image.BufferedImage thumb = thumbOf(400, 200);

    Assertions.assertEquals(100, thumb.getWidth());
    Assertions.assertEquals(50, thumb.getHeight());
  }

  @Test
  void createFitsTallImagesToTheHeight() throws java.io.IOException {
    final java.awt.image.BufferedImage thumb = thumbOf(200, 400);

    Assertions.assertEquals(50, thumb.getWidth());
    Assertions.assertEquals(100, thumb.getHeight());
  }

  @Test
  void createKeepsSquareImagesSquare() throws java.io.IOException {
    final java.awt.image.BufferedImage thumb = thumbOf(300, 300);

    Assertions.assertEquals(100, thumb.getWidth());
    Assertions.assertEquals(100, thumb.getHeight());
  }

  @Test
  void createFailsForAMissingFile() {
    final String missing = dir.resolve("missing.png").toString();
    final String out = dir.resolve("out.png").toString();

    Assertions.assertThrows(
        com.justjournal.exception.ThumbnailException.class, () -> thumbnail.create(missing, out));
  }
}
