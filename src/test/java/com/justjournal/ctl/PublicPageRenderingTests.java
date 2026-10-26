package com.justjournal.ctl;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import com.justjournal.core.Constants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders every public page through the real layout and fragment templates, so a Thymeleaf
 * upgrade that breaks the {@code ~{${contentTemplate} :: content}} include, or any fragment,
 * fails here instead of in production.
 */
class PublicPageRenderingTests {

  private static MockMvc mockMvc;

  @BeforeAll
  static void setUp() {
    final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode("HTML");
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCacheable(false);

    final SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);

    final ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
    viewResolver.setTemplateEngine(engine);
    viewResolver.setCharacterEncoding("UTF-8");

    mockMvc =
        MockMvcBuilders.standaloneSetup(new HomeController(), new PublicPageController())
            .setViewResolvers(viewResolver)
            .build();
  }

  /** Every GET route on PublicPageController (path variables filled in), plus the home page. */
  static Stream<String> pages() {
    final List<String> paths = new ArrayList<>();
    paths.add("/");
    for (final Method m : PublicPageController.class.getDeclaredMethods()) {
      final GetMapping mapping = m.getAnnotation(GetMapping.class);
      if (mapping == null) continue;
      for (final String path : mapping.value()) {
        paths.add(path.replace("{username}", "jjsite").replaceAll("\\{[^}]+}", "1"));
      }
    }
    return paths.stream().sorted();
  }

  @ParameterizedTest
  @MethodSource("pages")
  void page_rendersLayoutWithItsFragment(final String path) throws Exception {
    final MvcResult result = mockMvc.perform(get(path)).andReturn();

    if (path.equals("/cancel")) {
      assertEquals(302, result.getResponse().getStatus());
      return;
    }

    mockMvc.perform(get(path)).andExpect(status().isOk());
    final String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

    assertTrue(html.contains("id=\"copyright\""), "layout footer missing for " + path);
    assertFalse(html.contains("th:fragment"), "unprocessed fragment for " + path);
    assertFalse(html.contains("Loading…"), "layout placeholder not replaced for " + path);
    assertTrue(html.trim().endsWith("</html>"), "page cut short for " + path);
  }

  private static String render(final String path, final String username) throws Exception {
    final MockHttpSession session = new MockHttpSession();
    if (username != null) session.setAttribute(Constants.LOGIN_ATTRNAME, username);
    return mockMvc
        .perform(get(path).session(session))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void sitemap_hidesAccountLinksWhenLoggedOut() throws Exception {
    final String html = render("/sitemap", null);
    assertFalse(html.contains(">Preferences</a>"));
    assertFalse(html.contains("href=\"/prefs/journaldelete\""));
    assertFalse(html.contains("href=\"/logout\""));
  }

  @Test
  void sitemap_showsAccountLinksWhenLoggedIn() throws Exception {
    final String html = render("/sitemap", "someone");
    assertTrue(html.contains("href=\"/prefs\""));
    assertTrue(html.contains("href=\"/prefs/journaldelete\""));
    assertTrue(html.contains("href=\"/logout\""));
  }

  @Test
  void prefs_listsEveryPreferencePageWhenLoggedIn() throws Exception {
    final String html = render("/prefs", "someone");
    for (final Method m : PublicPageController.class.getDeclaredMethods()) {
      final GetMapping mapping = m.getAnnotation(GetMapping.class);
      if (mapping == null) continue;
      for (final String path : mapping.value()) {
        if (path.startsWith("/prefs/")) {
          assertTrue(html.contains("href=\"" + path + "\""), "landing page missing " + path);
        }
      }
    }
  }

  @Test
  void prefs_asksToLogInWhenLoggedOut() throws Exception {
    final String html = render("/prefs", null);
    assertTrue(html.contains("id=\"prefs-login\""));
    assertFalse(html.contains("id=\"prefs-list\""));
  }
}
