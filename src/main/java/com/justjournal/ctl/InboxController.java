/*
 * Copyright (c) 2003-2026 Lucas Holt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package com.justjournal.ctl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjournal.Login;
import com.justjournal.model.Journal;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.HttpSignatureException;
import com.justjournal.services.activitypub.HttpSignatureService;
import com.justjournal.services.activitypub.InboxService;
import com.justjournal.services.activitypub.SeenActivities;
import com.justjournal.services.activitypub.VerifiedSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The ActivityPub inboxes: each journal's, where other servers send Follows, and the instance
 * actor's, which checks what it is sent and then ignores it. While federation is off for the site
 * both answer 405 with an empty Allow header, so other servers don't retry as they would after a
 * 5xx.
 *
 * <p>A POST must be ActivityStreams JSON of at most {@code activitypub.inbox.max-bytes}, with an
 * HTTP signature by the actor the activity names. An activity seen before (see {@link
 * SeenActivities}) is answered 202 and not acted on again. {@link InboxService} acts on the rest.
 *
 * @author Lucas Holt
 */
@Slf4j
@RestController
public class InboxController {

  private static final Set<String> CONTENT_TYPES =
      Set.of(ActivityStreamController.ACTIVITY_JSON_VALUE, ActivityStreamController.LD_JSON_VALUE);

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  /** Activities that mean nothing without an object. */
  private static final Set<String> NEED_OBJECT = Set.of("Follow", "Undo", "Delete");

  private final UserRepository userRepository;
  private final FederationPolicy federationPolicy;
  private final HttpSignatureService httpSignatureService;
  private final SeenActivities seenActivities;
  private final InboxService inboxService;

  /** Largest activity taken; Mastodon's own limit is about 1 MB, and activities are small. */
  @Setter
  @Value("${activitypub.inbox.max-bytes:262144}")
  private int maxBytes = 262_144;

  public InboxController(
      final UserRepository userRepository,
      final FederationPolicy federationPolicy,
      final HttpSignatureService httpSignatureService,
      final SeenActivities seenActivities,
      final InboxService inboxService) {
    this.userRepository = userRepository;
    this.federationPolicy = federationPolicy;
    this.httpSignatureService = httpSignatureService;
    this.seenActivities = seenActivities;
    this.inboxService = inboxService;
  }

  @RequestMapping("/users/{username}/inbox")
  public ResponseEntity<Void> userInbox(
      @PathVariable("username") final String username, final HttpServletRequest request)
      throws IOException {
    final User user = publicUser(username);
    if (user == null) return ResponseEntity.notFound().build();
    return receive(request, user);
  }

  @RequestMapping(InstanceActorController.PATH + "/inbox")
  public ResponseEntity<Void> instanceInbox(final HttpServletRequest request) throws IOException {
    return receive(request, null);
  }

  /**
   * Take an activity.
   *
   * @param user the journal it was sent to, or null for the instance actor, which acts on nothing
   */
  private ResponseEntity<Void> receive(final HttpServletRequest request, final User user)
      throws IOException {
    if (!federationPolicy.isEnabled()) return methodNotAllowed("");
    if (!HttpMethod.POST.matches(request.getMethod())) return methodNotAllowed("POST");

    if (request.getContentLengthLong() > maxBytes) return status(HttpStatus.PAYLOAD_TOO_LARGE);
    if (!isActivityJson(request.getContentType()))
      return status(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    final byte[] body;
    try (InputStream in = request.getInputStream()) {
      body = in.readNBytes(maxBytes + 1);
    }
    if (body.length > maxBytes) return status(HttpStatus.PAYLOAD_TOO_LARGE);

    final JsonNode activity = parse(body);
    if (activity == null) return status(HttpStatus.BAD_REQUEST);
    final String actor = InboxService.id(activity.get("actor"));

    final VerifiedSignature signature;
    try {
      signature =
          httpSignatureService.verify(
              request.getMethod(), pathAndQuery(request), headers(request), body);
    } catch (final HttpSignatureException e) {
      log.debug(
          "Refused an activity from {} to {}: {} {}",
          actor,
          request.getRequestURI(),
          e.reason(),
          e.getMessage());
      if (user != null
          && e.reason() == HttpSignatureException.Reason.KEY_UNAVAILABLE
          && inboxService.deleteOfGoneActor(activity) == InboxService.Outcome.HANDLED) {
        return status(HttpStatus.ACCEPTED);
      }
      return status(HttpStatus.UNAUTHORIZED);
    }
    if (!signature.actorId().equals(actor)) {
      log.debug("Activity from {} was signed by {}", actor, signature.actorId());
      return status(HttpStatus.UNAUTHORIZED);
    }

    final JsonNode id = activity.get("id");
    final String seenKey =
        id != null && id.isTextual()
            ? "id " + id.asText()
            : "signature " + request.getHeader("Signature");
    if (!seenActivities.firstTime(actor, seenKey)) return status(HttpStatus.ACCEPTED);
    if (user == null) return status(HttpStatus.ACCEPTED);

    final InboxService.Outcome outcome;
    try {
      outcome = inboxService.handle(user, activity, actor);
    } catch (final RuntimeException e) {
      seenActivities.forget(actor, seenKey);
      throw e;
    }
    if (outcome == InboxService.Outcome.RETRY) {
      seenActivities.forget(actor, seenKey);
      return status(HttpStatus.SERVICE_UNAVAILABLE);
    }
    return status(HttpStatus.ACCEPTED);
  }

  /** The activity, or null when it is not a JSON object with a type, an actor and what it needs. */
  private static JsonNode parse(final byte[] body) {
    final JsonNode activity;
    try {
      activity = MAPPER.readTree(body);
    } catch (final IOException e) {
      return null;
    }
    if (activity == null || !activity.isObject()) return null;
    final JsonNode type = activity.get("type");
    if (type == null || !type.isTextual() || type.asText().isBlank()) return null;
    if (InboxService.id(activity.get("actor")) == null) return null;
    if (NEED_OBJECT.contains(type.asText()) && !activity.hasNonNull("object")) return null;
    return activity;
  }

  private static boolean isActivityJson(final String contentType) {
    if (contentType == null) return false;
    try {
      final MediaType type = MediaType.parseMediaType(contentType);
      return CONTENT_TYPES.contains(
          (type.getType() + "/" + type.getSubtype()).toLowerCase(Locale.ROOT));
    } catch (final InvalidMediaTypeException e) {
      return false;
    }
  }

  /** The path and query exactly as sent, which the signature covers. */
  private static String pathAndQuery(final HttpServletRequest request) {
    final String query = request.getQueryString();
    return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
  }

  private static Map<String, List<String>> headers(final HttpServletRequest request) {
    final Map<String, List<String>> headers = new LinkedHashMap<>();
    for (final String name : Collections.list(request.getHeaderNames())) {
      headers.put(name, Collections.list(request.getHeaders(name)));
    }
    return headers;
  }

  /** The user, when anyone may read their journal; the same journals the actor is served for. */
  private User publicUser(final String username) {
    if (!Login.isUserName(username)) return null;
    final User user = userRepository.findByUsername(username);
    if (user == null || user.getJournals().isEmpty()) return null;
    if (user.getJournals().stream().anyMatch(Journal::isOwnerViewOnly)) return null;
    return user;
  }

  private static ResponseEntity<Void> methodNotAllowed(final String allow) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .header(HttpHeaders.ALLOW, allow)
        .build();
  }

  private static ResponseEntity<Void> status(final HttpStatus status) {
    return ResponseEntity.status(status).build();
  }
}
