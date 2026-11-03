package com.justjournal.ctl.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.exception.NotFoundException;
import com.justjournal.repository.SettingsRepository;
import com.justjournal.services.AdminSettingsService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/** The settings page lists every row without secret values and saves one value at a time. */
class AdminSettingsControllerTests {

  private static final String SECRET = "hunter2-mail-password";

  private SettingsRepository repository;
  private AdminSettingsService service;
  private AdminSettingsController controller;

  @BeforeEach
  void setUp() {
    repository = mock(SettingsRepository.class);
    when(repository.findAll(Sort.by("name")))
        .thenReturn(
            List.of(
                row(9, "mailHost", "smtp.example.com"),
                row(10, "mailPass", SECRET),
                row(11, "mailPort", "25")));
    service = new AdminSettingsService(repository, mock(Settings.class));
    controller = new AdminSettingsController(service);
  }

  private static com.justjournal.model.Settings row(
      final int id, final String name, final String value) {
    final com.justjournal.model.Settings row = new com.justjournal.model.Settings();
    row.setId(id);
    row.setName(name);
    row.setValue(value);
    return row;
  }

  private static String render(final Map<String, Object> model) {
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
    model.forEach(context::setVariable);
    context.setVariable("csrfToken", "form-token");
    context.setVariable("adminLoggedIn", true);
    return engine.process("admin/layout", context);
  }

  @Test
  void list_usesTheAdminLayout_andHidesSecretValues() {
    final ExtendedModelMap model = new ExtendedModelMap();

    assertEquals("admin/layout", controller.list(model));

    assertEquals("settings", model.getAttribute("adminSection"));
    assertEquals("admin/settings", model.getAttribute("contentTemplate"));
    final List<?> settings = (List<?>) model.getAttribute("settings");
    assertEquals(3, settings.size());
    assertNull(((AdminSettingsService.SettingView) settings.get(1)).value());
    assertFalse(model.toString().contains(SECRET));
  }

  @Test
  void page_rendersAFormPerRow_withTheToken_andNeverTheSecret() {
    final ExtendedModelMap model = new ExtendedModelMap();
    controller.list(model);

    final String html = render(model);

    assertFalse(html.contains(SECRET), html);
    assertTrue(html.contains("action=\"/admin/settings/9\""), html);
    assertTrue(html.contains("action=\"/admin/settings/10\""), html);
    assertTrue(html.contains("action=\"/admin/settings/11\""), html);
    assertTrue(html.contains("value=\"smtp.example.com\""), html);
    assertTrue(html.contains("type=\"password\""), html);
    assertTrue(html.contains("Leave blank to keep the current value"), html);
    assertEquals(4, html.split("name=\"_csrf\" value=\"form-token\"", -1).length - 1, html);
  }

  @Test
  void update_redirectsWithAMessage() {
    when(repository.findById(9)).thenReturn(Optional.of(row(9, "mailHost", "a")));
    final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

    assertEquals("redirect:/admin/settings", controller.update(9, "smtp2.example.com", redirect));

    assertEquals("Saved mailHost.", redirect.getFlashAttributes().get("message"));
    assertNull(redirect.getFlashAttributes().get("error"));
  }

  @Test
  void update_withAnInvalidValue_redirectsWithAnError() {
    when(repository.findById(11)).thenReturn(Optional.of(row(11, "mailPort", "25")));
    final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

    assertEquals("redirect:/admin/settings", controller.update(11, "lots", redirect));

    assertEquals("mailPort must be a whole number.", redirect.getFlashAttributes().get("error"));
    assertNull(redirect.getFlashAttributes().get("message"));
  }

  @Test
  void update_ofAnUnknownRow_isNotFound() {
    when(repository.findById(99)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> controller.update(99, "x", new RedirectAttributesModelMap()));
  }
}
