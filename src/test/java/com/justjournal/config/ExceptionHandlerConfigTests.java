package com.justjournal.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class ExceptionHandlerConfigTests {

  @RestController
  static class FailingController {
    @GetMapping(value = "/html-fail", produces = "text/html")
    public String htmlFail(HttpServletResponse response) {
      // what a half-finished Thymeleaf render leaves behind
      response.setContentType("text/html;charset=UTF-8");
      throw new IllegalStateException("boom");
    }

    @GetMapping(value = "/year/{year}", produces = "text/html")
    public String year(@PathVariable int year) {
      return "ok";
    }

    @GetMapping("/count")
    public String count(@RequestParam int n) {
      return "ok";
    }
  }

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ExceptionHandlerConfig())
            .build();
  }

  @Test
  void generalError_onHtmlHandler_returnsJson500() throws Exception {
    mockMvc
        .perform(get("/html-fail"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value("Unknown error occurred. Please try again later."));
  }

  @Test
  void nonNumericPathVariable_returns404() throws Exception {
    mockMvc.perform(get("/year/users")).andExpect(status().isNotFound());
  }

  @Test
  void nonNumericRequestParam_returns400() throws Exception {
    mockMvc.perform(get("/count").param("n", "abc")).andExpect(status().isBadRequest());
  }
}
