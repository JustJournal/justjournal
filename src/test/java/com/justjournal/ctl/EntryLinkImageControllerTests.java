package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.exception.NotFoundException;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.EntryImageService;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EntryLinkImageControllerTests {

  private static final int USER_ID = 42;

  private EntryAttachmentService entryAttachmentService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    entryAttachmentService = mock(EntryAttachmentService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new EntryLinkImageController(entryAttachmentService))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private static EntryImageService.Picture picture(final boolean cacheable) {
    return new EntryImageService.Picture(
        new ByteArrayInputStream(new byte[] {1, 2, 3}), MediaType.IMAGE_JPEG_VALUE, cacheable);
  }

  @Test
  void approvedPictureOnAPublicPostIsCachedPublicly() throws Exception {
    when(entryAttachmentService.linkImage(4, 0)).thenReturn(picture(true));

    mockMvc
        .perform(get("/EntryLinkImage/4"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_JPEG))
        .andExpect(content().bytes(new byte[] {1, 2, 3}))
        .andExpect(header().string("Cache-Control", "max-age=3600, public"));
  }

  @Test
  void ownerSeesTheirHeldPictureUncached() throws Exception {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRID, USER_ID);
    when(entryAttachmentService.linkImage(4, USER_ID)).thenReturn(picture(false));

    mockMvc
        .perform(get("/EntryLinkImage/4").session(session))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void hiddenPictureIsNotFound() throws Exception {
    when(entryAttachmentService.linkImage(4, 0)).thenThrow(new NotFoundException("No picture 4"));

    mockMvc.perform(get("/EntryLinkImage/4")).andExpect(status().isNotFound());
    mockMvc.perform(get("/EntryLinkImage/x")).andExpect(status().isNotFound());
  }
}
