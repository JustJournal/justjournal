package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OllamaClientTests {

  private static final String ANSWER =
      "{\"model\":\"vision\",\"message\":{\"role\":\"assistant\","
          + "\"content\":\"{\\\"category\\\":\\\"safe\\\",\\\"reason\\\":\\\"a cat\\\"}\"},\"done\":true}";

  private MockRestServiceServer server;
  private OllamaClient client;
  private JsonNode format;

  @BeforeEach
  void setUp() throws IOException {
    final RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new OllamaClient(restTemplate);
    client.setUrls(List.of("http://one:11434", "http://two:11435"));
    format = new ObjectMapper().readTree("{\"type\":\"object\"}");
  }

  @Test
  void chat_sendsImageAndSchema_returnsParsedAnswer() throws Exception {
    final byte[] image = {1, 2, 3};
    server
        .expect(once(), requestTo("http://one:11434/api/chat"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.model").value("vision"))
        .andExpect(jsonPath("$.stream").value(false))
        .andExpect(jsonPath("$.format.type").value("object"))
        .andExpect(jsonPath("$.options.temperature").value(0))
        .andExpect(jsonPath("$.messages[0].role").value("user"))
        .andExpect(jsonPath("$.messages[0].content").value("what is it"))
        .andExpect(
            jsonPath("$.messages[0].images[0]").value(Base64.getEncoder().encodeToString(image)))
        .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

    final JsonNode answer = client.chat("vision", "what is it", image, format);

    assertEquals("safe", answer.path("category").asText());
    assertEquals("a cat", answer.path("reason").asText());
    server.verify(); // skipcq: JAVA-E1109 checks the expected requests were made, not a Mockito verify
  }

  @Test
  void chat_spreadsRequestsOverServers() throws Exception {
    server.expect(once(), requestTo("http://one:11434/api/chat")).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
    server.expect(once(), requestTo("http://two:11435/api/chat")).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
    server.expect(once(), requestTo("http://one:11434/api/chat")).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

    for (int i = 0; i < 3; i++) client.chat("vision", "q", new byte[] {1}, format);
    server.verify(); // skipcq: JAVA-E1109 checks the expected requests were made, not a Mockito verify
  }

  @Test
  void chat_serverFails_nextOneTried() throws Exception {
    server.expect(once(), requestTo("http://one:11434/api/chat")).andRespond(withServerError());
    server.expect(once(), requestTo("http://two:11435/api/chat")).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

    assertEquals("safe", client.chat("vision", "q", new byte[] {1}, format).path("category").asText());
    server.verify(); // skipcq: JAVA-E1109 checks the expected requests were made, not a Mockito verify
  }

  @Test
  void chat_allFail_ioException() {
    server.expect(once(), requestTo("http://one:11434/api/chat")).andRespond(withServerError());
    server.expect(once(), requestTo("http://two:11435/api/chat")).andRespond(withSuccess("{\"message\":{\"content\":\"\"}}", MediaType.APPLICATION_JSON));

    assertThrows(IOException.class, () -> client.chat("vision", "q", new byte[] {1}, format));
    server.verify(); // skipcq: JAVA-E1109 checks the expected requests were made, not a Mockito verify
  }

  @Test
  void chat_noServers_ioException() {
    client.setUrls(List.of());
    assertThrows(IOException.class, () -> client.chat("vision", "q", new byte[] {1}, format));
  }
}
