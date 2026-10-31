package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.AvatarSource;
import com.justjournal.model.ModerationStatus;
import com.justjournal.services.ImageModerationService;
import com.justjournal.services.ImageService;
import com.justjournal.services.ImageStorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AvatarControllerTests {

  private static final int USER_ID = 42;

  private ImageStorageService imageStorageService;
  private ImageModerationService moderation;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    imageStorageService = mock(ImageStorageService.class);
    moderation = mock(ImageModerationService.class);
    verdict(ModerationStatus.APPROVED);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AvatarController(imageStorageService, new ImageService(), moderation))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private void verdict(final ModerationStatus status) {
    when(moderation.moderate(any(), any()))
        .thenReturn(new ImageModerationService.Result(status, status.name().toLowerCase(), "test"));
  }

  private static BufferedImage picture(final int width, final int height, final int type) {
    final BufferedImage image = new BufferedImage(width, height, type);
    final Graphics2D g = image.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, width / 2, height);
    // noise, so the file is over the 500 byte minimum
    for (int x = 0; x < width; x += 3) {
      for (int y = 0; y < height; y += 3) image.setRGB(x, y, 0xff000000 | (x * 7919 + y * 104729));
    }
    g.dispose();
    return image;
  }

  private static byte[] encode(final BufferedImage image, final String format) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  private static byte[] png(final int width, final int height, final int type) throws Exception {
    return encode(picture(width, height, type), "png");
  }

  private MockHttpSession loggedIn() {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, USER_ID);
    return session;
  }

  /** Uploads the file and returns what was handed to storage, checking its mime type. */
  private byte[] uploadAndCapture(
      final byte[] data, final String uploadType, final String storedType) throws Exception {
    mockMvc
        .perform(
            multipart("/Avatar")
                .file(new MockMultipartFile("file", "a", uploadType, data))
                .session(loggedIn()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/Avatar/" + USER_ID));

    final ArgumentCaptor<InputStream> stored = ArgumentCaptor.forClass(InputStream.class);
    verify(imageStorageService)
        .uploadAvatar(
            eq(USER_ID),
            eq(storedType),
            eq(AvatarSource.UPLOAD),
            eq(ModerationStatus.APPROVED),
            stored.capture());
    verify(imageStorageService).setShowAvatar(USER_ID, true);
    return stored.getValue().readAllBytes();
  }

  private static BufferedImage decode(final byte[] data) throws Exception {
    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
    assertNotNull(image, "stored data should be a readable image");
    return image;
  }

  @Test
  void upload_largePng_scaledAndKeptAsPng() throws Exception {
    final byte[] stored =
        uploadAndCapture(
            png(400, 200, BufferedImage.TYPE_INT_RGB),
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_PNG_VALUE);

    final BufferedImage image = decode(stored);
    assertEquals(100, image.getWidth());
    assertEquals(50, image.getHeight());
  }

  @Test
  void upload_pngWithTransparency_keepsTransparency() throws Exception {
    final BufferedImage source = picture(300, 300, BufferedImage.TYPE_INT_ARGB);
    source.setRGB(299, 299, 0x00000000);

    final BufferedImage image =
        decode(
            uploadAndCapture(
                encode(source, "png"), MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_PNG_VALUE));

    assertEquals(100, image.getWidth());
    assertTrue(image.getColorModel().hasAlpha());
  }

  @Test
  void upload_jpeg_scaledAndStoredAsJpeg() throws Exception {
    final byte[] stored =
        uploadAndCapture(
            encode(picture(400, 400, BufferedImage.TYPE_INT_RGB), "jpg"),
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_JPEG_VALUE);

    assertEquals("ffd8ff", String.format("%02x%02x%02x", stored[0], stored[1], stored[2]));
    assertEquals(100, decode(stored).getWidth());
  }

  @Test
  void upload_bmp_storedAsJpeg() throws Exception {
    final byte[] stored =
        uploadAndCapture(
            encode(picture(150, 150, BufferedImage.TYPE_INT_RGB), "bmp"),
            "image/bmp",
            MediaType.IMAGE_JPEG_VALUE);

    assertEquals(100, decode(stored).getWidth());
  }

  @Test
  void upload_largeGif_scaledAndKeptAsGif() throws Exception {
    final byte[] stored =
        uploadAndCapture(
            encode(picture(200, 200, BufferedImage.TYPE_INT_RGB), "gif"),
            MediaType.IMAGE_GIF_VALUE,
            MediaType.IMAGE_GIF_VALUE);

    assertEquals("GIF", new String(stored, 0, 3, StandardCharsets.US_ASCII));
    assertEquals(100, decode(stored).getWidth());
  }

  @Test
  void upload_smallGif_storedUnchanged() throws Exception {
    // an animated gif would lose its animation if it were re-encoded
    final byte[] gif = encode(picture(80, 80, BufferedImage.TYPE_INT_RGB), "gif");

    final byte[] stored =
        uploadAndCapture(gif, MediaType.IMAGE_GIF_VALUE, MediaType.IMAGE_GIF_VALUE);

    assertArrayEquals(gif, stored);
  }

  @Test
  void upload_typeComesFromTheContent() throws Exception {
    // a png sent as image/jpeg is still a png
    uploadAndCapture(
        png(150, 150, BufferedImage.TYPE_INT_RGB),
        MediaType.IMAGE_JPEG_VALUE,
        MediaType.IMAGE_PNG_VALUE);
  }

  @Test
  void upload_over100k_badRequest() throws Exception {
    final byte[] big = encode(picture(400, 400, BufferedImage.TYPE_INT_RGB), "bmp");
    assertTrue(big.length > AvatarController.MAX_UPLOAD_BYTES);

    mockMvc
        .perform(
            multipart("/Avatar")
                .file(new MockMultipartFile("file", "a.bmp", "image/bmp", big))
                .session(loggedIn()))
        .andExpect(status().isBadRequest());

    verify(imageStorageService, never()).uploadAvatar(anyInt(), any(), any(), any(), any());
  }

  @Test
  void upload_storageFails_avatarNotTurnedOn() throws Exception {
    doThrow(new ServiceException("down"))
        .when(imageStorageService)
        .uploadAvatar(anyInt(), any(), any(), any(), any());

    mockMvc
        .perform(
            multipart("/Avatar")
                .file(
                    new MockMultipartFile(
                        "file",
                        "a.png",
                        MediaType.IMAGE_PNG_VALUE,
                        png(150, 150, BufferedImage.TYPE_INT_RGB)))
                .session(loggedIn()))
        .andExpect(status().isInternalServerError());

    verify(imageStorageService, never()).setShowAvatar(anyInt(), anyBoolean());
  }

  @Test
  void upload_nudity_rejectedAndNotStored() throws Exception {
    verdict(ModerationStatus.REJECTED);

    mockMvc
        .perform(
            multipart("/Avatar")
                .file(
                    new MockMultipartFile(
                        "file",
                        "a.png",
                        MediaType.IMAGE_PNG_VALUE,
                        png(150, 150, BufferedImage.TYPE_INT_RGB)))
                .session(loggedIn()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(AvatarController.REJECTED_MESSAGE));

    verify(imageStorageService, never()).uploadAvatar(anyInt(), any(), any(), any(), any());
    verify(imageStorageService, never()).setShowAvatar(anyInt(), anyBoolean());
  }

  @Test
  void upload_unsure_keptForReview() throws Exception {
    verdict(ModerationStatus.REVIEW);

    mockMvc
        .perform(
            multipart("/Avatar")
                .file(
                    new MockMultipartFile(
                        "file",
                        "a.png",
                        MediaType.IMAGE_PNG_VALUE,
                        png(150, 150, BufferedImage.TYPE_INT_RGB)))
                .session(loggedIn()))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Location", "/Avatar/" + USER_ID))
        .andExpect(jsonPath("$.message").value(AvatarController.REVIEW_MESSAGE));

    verify(imageStorageService)
        .uploadAvatar(
            eq(USER_ID),
            eq(MediaType.IMAGE_PNG_VALUE),
            eq(AvatarSource.UPLOAD),
            eq(ModerationStatus.REVIEW),
            any());
    verify(imageStorageService).setShowAvatar(USER_ID, true);
  }

  @Test
  void upload_filterSeesTheUpload() throws Exception {
    final byte[] data = png(150, 150, BufferedImage.TYPE_INT_RGB);

    mockMvc
        .perform(
            multipart("/Avatar")
                .file(new MockMultipartFile("file", "a.png", MediaType.IMAGE_PNG_VALUE, data))
                .session(loggedIn()))
        .andExpect(status().isCreated());

    verify(moderation).moderate(eq(data), any());
  }

  private void storedAvatar(final boolean shown, final String mimeType) throws Exception {
    storedAvatar(shown, true, mimeType);
  }

  private void storedAvatar(final boolean shown, final boolean approved, final String mimeType)
      throws Exception {
    when(imageStorageService.isShowAvatar(USER_ID)).thenReturn(shown);
    when(imageStorageService.isAvatarApproved(USER_ID)).thenReturn(approved);
    when(imageStorageService.downloadAvatar(USER_ID))
        .thenReturn(
            new ImageStorageService.StoredAvatar(
                new ByteArrayInputStream(new byte[] {1, 2, 3}), mimeType));
  }

  @Test
  void get_shown_servedWithStoredTypeAndCacheHeaders() throws Exception {
    storedAvatar(true, MediaType.IMAGE_PNG_VALUE);

    mockMvc
        .perform(get("/Avatar/" + USER_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(new byte[] {1, 2, 3}))
        .andExpect(header().string("Cache-Control", "max-age=3600, public"))
        .andExpect(header().exists("Expires"));
  }

  @Test
  void get_hidden_forbidden() throws Exception {
    storedAvatar(false, MediaType.IMAGE_JPEG_VALUE);

    mockMvc
        .perform(get("/Avatar/" + USER_ID))
        .andExpect(status().isForbidden())
        .andExpect(header().string("Cache-Control", "no-store"));

    verify(imageStorageService, never()).downloadAvatar(anyInt());
  }

  @Test
  void get_hidden_ownerCanStillSeeIt() throws Exception {
    storedAvatar(false, MediaType.IMAGE_GIF_VALUE);

    mockMvc
        .perform(get("/Avatar/" + USER_ID).session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_GIF))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().doesNotExist("Expires"));
  }

  @Test
  void get_awaitingReview_forbidden() throws Exception {
    storedAvatar(true, false, MediaType.IMAGE_JPEG_VALUE);

    mockMvc
        .perform(get("/Avatar/" + USER_ID))
        .andExpect(status().isForbidden())
        .andExpect(header().string("Cache-Control", "no-store"));

    verify(imageStorageService, never()).downloadAvatar(anyInt());
  }

  @Test
  void get_awaitingReview_ownerCanSeeIt() throws Exception {
    storedAvatar(true, false, MediaType.IMAGE_JPEG_VALUE);

    mockMvc
        .perform(get("/Avatar/" + USER_ID).session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void get_noAvatar_notFound() throws Exception {
    when(imageStorageService.isShowAvatar(USER_ID)).thenReturn(true);
    when(imageStorageService.isAvatarApproved(USER_ID)).thenReturn(true);
    when(imageStorageService.downloadAvatar(USER_ID)).thenThrow(new NotFoundException("none"));

    mockMvc.perform(get("/Avatar/" + USER_ID)).andExpect(status().isNotFound());
  }

  @Test
  void get_noSuchUser_notFound() throws Exception {
    when(imageStorageService.isShowAvatar(USER_ID)).thenThrow(new IllegalArgumentException());

    mockMvc.perform(get("/Avatar/" + USER_ID)).andExpect(status().isNotFound());
  }

  @Test
  void upload_notAnImage_badRequest() throws Exception {
    final byte[] junk = new byte[1000];
    Arrays.fill(junk, (byte) 'x');

    mockMvc
        .perform(
            multipart("/Avatar")
                .file(new MockMultipartFile("file", "a.png", MediaType.IMAGE_PNG_VALUE, junk))
                .session(loggedIn()))
        .andExpect(status().isBadRequest());

    verify(imageStorageService, never()).uploadAvatar(anyInt(), any(), any(), any(), any());
    verify(imageStorageService, never()).setShowAvatar(anyInt(), anyBoolean());
  }

  @Test
  void preference_returnsAvailabilityAndSetting() throws Exception {
    when(imageStorageService.hasAvatarImage(USER_ID)).thenReturn(true);
    when(imageStorageService.isShowAvatar(USER_ID)).thenReturn(false);

    mockMvc
        .perform(get("/Avatar/preference").session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.show").value(false));
  }

  @Test
  void preference_turnOn_withImage() throws Exception {
    when(imageStorageService.hasAvatarImage(USER_ID)).thenReturn(true);

    mockMvc
        .perform(
            put("/Avatar/preference")
                .session(loggedIn())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"show\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.show").value(true));

    verify(imageStorageService).setShowAvatar(USER_ID, true);
  }

  @Test
  void preference_turnOn_withoutImage_badRequest() throws Exception {
    when(imageStorageService.hasAvatarImage(USER_ID)).thenReturn(false);

    mockMvc
        .perform(
            put("/Avatar/preference")
                .session(loggedIn())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"show\": true}"))
        .andExpect(status().isBadRequest());

    verify(imageStorageService, never()).setShowAvatar(anyInt(), anyBoolean());
  }

  @Test
  void preference_turnOff_withoutImage_allowed() throws Exception {
    when(imageStorageService.hasAvatarImage(USER_ID)).thenReturn(false);

    mockMvc
        .perform(
            put("/Avatar/preference")
                .session(loggedIn())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"show\": false}"))
        .andExpect(status().isOk());

    verify(imageStorageService).setShowAvatar(USER_ID, false);
  }

  @Test
  void preference_notLoggedIn_forbidden() throws Exception {
    mockMvc.perform(get("/Avatar/preference")).andExpect(status().isForbidden());
  }
}
