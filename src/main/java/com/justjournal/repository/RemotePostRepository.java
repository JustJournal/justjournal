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

import com.justjournal.model.FollowingState;
import com.justjournal.model.RemotePost;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public posts by followed fediverse accounts. Not exported over Spring Data REST: they are shown
 * only to the writers following their authors.
 *
 * @author Lucas Holt
 */
@Repository
@RepositoryRestResource(exported = false)
public interface RemotePostRepository extends JpaRepository<RemotePost, Integer> {

  Optional<RemotePost> findByObjectId(String objectId);

  /** Posts by the accounts a writer follows in the given state, newest first. */
  @Query(
      "select p from RemotePost p where p.actorId in (select f.actorId from RemoteFollowing f"
          + " where f.userId = :userId and f.state = :state)"
          + " order by p.published desc, p.id desc")
  List<RemotePost> findFollowedBy(
      @Param("userId") int userId, @Param("state") FollowingState state, Pageable page);

  @Transactional
  long deleteByObjectId(String objectId);

  @Transactional
  long deleteByActorId(String actorId);

  /** Remove posts published before a cut off. */
  @Transactional
  @Modifying
  @Query("delete from RemotePost p where p.published < :before")
  int deletePublishedBefore(@Param("before") Date before);

  /** Remove posts whose author nobody here follows in the given state any more. */
  @Transactional
  @Modifying
  @Query(
      "delete from RemotePost p where p.actorId not in (select f.actorId from RemoteFollowing f"
          + " where f.state = :state)")
  int deleteUnfollowed(@Param("state") FollowingState state);
}
