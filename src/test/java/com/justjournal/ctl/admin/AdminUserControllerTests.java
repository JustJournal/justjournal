package com.justjournal.ctl.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.User;
import com.justjournal.services.AdminAccountService;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

/** The admin account list and its disable and enable buttons. */
class AdminUserControllerTests {

  private AdminAccountService adminAccountService;
  private MockMvc mockMvc;
  private MockHttpSession session;
  private User bob;

  @BeforeEach
  void setUp() {
    adminAccountService = mock(AdminAccountService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminAccountService)).build();
    session = new MockHttpSession();
    session.setAttribute(AdminSession.ADMIN_ATTR, "root");

    bob = new User();
    bob.setId(7);
    bob.setUsername("bob");
    bob.setName("Bob");
  }

  @Test
  void list_showsTheAccountsPage() throws Exception {
    final Page<User> page = new PageImpl<>(List.of(bob));
    when(adminAccountService.search("bo", 0)).thenReturn(page);

    mockMvc
        .perform(get("/admin/users").param("q", " bo ").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name(AdminUserController.VIEW))
        .andExpect(model().attribute("adminSection", "users"))
        .andExpect(model().attribute("contentTemplate", "admin/users"))
        .andExpect(model().attribute("q", "bo"))
        .andExpect(model().attribute("users", page));
  }

  @Test
  void disable_disablesAndGoesBackToTheSearch() throws Exception {
    when(adminAccountService.disable(7, "root")).thenReturn(bob);

    mockMvc
        .perform(post("/admin/users/7/disable").param("q", "bo").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/users?q=bo"))
        .andExpect(flash().attributeExists("message"));

    verify(adminAccountService).disable(7, "root");
  }

  @Test
  void enable_enablesAndGoesBackToTheList() throws Exception {
    when(adminAccountService.enable(7, "root")).thenReturn(bob);

    mockMvc
        .perform(post("/admin/users/7/enable").session(session))
        .andExpect(redirectedUrl("/admin/users"))
        .andExpect(flash().attribute("message", "Enabled bob."));

    verify(adminAccountService).enable(7, "root");
  }

  @Test
  void unknownAccount_saysSo() throws Exception {
    when(adminAccountService.disable(anyInt(), anyString()))
        .thenThrow(new NotFoundException("No account 9"));
    when(adminAccountService.enable(anyInt(), anyString()))
        .thenThrow(new NotFoundException("No account 9"));

    mockMvc
        .perform(post("/admin/users/9/disable").session(session))
        .andExpect(redirectedUrl("/admin/users"))
        .andExpect(flash().attribute("error", "That account doesn't exist."));
    mockMvc
        .perform(post("/admin/users/9/enable").session(session))
        .andExpect(flash().attribute("error", "That account doesn't exist."));
  }

  // ---- template -------------------------------------------------------------------------------

  private static String render(final Map<String, Object> vars) {
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
    context.setVariable("title", "Accounts");
    context.setVariable("adminSection", "users");
    context.setVariable("contentTemplate", "admin/users");
    context.setVariable("adminLoggedIn", true);
    context.setVariable("csrfToken", "tok");
    vars.forEach(context::setVariable);
    return engine.process(AdminUserController.VIEW, context);
  }

  @Test
  void template_showsEachAccountWithTheRightButton() {
    bob.setLastName("Smith");
    bob.setLastLogin(new Date(0));
    final User mallory = new User();
    mallory.setId(8);
    mallory.setUsername("mallory");
    mallory.setName("Mal");
    mallory.setDisabled(true);

    final String html =
        render(
            Map.of(
                "q", "o",
                "users", new PageImpl<>(List.of(bob, mallory), PageRequest.of(0, 50), 2)));

    assertTrue(html.contains("Bob Smith"), html);
    assertTrue(html.contains("action=\"/admin/users/7/disable\""), html);
    assertTrue(html.contains("action=\"/admin/users/8/enable\""), html);
    assertFalse(html.contains("action=\"/admin/users/7/enable\""), html);
    assertTrue(html.contains("Never"), html);
    assertTrue(html.contains("Disabled"), html);
    assertTrue(html.contains("name=\"_csrf\" value=\"tok\""), html);
    assertTrue(html.contains("name=\"q\" value=\"o\""), html);
  }

  @Test
  void template_saysWhenNothingMatches() {
    final String html =
        render(
            Map.of("q", "zzz", "users", new PageImpl<User>(List.of(), PageRequest.of(0, 50), 0)));

    assertTrue(html.contains("No accounts found."), html);
    assertFalse(html.contains("<table"), html);
  }
}
