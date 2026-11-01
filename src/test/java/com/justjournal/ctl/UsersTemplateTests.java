package com.justjournal.ctl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.justjournal.model.Journal;
import com.justjournal.model.Style;
import com.justjournal.model.User;
import com.justjournal.model.UserImage;
import com.justjournal.services.UserImageService;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Renders users.html with the model attributes each kind of page sets. Several handlers don't set
 * every attribute (pictures, search, subscriptions and tags leave out avatar), so the template must
 * cope with them being missing.
 */
class UsersTemplateTests {

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

    final Style style = new Style();
    style.setId(25);
    final Journal journal = new Journal();
    journal.setName("Tech News");
    journal.setStyle(style);
    final User user = new User();
    user.setUsername("technews");
    user.setId(7);

    context.setVariable("journal", journal);
    context.setVariable("user", user);
    vars.forEach(context::setVariable);
    return engine.process("users", context);
  }

  private static UserImage image(final int id, final String title) {
    final UserImage image = new UserImage();
    image.setId(id);
    image.setTitle(title);
    image.setModified(new Date());
    return image;
  }

  @Test
  void rendersPicturesWithoutAvatarAttribute() {
    final String html = render(Map.of("pictures", List.of(image(1, "Beach"))));

    assertTrue(html.contains("href=\"/users/technews/pictures/1\""), html);
  }

  @Test
  void rendersPageWithNoOptionalAttributes() {
    assertTrue(render(Map.of()).contains("<title>Tech News</title>"));
  }

  @Test
  void settingsMenuLinksTwoFactorWhenLoggedIn() {
    assertTrue(render(Map.of("authenticatedUsername", "technews")).contains("href=\"/prefs/twofactor\""));
    assertFalse(render(Map.of()).contains("href=\"/prefs/twofactor\""));
  }

  @Test
  void rendersPicturePage() {
    final UserImage a = image(1, "Beach"), b = image(2, "Sunset");
    final String html =
        render(
            Map.of(
                "avatar", true,
                "picture", new UserImageService.PicturePage(b, a, null)));

    assertTrue(html.contains("<title>Sunset - Tech News</title>"), html);
    assertTrue(html.contains("rel=\"canonical\" href=\"https://www.justjournal.com/users/technews/pictures/2\""), html);
    assertTrue(html.contains("href=\"/users/technews/pictures/1\" rel=\"prev\""), html);
  }
}
