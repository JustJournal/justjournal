package com.justjournal.ctl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/@")
public class ActivityStreamController {
    private static final String PATH_USERNAME = "username";

    final ObjectMapper mapper;

    public ActivityStreamController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping(value = "{username}", produces = "application/activity+json")
    public String get(@PathVariable(PATH_USERNAME) final String username, HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException {
        var activityStreamResponse = new ActivityStreamResponse();

        return mapper.writeValueAsString(activityStreamResponse);
    }

    @Getter
    @Setter
    class ActivityStreamResponse {

        @JsonProperty("@context")
        private List<String> context;

        @JsonCreator
        public ActivityStreamResponse() {
            this.context = new ArrayList<>();
            context.add("https://www.w3.org/ns/activitystreams");
            context.add("https://w3id.org/security/v1");
        }

    }
}