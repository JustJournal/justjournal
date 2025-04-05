package com.justjournal.ctl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.core.Settings;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.UserRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/.well-known/webfinger")
public class WebFingerController {

    final
    ObjectMapper mapper;

    final
    Settings settings;

    private final UserRepository userRepository;

    private final EntryRepository entryRepository;

    String baseUri;

    public WebFingerController(ObjectMapper mapper, Settings settings, UserRepository userRepository, EntryRepository entryRepository) {
        this.mapper = mapper;
        this.settings = settings;
        this.userRepository = userRepository;
        this.entryRepository = entryRepository;
    }

    @PostConstruct
    public void url() {
        baseUri = settings.getBaseUri();
        baseUri = baseUri.replace("http://", "https://");
    }

    @GetMapping(produces = "application/jrd+json")
    public String get(@RequestParam("resource") String resource, HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException {
        var webFingerResponse = new WebFingerResponse();
        webFingerResponse.setSubject(resource);

        final Pattern pattern = Pattern.compile("acct:([A-Za-z0-9]+)@justjournal\\.com", Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(resource);
        if (matcher.find()) {
            String account = matcher.group(1);

            var user = userRepository.findByUsername(account);
            if (user != null) {

                user.getJournals().stream().findFirst().ifPresent(journal -> {
                    if (!journal.isOwnerViewOnly()) {
                        webFingerResponse.setAliases(Collections.singletonList(baseUri + "users/" + account));
                        var links = new ArrayList<Links>();
                        links.add(new Links("http://webfinger.net/rel/profile-page", baseUri + "#!/profile/" + account));
                        links.get(0).setType("text/html");

                        links.add(new Links("self",baseUri + "users/" + account));
                        links.get(1).setType("application/activity+json");

                        if (user.getUserPref().getShowAvatar().equals(PrefBool.Y)) {
                            links.add(new Links("http://webfinger.net/rel/avatar", baseUri + "Avatar/" + user.getId()));
                        }
                        webFingerResponse.setLinks(links);
                        if (user.getFirstName() != null && user.getLastName() != null)
                            webFingerResponse.setProperties(Collections.singletonMap(baseUri + "ns/name", user.getFirstName() + " " + user.getLastName()));
                    }
                });
            }
        } else if (resource.startsWith(baseUri)) {
            // Compile regular expression
            final Pattern pattern1 = Pattern.compile("https://www\\.justjournal\\.com/users/([A-Za-z0-9]+)/entry/([0-9]+)", Pattern.CASE_INSENSITIVE);
            final Matcher matcher1 = pattern1.matcher(resource);
            if (matcher.find()) {
                Integer entryId = Integer.parseInt(matcher1.group(2));
                var entry = entryRepository.findById(entryId);
                if (entry.isPresent()&& entry.get().getSecurity() == Security.PUBLIC) {
                    var links = new ArrayList<Links>();
                    links.add(new Links("author", baseUri + "users/" + entry.get().getUser().getUsername(), Collections.singletonMap("en-us", entry.get().getUser().getJournals().stream().findFirst().get().getName())));
                    webFingerResponse.setLinks(links);
                }
            }
        }

        return mapper.writeValueAsString(webFingerResponse);
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
