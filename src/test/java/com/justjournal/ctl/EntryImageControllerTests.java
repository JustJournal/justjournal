package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.EntryImage;
import com.justjournal.model.ModerationStatus;
import com.justjournal.services.AlbumImageService;
import com.justjournal.services.EntryImageService;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EntryImageControllerTests {

  private static final int USER_ID = 42;

  private EntryImageService entryImageService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    entryImageService = mock(EntryImageService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new EntryImageController(entryImageService))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private static MockHttpSession loggedIn() {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, USER_ID);
    return session;
  }

  private static MockMultipartFile file(final int size) {
    return new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[size]);
  }

  private static EntryImageService.Picture picture(final String mimeType, final boolean cacheable) {
    return new EntryImageService.Picture(
        new ByteArrayInputStream(new byte[] {1, 2, 3}), mimeType, cacheable);
  }

  private static EntryImage saved(final ModerationStatus status) {
    final EntryImage image = new EntryImage();
    image.setId(99);
    image.setModerationStatus(status);
    return image;
  }

  @Test
  void get_publicPost_cachedPublicly() throws Exception {
    when(entryImageService.original(7, 0)).thenReturn(picture(MediaType.IMAGE_PNG_VALUE, true));

    mockMvc
        .perform(get("/EntryImage/7"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(new byte[] {1, 2, 3}))
        .andExpect(header().string("Cache-Control", "max-age=3600, public"));
  }

  @Test
  void get_notPublic_noStore() throws Exception {
    when(entryImageService.original(7, USER_ID))
        .thenReturn(picture(MediaType.IMAGE_JPEG_VALUE, false));

    mockMvc
        .perform(get("/EntryImage/7").session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(entryImageService).original(7, USER_ID);
  }

  @Test
  void display_servedAsJpeg() throws Exception {
    when(entryImageService.display(7, 0)).thenReturn(picture(MediaType.IMAGE_JPEG_VALUE, true));

    mockMvc
        .perform(get("/EntryImage/7/display"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_JPEG))
        .andExpect(header().string("Cache-Control", "max-age=3600, public"));
  }

  @Test
  void display_notPublic_noStore() throws Exception {
    when(entryImageService.display(7, USER_ID))
        .thenReturn(picture(MediaType.IMAGE_JPEG_VALUE, false));

    mockMvc
        .perform(get("/EntryImage/7/display").session(loggedIn()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void get_hidden_notFound() throws Exception {
    when(entryImageService.original(7, 0)).thenThrow(new NotFoundException("No picture 7"));
    when(entryImageService.display(7, 0)).thenThrow(new NotFoundException("No picture 7"));

    mockMvc.perform(get("/EntryImage/7")).andExpect(status().isNotFound());
    mockMvc.perform(get("/EntryImage/7/display")).andExpect(status().isNotFound());
    mockMvc.perform(get("/EntryImage/x")).andExpect(status().isNotFound());
  }

  @Test
  void upload_created() throws Exception {
    when(entryImageService.upload(eq(USER_ID), any(), eq("A beach")))
        .thenReturn(saved(ModerationStatus.APPROVED));

    mockMvc
        .perform(
            multipart("/EntryImage").file(file(5000)).param("alt", "A beach").session(loggedIn()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/EntryImage/99"))
        .andExpect(jsonPath("$.id").value(99))
        .andExpect(jsonPath("$.url").value("/EntryImage/99/display"))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  void upload_withoutAlt_created() throws Exception {
    when(entryImageService.upload(eq(USER_ID), any(), isNull()))
        .thenReturn(saved(ModerationStatus.APPROVED));

    mockMvc
        .perform(multipart("/EntryImage").file(file(5000)).session(loggedIn()))
        .andExpect(status().isCreated());
  }

  @Test
  void upload_heldForReview_acceptedWithMessage() throws Exception {
    when(entryImageService.upload(eq(USER_ID), any(), any()))
        .thenReturn(saved(ModerationStatus.REVIEW));

    mockMvc
        .perform(multipart("/EntryImage").file(file(5000)).session(loggedIn()))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Location", "/EntryImage/99"))
        .andExpect(jsonPath("$.id").value(99))
        .andExpect(jsonPath("$.url").value("/EntryImage/99/display"))
        .andExpect(jsonPath("$.message").value(EntryImageController.REVIEW_MESSAGE));
  }

  @Test
  void upload_rejectedByFilter_badRequestWithReason() throws Exception {
    when(entryImageService.upload(eq(USER_ID), any(), any()))
        .thenThrow(new BadRequestException(AlbumImageService.REJECTED_MESSAGE));

    mockMvc
        .perform(multipart("/EntryImage").file(file(5000)).session(loggedIn()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(AlbumImageService.REJECTED_MESSAGE));
  }

  @Test
  void upload_tooManyWaiting_badRequestWithReason() throws Exception {
    when(entryImageService.upload(eq(USER_ID), any(), any()))
        .thenThrow(new BadRequestException(EntryImageService.TOO_MANY_PENDING_MESSAGE));

    mockMvc
        .perform(multipart("/EntryImage").file(file(5000)).session(loggedIn()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(EntryImageService.TOO_MANY_PENDING_MESSAGE));
  }

  @Test
  void upload_tooSmall_badRequest() throws Exception {
    mockMvc
        .perform(multipart("/EntryImage").file(file(10)).session(loggedIn()))
        .andExpect(status().isBadRequest());

    verify(entryImageService, never()).upload(anyInt(), any(), any());
  }

  @Test
  void upload_notAPicture_badRequest() throws Exception {
    when(entryImageService.upload(anyInt(), any(), any()))
        .thenThrow(new IllegalArgumentException("Unsupported image format"));

    mockMvc
        .perform(multipart("/EntryImage").file(file(5000)).session(loggedIn()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error")
                .value("Unsupported picture. Upload a JPEG, PNG or GIF image of up to 100 megapixels."));
  }

  @Test
  void upload_altTooLong_badRequest() throws Exception {
    mockMvc
        .perform(
            multipart("/EntryImage")
                .file(file(5000))
                .param("alt", "x".repeat(256))
                .session(loggedIn()))
        .andExpect(status().isBadRequest());

    verify(entryImageService, never()).upload(anyInt(), any(), any());
  }

  @Test
  void upload_tooLarge_payloadTooLarge() throws Exception {
    mockMvc
        .perform(
            multipart("/EntryImage")
                .file(file((int) EntryImageController.MAX_UPLOAD_BYTES + 1))
                .session(loggedIn()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error").value("The file is too large. The limit is 20 MB."));

    verify(entryImageService, never()).upload(anyInt(), any(), any());
  }

  @Test
  void upload_notLoggedIn_forbidden() throws Exception {
    mockMvc.perform(multipart("/EntryImage").file(file(5000))).andExpect(status().isForbidden());

    verify(entryImageService, never()).upload(anyInt(), any(), any());
  }

  @Test
  void delete_owner_noContent() throws Exception {
    mockMvc
        .perform(delete("/EntryImage/7").session(loggedIn()))
        .andExpect(status().isNoContent());

    verify(entryImageService).delete(7, USER_ID);
  }

  @Test
  void delete_notLoggedIn_forbidden() throws Exception {
    mockMvc.perform(delete("/EntryImage/7")).andExpect(status().isForbidden());

    verify(entryImageService, never()).delete(anyInt(), anyInt());
  }

  @Test
  void delete_someoneElses_forbidden() throws Exception {
    doThrow(new ForbiddenException("You can only delete your own pictures"))
        .when(entryImageService)
        .delete(7, USER_ID);

    mockMvc.perform(delete("/EntryImage/7").session(loggedIn())).andExpect(status().isForbidden());
  }

  @Test
  void delete_missing_notFound() throws Exception {
    doThrow(new NotFoundException("No picture 7")).when(entryImageService).delete(7, USER_ID);

    mockMvc.perform(delete("/EntryImage/7").session(loggedIn())).andExpect(status().isNotFound());
  }
}
