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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.LOGIN_ATTRID;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.ForbiddenException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.FollowingState;
import com.justjournal.model.RemoteFollowing;
import com.justjournal.model.User;
import com.justjournal.repository.UserRepository;
import com.justjournal.services.activitypub.FederationPolicy;
import com.justjournal.services.activitypub.FollowingService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fediverse accounts the logged in writer follows ({@link FollowingService}). Following is
 * refused while federation is off for the site or the writer's journal doesn't federate; the list
 * is then empty. Unfollowing always works.
 *
 * @author Lucas Holt
 */
@RestController
@RequestMapping("/api/federation/following")
public class FollowingController {

  /** What to follow: {@code @user@host} or the address of the account's profile. */
  public record FollowRequest(String account) {}

  /** One followed account, as the writer sees it. */
  public record Following(
      String actorId,
      String handle,
      String displayName,
      String profileUrl,
      FollowingState state,
      Date created) {}

  private final UserRepository userRepository;
  private final FederationPolicy federationPolicy;
  private final FollowingService followingService;

  public FollowingController(
      final UserRepository userRepository,
      final FederationPolicy federationPolicy,
      final FollowingService followingService) {
    this.userRepository = userRepository;
    this.federationPolicy = federationPolicy;
    this.followingService = followingService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Following>> list(final HttpSession session) {
    final User user = user(session);
    if (!federationPolicy.isEnabled()) return ResponseEntity.ok(List.of());
    return ResponseEntity.ok(
        followingService.list(user).stream().map(FollowingController::view).toList());
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Following> follow(
      @RequestBody final FollowRequest request, final HttpSession session) {
    final User user = user(session);
    if (!federationPolicy.isEnabled()) {
      throw new BadRequestException("Following fediverse accounts is not available on this site.");
    }
    if (request == null || request.account() == null || request.account().isBlank()) {
      throw new BadRequestException("Enter the account to follow.");
    }
    try {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(view(followingService.follow(user, request.account())));
    } catch (final FollowingService.FollowRefusedException e) {
      throw new BadRequestException(e.getMessage());
    } catch (final IOException e) {
      throw new BadRequestException("That server could not be reached. Try again later.");
    }
  }

  @DeleteMapping
  public ResponseEntity<Void> unfollow(
      @RequestParam("actor") final String actorId, final HttpSession session) {
    final User user = user(session);
    if (!followingService.unfollow(user, actorId)) {
      throw new NotFoundException("You don't follow that account.");
    }
    return ResponseEntity.noContent().build();
  }

  private User user(final HttpSession session) {
    final Integer userId = (Integer) session.getAttribute(LOGIN_ATTRID);
    if (userId == null || userId < 1) {
      throw new ForbiddenException("You must be logged in to follow fediverse accounts");
    }
    return userRepository.findById(userId).orElseThrow(NotFoundException::new);
  }

  private static Following view(final RemoteFollowing following) {
    return new Following(
        following.getActorId(),
        following.getHandle(),
        following.getDisplayName(),
        following.getProfileUrl(),
        following.getState(),
        following.getCreated());
  }
}
