package com.justjournal.services;

import lombok.extern.slf4j.Slf4j;
import org.owasp.esapi.ESAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Slf4j
@Service
public class BingService {

    @Value("${bing.indexNowKey}")
    private String indexNowKey;

    private final RestTemplate restTemplate;

    public BingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean indexNow(String permalink) {
        try {
            final String cleanPermanentBlogEntryUrl = ESAPI.encoder().encodeForURL(permalink);

            final URI uri =
                    new URI(
                            "https://www.bing.com/indexnow?url="
                                    + cleanPermanentBlogEntryUrl
                                    + "&key="
                                    + indexNowKey);

            ResponseEntity<String> result = restTemplate.getForEntity(uri, String.class);

            return result.getStatusCode().is2xxSuccessful();
        } catch (final Exception me) {
            log.error("Failed to perform bing index now", me);
            return false;
        }
    }
}
