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

import com.justjournal.model.EntryLink;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.model.ModerationStatus;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links attached to blog posts and their previews. Not exported over Spring Data REST: it would
 * show links on private posts.
 *
 * @author Lucas Holt
 */
@Repository
@RepositoryRestResource(exported = false)
public interface EntryLinkRepository extends JpaRepository<EntryLink, Integer> {

  /**
   * The link on a post. A post has one; the column isn't unique, so a replaced link that hasn't
   * been deleted yet can show up here too.
   */
  List<EntryLink> findByEntryId(Integer entryId);

  /** The links on a page of posts. */
  List<EntryLink> findByEntryIdIn(Collection<Integer> entryIds);

  List<EntryLink> findByOwnerId(int ownerId);

  /** Links whose post was deleted. */
  List<EntryLink> findByEntryIdIsNull(Pageable page);

  /** Preview images with a given verdict and reason, oldest first; see {@code ModerationRetry}. */
  List<EntryLink> findByImageModerationStatusAndImageModerationCategoryOrderByIdAsc(
      ModerationStatus imageModerationStatus, String imageModerationCategory, Pageable page);

  /** Previews due to be fetched, oldest first. */
  List<EntryLink> findByPreviewStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
      LinkPreviewStatus previewStatus, Date now, Pageable page);

  /**
   * Claim a due preview for one fetch by moving its next attempt to the end of a lease, so another
   * run doesn't fetch it at the same time.
   *
   * @return 1 if this caller has the row, 0 if it is no longer due or no longer pending
   */
  @Transactional
  @Modifying
  @Query(
      "update EntryLink l set l.nextAttemptAt = :lease where l.id = :id"
          + " and l.previewStatus = com.justjournal.model.LinkPreviewStatus.PENDING"
          + " and l.nextAttemptAt <= :now")
  int claim(@Param("id") int id, @Param("now") Date now, @Param("lease") Date lease);
}
