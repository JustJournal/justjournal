package com.justjournal.ctl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.Journal;
import com.justjournal.model.PrefBool;
import com.justjournal.model.User;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.EntryAccessService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 7033 WebFinger for accounts (acct:user@host) and public entries. Resources we have nothing
 * to say about get a 404.
 */
@Slf4j
@RestController
@RequestMapping("/.well-known/webfinger")
public class WebFingerController {

    static final MediaType JRD_JSON = MediaType.parseMediaType("application/jrd+json");

    private static final String USERS_REL = "users/";

    private static final Pattern ACCOUNT = Pattern.compile("acct:([^@]+)@([^@]+)", Pattern.CASE_INSENSITIVE);

    /** An entry page, relative to the base URI. */
    private static final Pattern ENTRY = Pattern.compile("users/([^/]+)/entry/(\\d+)", Pattern.CASE_INSENSITIVE);

    final
    ObjectMapper mapper;

    final
    Settings settings;

    private final UserRepository userRepository;

    private final EntryRepository entryRepository;

    private final EntryAccessService entryAccessService;

    String baseUri;

    /** The host in acct: URIs: the site's host without a leading www. */
    String accountHost;

    public WebFingerController(ObjectMapper mapper, Settings settings, UserRepository userRepository,
                               EntryRepository entryRepository, EntryAccessService entryAccessService) {
        this.mapper = mapper;
        this.settings = settings;
        this.userRepository = userRepository;
        this.entryRepository = entryRepository;
        this.entryAccessService = entryAccessService;
    }

    @PostConstruct
    public void url() {
        baseUri = settings.getBaseUri();
        baseUri = baseUri.replace("http://", "https://");
        final String host = URI.create(baseUri).getHost().toLowerCase(Locale.ROOT);
        accountHost = host.startsWith("www.") ? host.substring(4) : host;
    }

    /**
     * The JRD is sent as application/jrd+json even to clients that only accept application/json,
     * as RFC 7033 section 4.2 asks.
     */
    @GetMapping(produces = {"application/jrd+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> get(@RequestParam("resource") String resource) throws JsonProcessingException {
        if (resource.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        final Optional<WebFingerResponse> webFingerResponse = account(resource).or(() -> entry(resource));
        if (webFingerResponse.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(JRD_JSON).body(mapper.writeValueAsString(webFingerResponse.get()));
    }

    private Optional<WebFingerResponse> account(final String resource) {
        final Matcher matcher = ACCOUNT.matcher(resource);
        if (!matcher.matches() || !isAccountHost(matcher.group(2)) || !Login.isUserName(matcher.group(1))) {
            return Optional.empty();
        }

        final User user = userRepository.findByUsername(matcher.group(1));
        if (user == null || user.isDisabled() || user.getJournals().isEmpty()
                || user.getJournals().stream().anyMatch(Journal::isOwnerViewOnly)) {
            return Optional.empty();
        }

        final String account = user.getUsername();
        var webFingerResponse = new WebFingerResponse();
        webFingerResponse.setSubject("acct:" + account + "@" + accountHost);
        webFingerResponse.setAliases(Collections.singletonList(baseUri + USERS_REL + account));
        var links = new ArrayList<Links>();
        links.add(new Links("http://webfinger.net/rel/profile-page", baseUri + "profile/" + account));
        links.get(0).setType("text/html");

        links.add(new Links("self", baseUri + USERS_REL + account));
        links.get(1).setType("application/activity+json");

        if (user.getUserPref().getShowAvatar().equals(PrefBool.Y)) {
            links.add(new Links("http://webfinger.net/rel/avatar", baseUri + "Avatar/" + user.getId()));
        }
        webFingerResponse.setLinks(links);
        if (user.getFirstName() != null && user.getLastName() != null)
            webFingerResponse.setProperties(Collections.singletonMap(baseUri + "ns/name", user.getFirstName() + " " + user.getLastName()));
        return Optional.of(webFingerResponse);
    }

    private boolean isAccountHost(final String host) {
        return host.equalsIgnoreCase(accountHost) || host.equalsIgnoreCase("www." + accountHost);
    }

    private Optional<WebFingerResponse> entry(final String resource) {
        if (!resource.regionMatches(true, 0, baseUri, 0, baseUri.length())) {
            return Optional.empty();
        }
        final Matcher matcher = ENTRY.matcher(resource.substring(baseUri.length()));
        if (!matcher.matches()) {
            return Optional.empty();
        }

        final int entryId;
        try {
            entryId = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        final Entry entry = entryRepository.findById(entryId).orElse(null);
        if (entry == null || !entryAccessService.isPubliclyVisible(entry)
                || !entry.getUser().getUsername().equalsIgnoreCase(matcher.group(1))) {
            return Optional.empty();
        }

        final User user = entry.getUser();
        final String author = baseUri + USERS_REL + user.getUsername();
        var webFingerResponse = new WebFingerResponse();
        webFingerResponse.setSubject(author + "/entry/" + entry.getId());
        var links = new ArrayList<Links>();
        links.add(user.getJournals().stream().findFirst()
                .map(journal -> new Links("author", author, Collections.singletonMap("en-us", journal.getName())))
                .orElseGet(() -> new Links("author", author)));
        webFingerResponse.setLinks(links);
        return Optional.of(webFingerResponse);
    }

    @Getter
    @Setter
    class WebFingerResponse {
        String subject;
        List<String> aliases = new ArrayList<>();

        Map<String,String> properties = new HashMap<>();

        List<Links> links = new ArrayList<>();
    }

    @Getter
    @Setter
    class Links {
        String rel;
        String href;
        String type;
        Map<String,String> titles = new HashMap<>();
        Map<String,String> properties = new HashMap<>();

        Links(String rel, String href) {
            this.rel = rel;
            this.href = href;
        }

        Links(String rel, String href, Map<String,String> titles) {
            this.rel = rel;
            this.href = href;
            this.titles = titles;
        }
        Links(String rel, String href, Map<String,String> titles, Map<String,String> properties) {
            this.rel = rel;
            this.href = href;
            this.titles = titles;
            this.properties = properties;
        }

        @JsonCreator
        Links() {
            this.rel = null;
            this.href = null;
        }
    }
}
