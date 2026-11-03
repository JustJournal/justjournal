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
package com.justjournal.repository;

import com.justjournal.model.EntryImage;
import com.justjournal.model.ModerationStatus;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

/**
 * Pictures attached to blog posts. Not exported over Spring Data REST: it would show pictures on
 * private posts and those held for review.
 *
 * @author Lucas Holt
 */
@Repository
@RepositoryRestResource(exported = false)
public interface EntryImageRepository extends JpaRepository<EntryImage, Integer> {

  /** The picture on a post; a post has at most one. */
  Optional<EntryImage> findByEntryId(Integer entryId);

  /** The pictures on a page of posts. */
  List<EntryImage> findByEntryIdIn(Collection<Integer> entryIds);

  List<EntryImage> findByOwnerId(int ownerId);

  /** Pictures the user uploaded that no saved post has taken yet. */
  long countByOwnerIdAndEntryIdIsNull(int ownerId);

  /** Uploads never attached to a post, or left over when their post was deleted. */
  List<EntryImage> findByEntryIdIsNullAndCreatedBefore(Date before, Pageable page);

  /** Pictures with a given verdict and reason, oldest first; see {@code ModerationRetry}. */
  List<EntryImage> findByModerationStatusAndModerationCategoryOrderByIdAsc(
      ModerationStatus moderationStatus, String moderationCategory, Pageable page);

  /** Pictures with a given verdict, oldest first; see {@code AdminModerationService}. */
  List<EntryImage> findByModerationStatusOrderByIdAsc(
      ModerationStatus moderationStatus, Pageable page);
}
