package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.model.AvatarSource;
import com.justjournal.services.ImageService;
import com.justjournal.services.ImageStorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    imageStorageService = mock(ImageStorageService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AvatarController(imageStorageService, new ImageService()))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private static byte[] png(final int width, final int height, final int type) throws Exception {
    final BufferedImage image = new BufferedImage(width, height, type);
    final Graphics2D g = image.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, width / 2, height);
    // noise, so the file is over the 500 byte minimum
    for (int x = 0; x < width; x += 3) {
      for (int y = 0; y < height; y += 3) image.setRGB(x, y, 0xff000000 | (x * 7919 + y * 104729));
    }
    g.dispose();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private MockHttpSession loggedIn() {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, USER_ID);
    return session;
  }

  /** Uploads the file and returns the image that was handed to storage. */
  private BufferedImage uploadAndCapture(final byte[] data) throws Exception {
    mockMvc
        .perform(
            multipart("/Avatar")
                .file(new MockMultipartFile("file", "a.png", MediaType.IMAGE_PNG_VALUE, data))
                .session(loggedIn()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/Avatar/" + USER_ID));

    final ArgumentCaptor<InputStream> stored = ArgumentCaptor.forClass(InputStream.class);
    verify(imageStorageService)
        .uploadAvatar(
            eq(USER_ID), eq(MediaType.IMAGE_JPEG_VALUE), eq(AvatarSource.UPLOAD), stored.capture());
    verify(imageStorageService).setShowAvatar(USER_ID, true);
    final byte[] bytes = stored.getValue().readAllBytes();
    // a real JPEG, not raw pixels
    assertEquals("ffd8ff", String.format("%02x%02x%02x", bytes[0], bytes[1], bytes[2]));
    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    assertNotNull(image);
    return image;
  }

  @Test
  void upload_largeImage_scaledAndStoredAsJpeg() throws Exception {
    final BufferedImage image = uploadAndCapture(png(400, 200, BufferedImage.TYPE_INT_RGB));

    assertEquals(100, image.getWidth());
    assertEquals(50, image.getHeight());
  }

  @Test
  void upload_smallImageWithAlpha_storedAsJpeg() throws Exception {
    final BufferedImage image = uploadAndCapture(png(60, 60, BufferedImage.TYPE_INT_ARGB));

    assertEquals(60, image.getWidth());
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

    verify(imageStorageService, never()).uploadAvatar(any(int.class), any(), any(), any());
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
