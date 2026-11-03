package com.justjournal.ctl.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.ModerationStatus;
import com.justjournal.services.AdminModerationService;
import com.justjournal.services.AdminModerationService.HeldFile;
import com.justjournal.services.AdminModerationService.HeldPicture;
import com.justjournal.services.AdminModerationService.Kind;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class AdminImageControllerTests {

  private AdminModerationService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(AdminModerationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AdminImageController(service))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private static MockHttpSession admin() {
    final MockHttpSession session = new MockHttpSession();
    session.setAttribute(AdminSession.ADMIN_ATTR, "root");
    return session;
  }

  private static HeldFile file(final String mimeType) {
    return new HeldFile(new ByteArrayInputStream(new byte[] {1, 2, 3}), mimeType);
  }

  @Test
  void list_model() throws Exception {
    final List<HeldPicture> held =
        List.of(new HeldPicture(Kind.ALBUM, 1, 4, "alice", "sexy", "/users/alice/pictures/1"));
    when(service.held()).thenReturn(held);

    mockMvc
        .perform(get("/admin/images").session(admin()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/layout"))
        .andExpect(model().attribute("adminSection", "images"))
        .andExpect(model().attribute("contentTemplate", "admin/images"))
        .andExpect(model().attribute("pictures", held))
        .andExpect(model().attribute("maxPerKind", AdminModerationService.MAX_PER_KIND));
  }

  @Test
  void image_servedWithStoredTypeAndNoStore() throws Exception {
    when(service.file(Kind.POST, 2)).thenReturn(file(MediaType.IMAGE_GIF_VALUE));

    mockMvc
        .perform(get("/admin/images/post/2").session(admin()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_GIF))
        .andExpect(content().bytes(new byte[] {1, 2, 3}))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  @Test
  void image_eachKindByName() throws Exception {
    when(service.file(any(), anyInt())).thenReturn(file(MediaType.IMAGE_JPEG_VALUE));

    for (final Kind kind : Kind.values()) {
      mockMvc
          .perform(get("/admin/images/" + kind.getPath() + "/5").session(admin()))
          .andExpect(status().isOk());
      verify(service).file(kind, 5);
    }
  }

  @Test
  void image_unexpectedType_sentAsDownload() throws Exception {
    when(service.file(Kind.ALBUM, 1)).thenReturn(file("text/html"));

    mockMvc
        .perform(get("/admin/images/album/1").session(admin()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
  }

  @Test
  void image_unknownKind_notFound() throws Exception {
    mockMvc.perform(get("/admin/images/other/1").session(admin())).andExpect(status().isNotFound());
    mockMvc.perform(get("/admin/images/ALBUM/1").session(admin())).andExpect(status().isNotFound());
    mockMvc.perform(get("/admin/images/album/x").session(admin())).andExpect(status().isNotFound());
    verify(service, never()).file(any(), anyInt());
  }

  @Test
  void image_notHeld_notFound() throws Exception {
    when(service.file(Kind.AVATAR, 4)).thenThrow(new NotFoundException("not held"));

    mockMvc
        .perform(get("/admin/images/avatar/4").session(admin()))
        .andExpect(status().isNotFound());
  }

  @Test
  void decide_approved() throws Exception {
    when(service.decide(Kind.LINK, 9, ModerationStatus.APPROVED, "root")).thenReturn(true);

    mockMvc
        .perform(post("/admin/images/link/9").param("decision", "APPROVED").session(admin()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/images"))
        .andExpect(flash().attribute("message", "Link preview 9 approved."));
    verify(service).decide(Kind.LINK, 9, ModerationStatus.APPROVED, "root");
  }

  @Test
  void decide_rejected() throws Exception {
    when(service.decide(Kind.ALBUM, 1, ModerationStatus.REJECTED, "root")).thenReturn(true);

    mockMvc
        .perform(post("/admin/images/album/1").param("decision", "REJECTED").session(admin()))
        .andExpect(redirectedUrl("/admin/images"))
        .andExpect(flash().attribute("message", "Album picture 1 rejected."));
  }

  @Test
  void decide_noLongerHeld_error() throws Exception {
    mockMvc
        .perform(post("/admin/images/post/2").param("decision", "APPROVED").session(admin()))
        .andExpect(redirectedUrl("/admin/images"))
        .andExpect(
            flash()
                .attribute(
                    "error",
                    "Post picture 2 is no longer waiting for review; nothing was changed."));
  }

  @Test
  void decide_badDecision_refused() throws Exception {
    for (final String decision : new String[] {"REVIEW", "approved", "", "DELETE"}) {
      mockMvc
          .perform(post("/admin/images/album/1").param("decision", decision).session(admin()))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(post("/admin/images/album/1").session(admin()))
        .andExpect(status().isBadRequest());
    verify(service, never()).decide(any(), anyInt(), any(), anyString());
  }

  @Test
  void decide_unknownKind_notFound() throws Exception {
    mockMvc
        .perform(post("/admin/images/other/1").param("decision", "APPROVED").session(admin()))
        .andExpect(status().isNotFound());
    verify(service, never()).decide(any(), anyInt(), any(), anyString());
  }

  // the page itself

  private static String render(final List<HeldPicture> pictures) {
    final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    final SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);

    final MockServletContext servletContext = new MockServletContext();
    final WebContext context =
        new WebContext(
            JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(
                    new MockHttpServletRequest(servletContext), new MockHttpServletResponse()),
            Locale.US);
    final Map<String, Object> vars = new HashMap<>();
    vars.put("title", "Pictures to review");
    vars.put("adminSection", "images");
    vars.put("contentTemplate", "admin/images");
    vars.put("adminLoggedIn", true);
    vars.put("csrfToken", "tok123");
    vars.put("maxPerKind", AdminModerationService.MAX_PER_KIND);
    vars.put("pictures", pictures);
    vars.forEach(context::setVariable);
    return engine.process("admin/layout", context);
  }

  @Test
  void page_listsPicturesWithFormsCarryingTheToken() {
    final String html =
        render(
            List.of(
                new HeldPicture(Kind.POST, 2, 5, "bob", "animated", "/users/bob/entry/30"),
                new HeldPicture(Kind.AVATAR, 7, 7, null, null, null)));

    assertTrue(html.contains("src=\"/admin/images/post/2\""), html);
    assertTrue(html.contains("action=\"/admin/images/post/2\""), html);
    assertTrue(html.contains("href=\"/users/bob/entry/30\""), html);
    assertTrue(html.contains("animated"), html);
    assertTrue(html.contains(">bob<"), html);
    assertTrue(html.contains("src=\"/admin/images/avatar/7\""), html);
    assertTrue(html.contains("missing user 7"), html);
    assertTrue(html.contains("name=\"_csrf\" value=\"tok123\""), html);
    assertTrue(html.contains("value=\"APPROVED\""), html);
    assertTrue(html.contains("value=\"REJECTED\""), html);
    assertFalse(html.contains("No pictures are waiting for review."), html);
  }

  @Test
  void page_emptyState() {
    final String html = render(List.of());

    assertTrue(html.contains("No pictures are waiting for review."), html);
    assertFalse(html.contains("value=\"APPROVED\""), html);
  }
}
