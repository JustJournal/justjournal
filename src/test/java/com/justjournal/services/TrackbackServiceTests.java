package com.justjournal.services;

import com.justjournal.model.Trackback;
import com.justjournal.model.api.TrackbackTo;
import com.justjournal.repository.TrackbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import org.owasp.esapi.Encoder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class TrackbackServiceTests {

    @Mock
    private TrackbackRepository trackbackRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Encoder encoder;

    @InjectMocks
    private TrackbackService trackbackService;

    private Trackback testTrackback;

    @BeforeEach
    void setUp() {
        testTrackback = new Trackback();
        testTrackback.setId(1);
        testTrackback.setEntryId(100);
        testTrackback.setUrl("http://example.com");
        testTrackback.setSubject("Test Trackback");
    }

    @Test
    void send_shouldReturnTrue_whenPingIsSuccessful() throws Exception {
        String response = "<?xml version=\"1.0\" encoding=\"utf-8\"?><response><error>0</error></response>";
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        when(encoder.encodeForURL(anyString())).thenAnswer(i -> i.getArgument(0));

        boolean result = trackbackService.send("http://pingurl.com", "Blog", "http://permalink.com", "Title", "Excerpt");

        assertTrue(result);
    }

    @Test
    void parseTrackbackUrl_shouldReturnUrl_whenValidRdfInput() {
        String input = "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" " +
                "xmlns:trackback=\"http://madskills.com/public/xml/rss/module/trackback/\">\n" +
                "<rdf:Description rdf:about=\"http://example.org/blog/post/1\" " +
                "trackback:ping=\"http://example.org/trackback-url\" />\n" +
                "</rdf:RDF>";

        Optional<String> result = trackbackService.parseTrackbackUrl(input);

        assertTrue(result.isPresent());
        assertEquals("http://example.org/trackback-url", result.get());
    }

    @Test
    void save_shouldReturnNull_whenTrackbackAlreadyExists() {
        when(trackbackRepository.existsByEntryIdAndUrl(anyInt(), anyString())).thenReturn(true);

        TrackbackTo result = trackbackService.save(testTrackback);

        assertNull(result);
        verify(trackbackRepository, never()).save(any(Trackback.class));
    }

    @Test
    void getByEntry_shouldReturnListOfTrackbacks() {
        when(trackbackRepository.findByEntryIdOrderByDate(anyInt())).thenReturn(Arrays.asList(testTrackback));

        List<TrackbackTo> result = trackbackService.getByEntry(100);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testTrackback.getId(), result.get(0).getId());
    }

    @Test
    void generateResponse_shouldReturnValidXml() {
        String result = trackbackService.generateResponse(0, "Success");

        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"));
        assertTrue(result.contains("<error>0</error>"));
        assertTrue(result.contains("<message>Success</message>"));
    }
}