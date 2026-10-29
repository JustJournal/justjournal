package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class BingServiceTests {

  private static BingService service(final RestTemplate restTemplate, final String key, final String keyLocation) {
    final BingService service = new BingService(restTemplate);
    ReflectionTestUtils.setField(service, "indexNowKey", key);
    ReflectionTestUtils.setField(service, "host", "www.justjournal.com");
    ReflectionTestUtils.setField(service, "keyLocation", keyLocation);
    return service;
  }

  @Test
  void skipsSubmissionWithoutKey() {
    final RestTemplate restTemplate = mock(RestTemplate.class);

    assertFalse(service(restTemplate, "", "").indexNow("https://www.justjournal.com/users/a/entry/1"));

    verifyNoInteractions(restTemplate);
  }

  @Test
  void submitsWhenKeyConfigured() {
    final RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("ok"));

    assertTrue(
        service(restTemplate, "abc123", "https://www.justjournal.com/abc123.txt")
            .indexNow("https://www.justjournal.com/users/a/entry/1"));
  }
}
