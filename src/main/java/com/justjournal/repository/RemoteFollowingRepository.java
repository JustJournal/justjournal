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
import com.justjournal.model.RemoteFollowing;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remote accounts that writers follow. Not exported over Spring Data REST: whom a writer follows
 * is theirs alone.
 *
 * @author Lucas Holt
 */
@Repository
@RepositoryRestResource(exported = false)
public interface RemoteFollowingRepository extends JpaRepository<RemoteFollowing, Integer> {

  List<RemoteFollowing> findByUserIdOrderByIdAsc(int userId);

  Optional<RemoteFollowing> findByUserIdAndActorId(int userId, String actorId);

  Optional<RemoteFollowing> findByUserIdAndFollowId(int userId, String followId);

  long countByUserId(int userId);

  long countByUserIdAndState(int userId, FollowingState state);

  boolean existsByActorId(String actorId);

  boolean existsByActorIdAndState(String actorId, FollowingState state);

  @Transactional
  long deleteByUserIdAndActorId(int userId, String actorId);

  @Transactional
  long deleteByActorId(String actorId);

  @Transactional
  long deleteByUserId(int userId);
}
