package com.justjournal.services;

import com.justjournal.core.Settings;
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

    @Mock
    private Settings settings;

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

    @Test
    void send_shouldReturnFalse_whenThePingReportsAnErrorOrFails() throws Exception {
        when(encoder.encodeForURL(anyString())).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("<response><error>1</error></response>", HttpStatus.OK));
        assertFalse(trackbackService.send("http://pingurl.com", "Blog", "http://permalink.com", "Title", "Excerpt"));

        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenThrow(new IllegalStateException("down"));
        assertFalse(trackbackService.send("http://pingurl.com", "Blog", "http://permalink.com", "Title", "Excerpt"));

        assertFalse(trackbackService.send("http://ping url with spaces", "Blog", "x", "Title", "Excerpt"));
    }

    @Test
    void sendForBlog_pingsTheTrackbackUrlFoundInThePage() throws Exception {
        when(settings.getBaseUri()).thenReturn("https://www.example.com/");
        when(encoder.encodeForURL(anyString())).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.getForEntity("http://blog.example.com/post", String.class))
                .thenReturn(new ResponseEntity<>(
                        "<html><rdf:RDF><rdf:Description trackback:ping=\"http://blog.example.com/tb/1\" /></rdf:RDF></html>",
                        HttpStatus.OK));
        when(restTemplate.postForEntity(any(URI.class), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("<response><error>0</error></response>", HttpStatus.OK));

        trackbackService.sendForBlog("http://blog.example.com/post", 42, "ada", "Journal", "Subject", "Body");

        verify(restTemplate).postForEntity(
                eq(URI.create("http://blog.example.com/tb/1?title=Subject&url=https://www.example.com/users/ada/entry/42&blog_name=Journal&excerpt=Body")),
                any(), eq(String.class));
    }

    @Test
    void sendForBlog_doesNothingWithoutAUrl_aPage_orATrackbackLink() {
        trackbackService.sendForBlog("  ", 42, "ada", "Journal", "Subject", "Body");
        verifyNoInteractions(restTemplate);

        when(restTemplate.getForEntity("http://blog.example.com/missing", String.class))
                .thenReturn(new ResponseEntity<>(HttpStatus.NOT_FOUND));
        trackbackService.sendForBlog("http://blog.example.com/missing", 42, "ada", "Journal", "Subject", "Body");

        when(restTemplate.getForEntity("http://blog.example.com/plain", String.class))
                .thenReturn(new ResponseEntity<>("<html><body>no trackback here</body></html>", HttpStatus.OK));
        trackbackService.sendForBlog("http://blog.example.com/plain", 42, "ada", "Journal", "Subject", "Body");

        when(restTemplate.getForEntity("http://blog.example.com/down", String.class))
                .thenThrow(new IllegalStateException("down"));
        trackbackService.sendForBlog("http://blog.example.com/down", 42, "ada", "Journal", "Subject", "Body");

        verify(restTemplate, never()).postForEntity(any(URI.class), any(), eq(String.class));
    }

    @Test
    void getHtmlDocument_returnsTheBodyOnlyForOk() {
        when(restTemplate.getForEntity("http://a", String.class)).thenReturn(new ResponseEntity<>("<html/>", HttpStatus.OK));
        when(restTemplate.getForEntity("http://b", String.class)).thenReturn(new ResponseEntity<>("<html/>", HttpStatus.MOVED_PERMANENTLY));

        assertEquals(Optional.of("<html/>"), trackbackService.getHtmlDocument("http://a"));
        assertTrue(trackbackService.getHtmlDocument("http://b").isEmpty());
    }

    @Test
    void parseTrackbackUrl_fallsBackToTheLinkTag_whenThereIsNoRdfPing() {
        String input = "<html><head><link rel=\"trackback\""
                + " type=\"application/x-www-form-urlencoded\""
                + " href=\"http://example.org/trackback-url\" /></head></html>";

        assertTrue(trackbackService.parseTrackbackUrlWithRdf(input).isEmpty());
        assertEquals(Optional.of("http://example.org/trackback-url"),
                trackbackService.parseTrackbackUrlWithLinkTag(input));
        assertEquals(Optional.of("http://example.org/trackback-url"),
                trackbackService.parseTrackbackUrl(input));
    }

    @Test
    void parseTrackbackUrlWithLinkTag_isEmptyWithoutALinkTag() {
        assertTrue(trackbackService.parseTrackbackUrlWithLinkTag("<html/>").isEmpty());
        assertTrue(trackbackService.parseTrackbackUrl("<html><body>no trackback here</body></html>")
                .isEmpty());
    }

    @Test
    void parseTrackbackUrlWithLinkTag_skipsOtherLinks_andAcceptsAnyAttributeOrder() {
        String input = "<html><head>"
                + "<link rel=\"stylesheet\" href=\"http://example.org/style.css\" />\n"
                + "<LINK href='http://example.org/tb' type=\"application/x-www-form-urlencoded\" rel='trackback'>"
                + "</head></html>";

        assertEquals(Optional.of("http://example.org/tb"), trackbackService.parseTrackbackUrlWithLinkTag(input));
        assertTrue(trackbackService.parseTrackbackUrlWithLinkTag(
                "<link rel=\"stylesheet\" href=\"http://example.org/style.css\" />").isEmpty());
    }

    @Test
    void getHtmlDocument_capsTheScannedPage() {
        String big = "x".repeat(TrackbackService.MAX_HTML_LENGTH + 10);
        when(restTemplate.getForEntity("http://big", String.class)).thenReturn(new ResponseEntity<>(big, HttpStatus.OK));

        assertEquals(TrackbackService.MAX_HTML_LENGTH,
                trackbackService.getHtmlDocument("http://big").orElseThrow().length());
    }

    @Test
    void save_storesANewTrackback_withTodaysDate() {
        when(trackbackRepository.existsByEntryIdAndUrl(100, "http://example.com")).thenReturn(false);
        when(trackbackRepository.save(testTrackback)).thenReturn(testTrackback);
        testTrackback.setBlogName("Blog");
        testTrackback.setAuthorName("Ada");
        testTrackback.setAuthorEmail("ada@example.com");
        testTrackback.setBody("excerpt");
        testTrackback.setType(com.justjournal.model.TrackbackType.trackback);

        TrackbackTo result = trackbackService.save(testTrackback);

        assertEquals(1, result.getId());
        assertEquals(100, result.getEntryId());
        assertEquals("http://example.com", result.getUrl());
        assertEquals("Test Trackback", result.getSubject());
        assertEquals("Blog", result.getBlogName());
        assertEquals("Ada", result.getAuthorName());
        assertEquals("ada@example.com", result.getAuthorEmail());
        assertEquals("excerpt", result.getBody());
        assertEquals(com.justjournal.model.TrackbackType.trackback, result.getType());
        assertTrue(System.currentTimeMillis() - testTrackback.getDate().getTime() < 60_000);
    }

    @Test
    void save_needsAnEntryId() {
        Trackback orphan = new Trackback();
        orphan.setUrl("http://example.com");
        when(trackbackRepository.existsByEntryIdAndUrl(0, "http://example.com")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> trackbackService.save(orphan));
    }

    @Test
    void getById_andDeleteById_useTheRepository() {
        when(trackbackRepository.findById(1)).thenReturn(Optional.of(testTrackback));
        when(trackbackRepository.findById(2)).thenReturn(Optional.empty());

        assertEquals(1, trackbackService.getById(1).get().getId());
        assertTrue(trackbackService.getById(2).isEmpty());

        trackbackService.deleteById(1);
        verify(trackbackRepository).deleteById(1);
    }

    @Test
    void generateResponse_omitsAnEmptyMessage() {
        assertEquals(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><response><error>1</error></response>",
                trackbackService.generateResponse(1, ""));
    }
}
