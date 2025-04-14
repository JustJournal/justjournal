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


import com.justjournal.exception.ThumbnailException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.imageio.ImageIO;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Create thumbnails of full images as PNG
 *
 * @author Lucas Holt
 */
@Slf4j
public class Thumbnail {
  private int thumbWidth; /* Suggested thumbnail max width */
  private int thumbHeight; /* Suggested thumbnail max height */

  /** Create Thumbnail with default width & height 100 */
  public Thumbnail() {
    this(100, 100);
  }

  public Thumbnail(final int width, final int height) {
    super();
    thumbWidth = width;
    thumbHeight = height;
  }

  protected double calcThumbRatio() {
    return (double) thumbWidth / (double) thumbHeight;
  }

  protected double calcImageRatio(@NotNull final Image image) {
    final int imageWidth = image.getWidth(null);
    final int imageHeight = image.getHeight(null);
    return (double) imageWidth / (double) imageHeight;
  }

  public int getThumbWidth() {
    return thumbWidth;
  }

  public void setThumbWidth(final int thumbWidth) {
    this.thumbWidth = thumbWidth;
  }

  public int getThumbHeight() {
    return thumbHeight;
  }

  public void setThumbHeight(final int thumbHeight) {
    this.thumbHeight = thumbHeight;
  }

  public void create(@NotNull final String inputfile, @NotNull final String outputfile) {
    try {
      File f = new File(inputfile);
      BufferedImage bi = ImageIO.read(f);

      int height = thumbHeight;
      int width = thumbWidth;
      final double imageRatio = calcImageRatio(bi);

      if (calcThumbRatio() < imageRatio) {
        height = (int) (thumbWidth / imageRatio);
      } else {
        width = (int) (thumbHeight * imageRatio);
      }
      // draw original image to thumbnail image object and
      // scale it to the new size on-the-fly
      BufferedImage thumbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics2D = thumbImage.createGraphics();
      graphics2D.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics2D.drawImage(bi, 0, 0, width, height, null);

      // save thumbnail image to OUTFILE
      final File outfile = new File(outputfile);
      ImageIO.write(thumbImage, "png", outfile);
    } catch (final FileNotFoundException fe) {
      log.error("create(): File not found", fe);
      throw new ThumbnailException();
    } catch (final IOException ioe) {
      log.error("create(): IO error", ioe);
      throw new ThumbnailException();
    }
  }
}
