package com.justjournal.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Talks to one or more Ollama servers. Requests are spread over the servers in turn, and a server
 * that fails is skipped for that request, so one being down or missing the model costs a little
 * time rather than the request.
 */
@Slf4j
@Service
public class OllamaClient {

  /** Servers to use, as a comma separated list of base URLs. */
  @Setter
  @Value("#{'${moderation.ollama.urls:http://localhost:11434}'.split(',')}")
  private List<String> urls;

  private final RestTemplate restTemplate;
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicInteger next = new AtomicInteger();

  @Autowired // there is a second constructor for tests; this is the one Spring uses
  public OllamaClient(
      final RestTemplateBuilder builder,
      @Value("${moderation.ollama.timeout-seconds:90}") final long timeoutSeconds) {
    // the shared RestTemplate gives up after 10 seconds; a vision model can take longer
    this(
        builder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(timeoutSeconds))
            .build());
  }

  OllamaClient(final RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * Ask a model about an image and get its answer as JSON.
   *
   * @param model model name, as {@code ollama list} shows it
   * @param prompt what to ask
   * @param image the image, in any format the model reads (JPEG and PNG are safe)
   * @param format JSON schema the answer must follow
   * @return the answer, parsed
   * @throws IOException if no server gave a usable answer
   */
  public JsonNode chat(
      final String model, final String prompt, final byte[] image, final JsonNode format)
      throws IOException {
    final ObjectNode body = mapper.createObjectNode();
    body.put("model", model);
    body.put("stream", false);
    body.set("format", format);
    body.putObject("options").put("temperature", 0);
    final ArrayNode messages = body.putArray("messages");
    final ObjectNode message = messages.addObject();
    message.put("role", "user");
    message.put("content", prompt);
    message.putArray("images").add(Base64.getEncoder().encodeToString(image));

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    final HttpEntity<String> request = new HttpEntity<>(mapper.writeValueAsString(body), headers);

    if (urls == null || urls.isEmpty()) throw new IOException("No Ollama servers configured");
    final int first = next.getAndIncrement();
    IOException failure = null;
    for (int i = 0; i < urls.size(); i++) {
      final String url = urls.get(Math.floorMod(first + i, urls.size())).trim();
      if (url.isEmpty()) continue;
      try {
        final String response =
            restTemplate.postForObject(url + "/api/chat", request, String.class);
        final String content =
            response == null ? "" : mapper.readTree(response).path("message").path("content").asText("");
        if (content.isBlank()) throw new IOException("Empty answer from " + url);
        return mapper.readTree(content);
      } catch (final RestClientException | IOException e) {
        log.warn("Ollama at {} failed: {}", url, e.getMessage());
        failure = e instanceof IOException io ? io : new IOException(e.getMessage(), e);
      }
    }
    throw failure == null ? new IOException("No Ollama servers configured") : failure;
  }
}
