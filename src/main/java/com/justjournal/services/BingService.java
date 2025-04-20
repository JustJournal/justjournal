package com.justjournal.services;

import lombok.extern.slf4j.Slf4j;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.errors.EncodingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BingService {

    @Value("${bing.indexNowKey}")
    private String indexNowKey;

    @Value("${bing.host}")
    private String host;

    @Value("${bing.keyLocation}")
    private String keyLocation;

    private final RestTemplate restTemplate;

    public BingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean indexNow(List<String> permalinks) {
        try {
            List<String> cleanUrls = permalinks.stream()
                    .map(url -> {
                        try {
                            return ESAPI.encoder().encodeForURL(url);
                        } catch (EncodingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("host", host);
            requestBody.put("key", indexNowKey);
            requestBody.put("keyLocation", keyLocation);
            requestBody.put("urlList", cleanUrls);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> result = restTemplate.postForEntity(
                    "https://api.indexnow.org/IndexNow",
                    request,
                    String.class
            );

            return result.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to perform IndexNow API request", e);
            return false;
        }
    }

    // Overloaded method for single URL indexing
    public boolean indexNow(String permalink) {
        return indexNow(Collections.singletonList(permalink));
    }
}