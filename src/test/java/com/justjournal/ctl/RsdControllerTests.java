package com.justjournal.ctl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.justjournal.config.ExceptionHandlerConfig;
import com.justjournal.core.Settings;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Blog clients follow the EditURI link on users.html to /rsd to find the XML-RPC endpoint. */
@ExtendWith(MockitoExtension.class)
class RsdControllerTests {

  private static final String API_LINK = "apiLink=\"https://www.justjournal.com/xml-rpc\"";

  @Mock private UserRepository userRepository;
  @Mock private Settings settings;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    lenient().when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");
    lenient()
        .when(settings.getBlogBaseUrl("jjsite"))
        .thenReturn("https://www.justjournal.com/users/jjsite");
    mockMvc =
        MockMvcBuilders.standaloneSetup(new RsdController(userRepository, settings))
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request).andReturn().getResponse();
  }

  private void assertRsd(MockHttpServletResponse response, int status) throws Exception {
    assertEquals(status, response.getStatus());
    final MediaType type = MediaType.parseMediaType(response.getContentType());
    assertEquals("application/rsd+xml", type.getType() + "/" + type.getSubtype());
    assertTrue(
        response.getContentAsString(StandardCharsets.UTF_8).startsWith("<?xml"),
        response.getContentAsString());
  }

  private User user() {
    final User user = new User();
    user.setUsername("jjsite");
    return user;
  }

  @Test
  void blogRsdAsLinkedFromUsersPage() throws Exception {
    when(userRepository.findByUsername("jjsite")).thenReturn(user());

    for (String accept : new String[] {null, "*/*", "application/rsd+xml",
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "text/html"}) {
      final MockHttpServletRequestBuilder request = get("/rsd").param("blogID", "jjsite");
      if (accept != null) request.header("Accept", accept);
      final MockHttpServletResponse response = perform(request);

      assertRsd(response, 200);
      final String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertTrue(body.contains("<engineName>JustJournal</engineName>"), body);
      assertTrue(
          body.contains("<homePageLink>https://www.justjournal.com/users/jjsite</homePageLink>"),
          body);
      assertTrue(body.contains("<api name=\"Blogger\""), body);
      assertTrue(body.contains("<api name=\"MetaWeblog\""), body);
      assertTrue(body.contains(API_LINK + " blogID=\"jjsite\""), body);
    }
  }

  @Test
  void bareRsdDescribesTheSite() throws Exception {
    for (String accept : new String[] {null, "*/*"}) {
      final MockHttpServletRequestBuilder request = get("/rsd");
      if (accept != null) request.header("Accept", accept);
      final MockHttpServletResponse response = perform(request);

      assertRsd(response, 200);
      final String body = response.getContentAsString(StandardCharsets.UTF_8);
      assertTrue(body.contains("<homePageLink>https://www.justjournal.com/</homePageLink>"), body);
      assertTrue(body.contains(API_LINK + " blogID=\"\""), body);
    }
  }

  @Test
  void unknownBlogIsNotFound() throws Exception {
    when(userRepository.findByUsername("nosuchuser")).thenReturn(null);

    assertRsd(perform(get("/rsd").param("blogID", "nosuchuser").header("Accept", "*/*")), 404);
  }
}
