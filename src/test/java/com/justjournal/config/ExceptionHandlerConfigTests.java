package com.justjournal.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping(value = "/feed", produces = "application/rsd+xml")
    public String feed() {
      return "<rsd/>";
    }

    @PostMapping(value = "/echo", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String echo(@RequestBody Map<String, String> body) {
      return "ok";
    }

    @GetMapping("/gone")
    public String gone() {
      throw new GoneException();
    }
  }

  @ResponseStatus(HttpStatus.GONE)
  static class GoneException extends RuntimeException {}

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

  @Test
  void notAcceptable_returns406NotA500() throws Exception {
    mockMvc
        .perform(get("/feed").accept(MediaType.TEXT_HTML))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void wrongMethod_returns405WithAllowHeader() throws Exception {
    mockMvc
        .perform(delete("/count"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", "GET"))
        .andExpect(jsonPath("$.error").value("Method Not Allowed"));
  }

  @Test
  void missingRequestParam_returns400() throws Exception {
    mockMvc.perform(get("/count")).andExpect(status().isBadRequest());
  }

  @Test
  void unsupportedContentType_returns415() throws Exception {
    mockMvc
        .perform(post("/echo").contentType(MediaType.TEXT_PLAIN).content("hi"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void malformedJsonBody_returns400() throws Exception {
    mockMvc
        .perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  void responseStatusAnnotation_isRespected() throws Exception {
    mockMvc.perform(get("/gone")).andExpect(status().isGone());
  }
}
