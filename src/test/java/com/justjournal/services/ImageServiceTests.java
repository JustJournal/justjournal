package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.BiFunction;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageServiceTests {

  private final ImageService imageService = new ImageService();

  /** 3 x 2 image with a different colour at every pixel. */
  private static BufferedImage grid() {
    final BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) image.setRGB(x, y, (x << 16) | (y << 8) | 0x80);
    return image;
  }

  /** Where each pixel of the displayed image comes from in the stored one, per EXIF. */
  private static BiFunction<Integer, Integer, int[]> source(final int orientation, final int w, final int h) {
    return switch (orientation) {
      case 2 -> (x, y) -> new int[] {w - 1 - x, y};
      case 3 -> (x, y) -> new int[] {w - 1 - x, h - 1 - y};
      case 4 -> (x, y) -> new int[] {x, h - 1 - y};
      case 5 -> (x, y) -> new int[] {y, x};
      case 6 -> (x, y) -> new int[] {y, h - 1 - x};
      case 7 -> (x, y) -> new int[] {w - 1 - y, h - 1 - x};
      case 8 -> (x, y) -> new int[] {w - 1 - y, x};
      default -> (x, y) -> new int[] {x, y};
    };
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
  void applyOrientation_matchesExif(final int orientation) {
    final BufferedImage stored = grid();

    final BufferedImage shown = ImageService.applyOrientation(stored, orientation);

    final boolean swap = orientation >= 5;
    assertEquals(swap ? 2 : 3, shown.getWidth());
    assertEquals(swap ? 3 : 2, shown.getHeight());
    final BiFunction<Integer, Integer, int[]> from = source(orientation, 3, 2);
    for (int x = 0; x < shown.getWidth(); x++) {
      for (int y = 0; y < shown.getHeight(); y++) {
        final int[] s = from.apply(x, y);
        assertEquals(
            stored.getRGB(s[0], s[1]),
            shown.getRGB(x, y),
            "orientation " + orientation + " pixel " + x + "," + y);
      }
    }
  }

  /** A JPEG with an EXIF block holding just an orientation tag. */
  static byte[] jpegWithOrientation(final BufferedImage image, final int orientation, final boolean bigEndian)
      throws Exception {
    final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", jpeg);
    final byte[] plain = jpeg.toByteArray();

    final ByteArrayOutputStream tiff = new ByteArrayOutputStream();
    final java.util.function.IntConsumer u16 =
        v -> {
          if (bigEndian) { tiff.write(v >> 8); tiff.write(v); } else { tiff.write(v); tiff.write(v >> 8); }
        };
    tiff.writeBytes(bigEndian ? new byte[] {'M', 'M'} : new byte[] {'I', 'I'});
    u16.accept(42);
    u16.accept(0); u16.accept(8); // offset of IFD0, written as two halves below
    final byte[] header = tiff.toByteArray();
    if (!bigEndian) { header[4] = 8; header[5] = 0; header[6] = 0; header[7] = 0; }
    tiff.reset();
    tiff.writeBytes(header);
    u16.accept(1); // one entry
    u16.accept(0x0112); u16.accept(3); // orientation, SHORT
    if (bigEndian) { tiff.writeBytes(new byte[] {0, 0, 0, 1}); } else { tiff.writeBytes(new byte[] {1, 0, 0, 0}); }
    u16.accept(orientation); u16.accept(0);
    tiff.writeBytes(new byte[] {0, 0, 0, 0}); // no next IFD

    final byte[] exif = tiff.toByteArray();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(0xff); out.write(0xd8);
    out.write(0xff); out.write(0xe1);
    final int length = 2 + 6 + exif.length;
    out.write(length >> 8); out.write(length);
    out.writeBytes("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    out.writeBytes(exif);
    out.write(plain, 2, plain.length - 2);
    return out.toByteArray();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 6, 8})
  void jpegOrientation_readsBothByteOrders(final int orientation) throws Exception {
    final BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
    assertEquals(orientation, ImageService.jpegOrientation(jpegWithOrientation(image, orientation, true)));
    assertEquals(orientation, ImageService.jpegOrientation(jpegWithOrientation(image, orientation, false)));
  }

  @Test
  void jpegOrientation_noExif_isOne() throws Exception {
    final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
    assertEquals(1, ImageService.jpegOrientation(jpeg.toByteArray()));
    assertEquals(1, ImageService.jpegOrientation(new byte[] {1, 2, 3}));
  }

  @Test
  void thumbnail_portraitPhoto_turnedUpright() throws Exception {
    // a phone held upright: stored 4000 x 3000 landscape, orientation 6 says turn it
    final BufferedImage stored = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);

    final BufferedImage thumb =
        ImageIO.read(
            new ByteArrayInputStream(
                imageService.thumbnail(jpegWithOrientation(stored, 6, true), 400)));

    assertEquals(300, thumb.getWidth());
    assertEquals(400, thumb.getHeight());
  }

  @Test
  void thumbnail_smallImage_notScaledUp() throws Exception {
    final ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB), "png", png);

    final BufferedImage thumb =
        ImageIO.read(new ByteArrayInputStream(imageService.thumbnail(png.toByteArray(), 400)));

    assertEquals(120, thumb.getWidth());
  }

  @Test
  void describe_readsFormatAndSize() throws Exception {
    final ByteArrayOutputStream gif = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "gif", gif);

    final ImageService.ImageInfo info = imageService.describe(gif.toByteArray());

    assertEquals("image/gif", info.mimeType());
    assertEquals("gif", info.extension());
    assertEquals(30, info.width());
    assertEquals(20, info.height());
  }

  @Test
  void describe_unsupportedFormat() throws Exception {
    final ByteArrayOutputStream bmp = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "bmp", bmp);

    assertThrows(IllegalArgumentException.class, () -> imageService.describe(bmp.toByteArray()));
    assertThrows(IllegalArgumentException.class, () -> imageService.describe(new byte[] {1, 2, 3}));
  }
}
