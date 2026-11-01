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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * Image manipulation service
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class ImageService {
  private static final int AVATAR_MAX_WIDTH = 100;

  /** An encoded image and its mime type. */
  public record EncodedImage(byte[] data, String mimeType) {}

  public BufferedImage resizeAvatar(final byte[] data) throws IOException {
    return scaleImageAsSquare(convertByteArrayToBufferedImage(data), AVATAR_MAX_WIDTH);
  }

  /**
   * Scale an uploaded avatar down to fit {@value #AVATAR_MAX_WIDTH} pixels. PNG and GIF stay PNG
   * and GIF, keeping transparency; anything else becomes a JPEG. Images are re-encoded, which drops
   * metadata such as the location a photo was taken, except a GIF that is already small enough,
   * which is kept as it is so an animated one stays animated.
   *
   * @throws IllegalArgumentException if the data isn't an image format ImageIO can read
   */
  public EncodedImage encodeAvatar(final byte[] data) throws IOException {
    final String format = imageFormat(data);
    final BufferedImage original = convertByteArrayToBufferedImage(data);
    final BufferedImage scaled = scaleImageAsSquare(original, AVATAR_MAX_WIDTH);

    switch (format) {
      case "gif":
        if (scaled == original) return new EncodedImage(data, MediaType.IMAGE_GIF_VALUE);
        return new EncodedImage(write(scaled, "gif"), MediaType.IMAGE_GIF_VALUE);
      case "png":
        return new EncodedImage(write(scaled, "png"), MediaType.IMAGE_PNG_VALUE);
      default:
        return new EncodedImage(convertBufferedImageToJpeg(scaled), MediaType.IMAGE_JPEG_VALUE);
    }
  }

  /** The format ImageIO reads the data as, in lower case, or an empty string if none. */
  private static String imageFormat(final byte[] data) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      return readers.hasNext() ? readers.next().getFormatName().toLowerCase(Locale.ROOT) : "";
    }
  }

  private static byte[] write(final BufferedImage image, final String format) throws IOException {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    if (!ImageIO.write(image, format, baos)) {
      throw new IOException("No " + format + " writer available");
    }
    return baos.toByteArray();
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
        new BufferedImage(
            scaledWidth,
            scaledHeight,
            bufferedImage.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB);

    final Graphics2D g = scaledBI.createGraphics();

    g.drawImage(scaledImage, 0, 0, null);

    g.dispose();

    return scaledBI;
  }

  /** Largest image accepted, in pixels; a 48 megapixel phone photo fits. */
  public static final long MAX_PIXELS = 100_000_000L;

  /** Format and size of an image, read from its header without decoding it. */
  public record ImageInfo(String mimeType, String extension, int width, int height) {}

  /**
   * Describe a JPEG, PNG or GIF image.
   *
   * @throws IllegalArgumentException if the data isn't one of those, or is larger than {@link
   *     #MAX_PIXELS}
   */
  public ImageInfo describe(final byte[] data) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported image format");
      final ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        final String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        final ImageInfo info =
            switch (format) {
              case "jpeg", "jpg" ->
                  new ImageInfo(MediaType.IMAGE_JPEG_VALUE, "jpg", reader.getWidth(0), reader.getHeight(0));
              case "png" ->
                  new ImageInfo(MediaType.IMAGE_PNG_VALUE, "png", reader.getWidth(0), reader.getHeight(0));
              case "gif" ->
                  new ImageInfo(MediaType.IMAGE_GIF_VALUE, "gif", reader.getWidth(0), reader.getHeight(0));
              default -> throw new IllegalArgumentException("Unsupported image format " + format);
            };
        if ((long) info.width() * info.height() > MAX_PIXELS) {
          throw new IllegalArgumentException("Image is too large");
        }
        return info;
      } finally {
        reader.dispose();
      }
    }
  }

  /**
   * How many frames the image has: more than one for an animated GIF or PNG. ImageIO's PNG reader
   * only knows the first frame of an animated PNG, so those are counted from the file itself. A JPEG
   * is always one frame: phone cameras append a second picture, such as an HDR gain map, which
   * ImageIO would count as another image although it is never shown.
   *
   * @throws IllegalArgumentException if the data isn't an image format ImageIO can read
   */
  public int frameCount(final byte[] data) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported image format");
      final ImageReader reader = readers.next();
      try {
        if ("jpeg".equalsIgnoreCase(reader.getFormatName())) return 1;
        // counting frames searches the stream, which a forward-only input doesn't allow
        reader.setInput(in, false, true);
        final int frames = reader.getNumImages(true);
        if ("png".equalsIgnoreCase(reader.getFormatName())) {
          return Math.max(frames, apngFrames(data));
        }
        return frames;
      } finally {
        reader.dispose();
      }
    }
  }

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  /**
   * The frame count an animated PNG declares in its acTL chunk, but at least 2 whenever that chunk
   * is there, since a crafted file can declare fewer frames than it carries; 0 if the PNG has no
   * acTL chunk. Walks the chunk headers only; stops at the first image data, which acTL must
   * precede.
   */
  static int apngFrames(final byte[] data) {
    if (data.length < PNG_SIGNATURE.length) return 0;
    for (int i = 0; i < PNG_SIGNATURE.length; i++) {
      if (data[i] != PNG_SIGNATURE[i]) return 0;
    }
    final ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
    buf.position(PNG_SIGNATURE.length);
    while (buf.remaining() >= 12) {
      final long length = buf.getInt() & 0xFFFFFFFFL;
      final int type = buf.getInt();
      if (type == 0x49444154) return 0; // IDAT: image data starts, no acTL before it
      if (type == 0x6163544C) { // acTL
        return length >= 4 && buf.remaining() >= 4 ? Math.max(buf.getInt(), 2) : 2;
      }
      if (length + 4 > buf.remaining()) return 0; // truncated chunk
      buf.position(buf.position() + (int) length + 4); // data and CRC
    }
    return 0;
  }

  /**
   * A JPEG thumbnail that fits in a square of {@code boundSize} pixels, turned the right way up
   * for photos whose camera recorded the orientation instead of rotating the pixels. Large images
   * are read at a reduced resolution, so a phone photo isn't decoded at full size.
   *
   * @throws IllegalArgumentException if the data isn't an image format ImageIO can read
   */
  public byte[] thumbnail(final byte[] data, final int boundSize) throws IOException {
    final BufferedImage image;
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported image format");
      final ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        final int longest = Math.max(reader.getWidth(0), reader.getHeight(0));
        final ImageReadParam param = reader.getDefaultReadParam();
        // keep at least twice the thumbnail size, so scaling down still looks smooth
        final int step = Math.max(1, longest / (boundSize * 2));
        param.setSourceSubsampling(step, step, 0, 0);
        image = reader.read(0, param);
      } finally {
        reader.dispose();
      }
    }

    return convertBufferedImageToJpeg(
        applyOrientation(scaleImageAsSquare(image, boundSize), jpegOrientation(data)));
  }

  /**
   * The EXIF orientation of a JPEG (1 to 8), or 1 if it has none. Phone cameras usually save the
   * pixels as the sensor saw them and record how to turn them here.
   */
  static int jpegOrientation(final byte[] data) {
    if (data.length < 4 || (data[0] & 0xff) != 0xff || (data[1] & 0xff) != 0xd8) return 1;

    int pos = 2;
    while (pos + 4 <= data.length && (data[pos] & 0xff) == 0xff) {
      final int marker = data[pos + 1] & 0xff;
      if (marker == 0xd9 || marker == 0xda) break; // end of image, start of pixel data
      final int length = u16(data, pos + 2, true);
      final int start = pos + 4;
      if (marker == 0xe1
          && start + 14 <= data.length
          && new String(data, start, 6, java.nio.charset.StandardCharsets.ISO_8859_1)
              .equals("Exif\0\0")) {
        return exifOrientation(data, start + 6, Math.min(data.length, pos + 2 + length));
      }
      pos += 2 + length;
    }
    return 1;
  }

  private static int exifOrientation(final byte[] data, final int tiff, final int end) {
    final boolean bigEndian;
    if (data[tiff] == 'M' && data[tiff + 1] == 'M') bigEndian = true;
    else if (data[tiff] == 'I' && data[tiff + 1] == 'I') bigEndian = false;
    else return 1;

    final long ifd = tiff + u32(data, tiff + 4, bigEndian);
    if (ifd + 2 > end) return 1;
    final int entries = u16(data, (int) ifd, bigEndian);
    for (int i = 0; i < entries; i++) {
      final int entry = (int) ifd + 2 + i * 12;
      if (entry + 12 > end) return 1;
      if (u16(data, entry, bigEndian) == 0x0112) {
        final int orientation = u16(data, entry + 8, bigEndian);
        return orientation >= 1 && orientation <= 8 ? orientation : 1;
      }
    }
    return 1;
  }

  private static int u16(final byte[] data, final int pos, final boolean bigEndian) {
    final int a = data[pos] & 0xff;
    final int b = data[pos + 1] & 0xff;
    return bigEndian ? (a << 8) | b : (b << 8) | a;
  }

  private static long u32(final byte[] data, final int pos, final boolean bigEndian) {
    return bigEndian
        ? ((long) u16(data, pos, true) << 16) | u16(data, pos + 2, true)
        : ((long) u16(data, pos + 2, false) << 16) | u16(data, pos, false);
  }

  /** Turn or flip an image as an EXIF orientation says. */
  static BufferedImage applyOrientation(final BufferedImage image, final int orientation) {
    if (orientation <= 1 || orientation > 8) return image;

    final int w = image.getWidth();
    final int h = image.getHeight();
    final boolean swap = orientation >= 5;
    final AffineTransform t = new AffineTransform();
    switch (orientation) {
      case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
      case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
      case 4 -> { t.translate(0, h); t.scale(1, -1); }
      case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
      case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
      case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
      case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
      default -> { }
    }

    final BufferedImage turned =
        new BufferedImage(
            swap ? h : w,
            swap ? w : h,
            image.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB);
    final Graphics2D g = turned.createGraphics();
    g.drawImage(image, t, null);
    g.dispose();
    return turned;
  }
}
