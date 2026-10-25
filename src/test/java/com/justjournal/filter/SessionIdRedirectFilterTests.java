package com.justjournal.filter;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionIdRedirectFilterTests {

  private final SessionIdRedirectFilter filter = new SessionIdRedirectFilter();

  private MockHttpServletResponse run(final MockHttpServletRequest request, final FilterChain chain)
      throws Exception {
    final MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }

  @ParameterizedTest
  @CsvSource({
    "/;jsessionid=92859A130DD62B97E75F07C804B6A341, /",
    "/users/mbsd;jsessionid=ABC, /users/mbsd",
    "/users/mbsd/json;jsessionid=E1F682D24407109FD166E039E821E41C, /users/mbsd/json",
    "/users/mbsd/rtf%3Bjsessionid=472464554B6B, /users/mbsd/rtf",
    "/users/mbsd;JSESSIONID=abc/2003, /users/mbsd/2003",
    "//evil.example;jsessionid=x, /evil.example",
  })
  void get_withSessionId_redirectedWithoutIt(final String uri, final String expected)
      throws Exception {
    final MockFilterChain chain = new MockFilterChain();

    final MockHttpServletResponse response = run(new MockHttpServletRequest("GET", uri), chain);

    assertEquals(301, response.getStatus());
    assertEquals(expected, response.getHeader("Location"));
    assertNull(chain.getRequest(), "request should not continue");
  }

  @Test
  void queryString_kept() throws Exception {
    final MockHttpServletRequest request =
        new MockHttpServletRequest("HEAD", "/users/mbsd;jsessionid=ABC");
    request.setQueryString("page=2");

    final MockHttpServletResponse response = run(request, new MockFilterChain());

    assertEquals(301, response.getStatus());
    assertEquals("/users/mbsd?page=2", response.getHeader("Location"));
  }

  @Test
  void post_withSessionId_passedThrough() throws Exception {
    final MockFilterChain chain = new MockFilterChain();

    final MockHttpServletResponse response =
        run(new MockHttpServletRequest("POST", "/xml-rpc;jsessionid=ABC"), chain);

    assertEquals(200, response.getStatus());
    assertNotNull(chain.getRequest());
  }

  @Test
  void normalAddress_passedThrough() throws Exception {
    final MockFilterChain chain = new MockFilterChain();

    final MockHttpServletResponse response =
        run(new MockHttpServletRequest("GET", "/users/mbsd/json"), chain);

    assertNull(response.getHeader("Location"));
    assertNotNull(chain.getRequest());
  }
}
