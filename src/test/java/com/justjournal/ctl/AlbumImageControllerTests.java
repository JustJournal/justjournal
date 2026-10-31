package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.UserImage;
import com.justjournal.services.AlbumImageService;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlbumImageControllerTests {

  private static final int USER_ID = 42;

  private AlbumImageService albumImageService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    albumImageService = mock(AlbumImageService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AlbumImageController(albumImageService))
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

  private static AlbumImageService.Picture picture(final String mimeType) {
    return new AlbumImageService.Picture(new ByteArrayInputStream(new byte[] {1, 2, 3}), mimeType);
  }

  @Test
  void get_servedWithStoredTypeAndCacheHeader() throws Exception {
    when(albumImageService.picture(7, 0)).thenReturn(picture(MediaType.IMAGE_PNG_VALUE));

    mockMvc
        .perform(get("/AlbumImage/7"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(new byte[] {1, 2, 3}))
        .andExpect(header().string("Cache-Control", "max-age=86400, public"));
  }

  @Test
  void get_byQueryParameter_forTheRssFeed() throws Exception {
    when(albumImageService.picture(7, 0)).thenReturn(picture(MediaType.IMAGE_JPEG_VALUE));

    mockMvc.perform(get("/AlbumImage").param("id", "7")).andExpect(status().isOk());
  }

  @Test
  void get_badIds() throws Exception {
    mockMvc.perform(get("/AlbumImage").param("id", "0")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/AlbumImage").param("id", "x")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/AlbumImage/x")).andExpect(status().isNotFound());
  }

  @Test
  void get_missing_notFound() throws Exception {
    when(albumImageService.picture(7, 0)).thenThrow(new NotFoundException("No picture 7"));

    mockMvc.perform(get("/AlbumImage/7")).andExpect(status().isNotFound());
  }

  @Test
  void get_loggedIn_viewerPassedAlong() throws Exception {
    when(albumImageService.picture(7, USER_ID)).thenReturn(picture(MediaType.IMAGE_JPEG_VALUE));

    mockMvc.perform(get("/AlbumImage/7").session(loggedIn())).andExpect(status().isOk());
    verify(albumImageService).picture(7, USER_ID);
  }

  @Test
  void thumbnail_servedAsJpeg() throws Exception {
    when(albumImageService.thumbnail(7, 0)).thenReturn(picture(MediaType.IMAGE_JPEG_VALUE));

    mockMvc
        .perform(get("/AlbumImage/7/thumbnail"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_JPEG));
  }

  @Test
  void upload_created() throws Exception {
    final UserImage saved = new UserImage();
    saved.setId(99);
    when(albumImageService.upload(eq(USER_ID), eq("Beach"), any())).thenReturn(saved);

    mockMvc
        .perform(
            multipart("/AlbumImage").file(file(5000)).param("title", "  Beach ").session(loggedIn()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/AlbumImage/99"));
  }

  @Test
  void upload_heldForReview_accepted() throws Exception {
    final UserImage saved = new UserImage();
    saved.setId(99);
    saved.setModerationStatus(ModerationStatus.REVIEW);
    when(albumImageService.upload(eq(USER_ID), eq("Beach"), any())).thenReturn(saved);

    mockMvc
        .perform(multipart("/AlbumImage").file(file(5000)).param("title", "Beach").session(loggedIn()))
        .andExpect(status().isAccepted())
        .andExpect(header().string("Location", "/AlbumImage/99"))
        .andExpect(jsonPath("$.message").value(AlbumImageController.REVIEW_MESSAGE));
  }

  @Test
  void upload_rejectedByFilter_badRequestWithReason() throws Exception {
    when(albumImageService.upload(eq(USER_ID), eq("Beach"), any()))
        .thenThrow(new BadRequestException(AlbumImageService.REJECTED_MESSAGE));

    mockMvc
        .perform(multipart("/AlbumImage").file(file(5000)).param("title", "Beach").session(loggedIn()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(AlbumImageService.REJECTED_MESSAGE));
  }

  @Test
  void upload_blankTitle_untitled() throws Exception {
    final UserImage saved = new UserImage();
    saved.setId(99);
    when(albumImageService.upload(eq(USER_ID), eq("untitled"), any())).thenReturn(saved);

    mockMvc
        .perform(multipart("/AlbumImage").file(file(5000)).param("title", " ").session(loggedIn()))
        .andExpect(status().isCreated());
  }

  @Test
  void upload_phonePhotoSize_accepted() throws Exception {
    final UserImage saved = new UserImage();
    saved.setId(99);
    when(albumImageService.upload(anyInt(), anyString(), any())).thenReturn(saved);

    mockMvc
        .perform(multipart("/AlbumImage").file(file(12 * 1024 * 1024)).session(loggedIn()))
        .andExpect(status().isCreated());
  }

  @Test
  void upload_notLoggedIn_forbidden() throws Exception {
    mockMvc.perform(multipart("/AlbumImage").file(file(5000))).andExpect(status().isForbidden());

    verify(albumImageService, never()).upload(anyInt(), any(), any());
  }

  @Test
  void upload_tooLarge_payloadTooLarge() throws Exception {
    mockMvc
        .perform(
            multipart("/AlbumImage")
                .file(file((int) AlbumImageController.MAX_UPLOAD_BYTES + 1))
                .session(loggedIn()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error").value("The file is too large. The limit is 20 MB."));

    verify(albumImageService, never()).upload(anyInt(), any(), any());
  }

  @Test
  void upload_tooSmall_badRequest() throws Exception {
    mockMvc
        .perform(multipart("/AlbumImage").file(file(10)).session(loggedIn()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void upload_titleTooLong_badRequest() throws Exception {
    mockMvc
        .perform(
            multipart("/AlbumImage")
                .file(file(5000))
                .param("title", "x".repeat(151))
                .session(loggedIn()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void upload_notAPicture_badRequest() throws Exception {
    when(albumImageService.upload(anyInt(), anyString(), any()))
        .thenThrow(new IllegalArgumentException("Unsupported image format"));

    mockMvc
        .perform(multipart("/AlbumImage").file(file(5000)).session(loggedIn()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void delete_owner_noContent() throws Exception {
    mockMvc
        .perform(delete("/AlbumImage/7").session(loggedIn()))
        .andExpect(status().isNoContent());

    verify(albumImageService).delete(7, USER_ID);
  }

  @Test
  void delete_notLoggedIn_forbidden() throws Exception {
    mockMvc.perform(delete("/AlbumImage/7")).andExpect(status().isForbidden());

    verify(albumImageService, never()).delete(anyInt(), anyInt());
  }

  @Test
  void delete_someoneElses_forbidden() throws Exception {
    doThrow(new ForbiddenException("You can only delete your own pictures"))
        .when(albumImageService)
        .delete(7, USER_ID);

    mockMvc.perform(delete("/AlbumImage/7").session(loggedIn())).andExpect(status().isForbidden());
  }

  @Test
  void delete_missing_notFound() throws Exception {
    doThrow(new NotFoundException("No picture 7")).when(albumImageService).delete(7, USER_ID);

    mockMvc.perform(delete("/AlbumImage/7").session(loggedIn())).andExpect(status().isNotFound());
  }
}
