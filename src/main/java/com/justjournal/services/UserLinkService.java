/*
 * Copyright (c) 2003-2021 Lucas Holt
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
package com.justjournal.services;


import com.justjournal.model.User;
import com.justjournal.model.UserLink;
import com.justjournal.model.api.UserLinkTo;
import com.justjournal.repository.UserLinkRepository;
import com.justjournal.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @author Lucas Holt */
@Service
public class UserLinkService {
  private static final String DUPLICATE_LINK = "You already have a link with that title and URL.";

  private final UserLinkRepository userLinkRepository;

  private final UserRepository userRepository;

  private final UserLinkValidator userLinkValidator;

  public UserLinkService(
      final UserLinkRepository userLinkRepository,
      final UserRepository userRepository,
      final UserLinkValidator userLinkValidator) {
    this.userLinkRepository = userLinkRepository;
    this.userRepository = userRepository;
    this.userLinkValidator = userLinkValidator;
  }

  public Optional<UserLinkTo> get(final int linkId) {
    return userLinkRepository.findById(linkId).map(this::transform);
  }

  public List<UserLinkTo> getByUser(@NonNull final String username) {
    return userLinkRepository.findByUsernameOrderByTitleTitleAsc(username).stream()
        .map(this::transform)
        .collect(Collectors.toList());
  }

  public void delete(final int linkId) {
    userLinkRepository.deleteById(linkId);
  }

  /**
   * Create a new link for the user identified by link.getUserId(). Any id on the incoming link is
   * ignored so an existing link can't be overwritten.
   *
   * @throws IllegalArgumentException if the link is not valid or is a duplicate
   */
  @Transactional
  public UserLinkTo create(final UserLinkTo link) {
    validate(link);

    final UserLinkTo newLink =
        UserLinkTo.builder()
            .title(link.getTitle().trim())
            .uri(link.getUri().trim())
            .userId(link.getUserId())
            .build();
    return transform(save(newLink));
  }

  /**
   * Update the title and uri of an existing link owned by userId.
   *
   * @return the updated link, or empty if the link doesn't exist or belongs to another user
   * @throws IllegalArgumentException if the link is not valid or is a duplicate
   */
  @Transactional
  public Optional<UserLinkTo> update(final int userId, final int linkId, final UserLinkTo link) {
    final Optional<UserLink> existing = userLinkRepository.findById(linkId);
    if (existing.isEmpty()
        || existing.get().getUser() == null
        || existing.get().getUser().getId() != userId) {
      return Optional.empty();
    }

    validate(link);

    final UserLink userLink = existing.get();
    userLink.setTitle(link.getTitle().trim());
    userLink.setUri(link.getUri().trim());
    try {
      return Optional.of(transform(userLinkRepository.saveAndFlush(userLink)));
    } catch (final DataIntegrityViolationException e) {
      throw new IllegalArgumentException(DUPLICATE_LINK, e);
    }
  }

  private void validate(final UserLinkTo link) {
    userLinkValidator
        .validate(link.getTitle(), link.getUri())
        .ifPresent(
            error -> {
              throw new IllegalArgumentException(error);
            });
  }

  protected UserLink save(final UserLinkTo link) {
    try {
      return userLinkRepository.saveAndFlush(transform(link));
    } catch (final DataIntegrityViolationException e) {
      throw new IllegalArgumentException(DUPLICATE_LINK, e);
    }
  }

  protected UserLinkTo transform(final UserLink userLink) {
    if (userLink == null) return null;

    return UserLinkTo.builder()
        .id(userLink.getId())
        .title(userLink.getTitle())
        .uri(userLink.getUri())
        .userId(userLink.getUser().getId())
        .build();
  }

  protected UserLink transform(final UserLinkTo userLink) {
    final UserLink link =
        UserLink.builder()
            .id(userLink.getId())
            .title(userLink.getTitle())
            .uri(userLink.getUri())
            .build();
    final Optional<User> user = userRepository.findById(userLink.getUserId());
    user.ifPresent(link::setUser);
    return link;
  }
}
