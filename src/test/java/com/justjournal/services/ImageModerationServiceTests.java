package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.model.ModerationStatus;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImageModerationServiceTests {

  private OllamaClient ollama;
  private ImageModerationService service;

  @BeforeEach
  void setUp() {
    ollama = mock(OllamaClient.class);
    service = new ImageModerationService(ollama, new ImageService());
    service.setEnabled(true);
    service.setModel("vision");
    service.setOnSuggestive(ModerationStatus.REVIEW);
    service.setOnError(ModerationStatus.REVIEW);
  }

  private static byte[] png(final int width, final int height) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
    return out.toByteArray();
  }

  private void answers(final String category) throws IOException {
    final JsonNode answer =
        new ObjectMapper().readTree("{\"category\":\"" + category + "\",\"reason\":\"because\"}");
    when(ollama.chat(eq("vision"), anyString(), any(), any())).thenReturn(answer);
  }

  @Test
  void safe_approved() throws Exception {
    answers("safe");

    final ImageModerationService.Result result = service.moderate(png(50, 50));

    assertEquals(ModerationStatus.APPROVED, result.status());
    assertEquals("safe", result.category());
    assertEquals("because", result.reason());
  }

  @Test
  void nudityAndSexual_rejected() throws Exception {
    answers("nudity");
    assertTrue(service.moderate(png(50, 50)).rejected());

    answers("Sexual");
    assertTrue(service.moderate(png(50, 50)).rejected());
  }

  @Test
  void suggestive_asConfigured() throws Exception {
    answers("suggestive");
    assertEquals(ModerationStatus.REVIEW, service.moderate(png(50, 50)).status());

    service.setOnSuggestive(ModerationStatus.APPROVED);
    assertEquals(ModerationStatus.APPROVED, service.moderate(png(50, 50)).status());
  }

  @Test
  void modelDown_fallsBackAsConfigured() throws Exception {
    when(ollama.chat(anyString(), anyString(), any(), any())).thenThrow(new IOException("down"));

    assertEquals(ModerationStatus.REVIEW, service.moderate(png(50, 50)).status());

    service.setOnError(ModerationStatus.REJECTED);
    assertEquals(ModerationStatus.REJECTED, service.moderate(png(50, 50)).status());
  }

  @Test
  void nonsenseAnswer_treatedAsError() throws Exception {
    answers("purple");
    assertEquals(ModerationStatus.REVIEW, service.moderate(png(50, 50)).status());
  }

  @Test
  void notAnImage_treatedAsError() throws Exception {
    assertEquals(ModerationStatus.REVIEW, service.moderate(new byte[100]).status());
    verify(ollama, never()).chat(any(), any(), any(), any());
  }

  @Test
  void off_approvedWithoutAsking() throws Exception {
    service.setEnabled(false);

    assertEquals(ModerationStatus.APPROVED, service.moderate(new byte[100]).status());
    verify(ollama, never()).chat(any(), any(), any(), any());
  }

  @Test
  void modelGetsASmallJpeg() throws Exception {
    answers("safe");

    service.moderate(png(2000, 1000));

    final ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
    final ArgumentCaptor<JsonNode> format = ArgumentCaptor.forClass(JsonNode.class);
    verify(ollama).chat(eq("vision"), eq(ImageModerationService.PROMPT), sent.capture(), format.capture());
    final BufferedImage image = ImageIO.read(new ByteArrayInputStream(sent.getValue()));
    assertEquals(ImageModerationService.IMAGE_SIZE, image.getWidth());
    assertEquals(ImageModerationService.IMAGE_SIZE / 2, image.getHeight());
    assertEquals((byte) 0xff, sent.getValue()[0], "JPEG");
    assertEquals("object", format.getValue().path("type").asText());
    assertEquals(4, format.getValue().path("properties").path("category").path("enum").size());
  }
}
