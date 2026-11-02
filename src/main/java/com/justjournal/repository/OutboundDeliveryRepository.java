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

import com.justjournal.model.DeliveryStatus;
import com.justjournal.model.OutboundDelivery;
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
 * Activities queued for other servers' inboxes. Not exported over Spring Data REST: it holds who
 * follows whom and copies of entries.
 *
 * @author Lucas Holt
 */
@Repository
@RepositoryRestResource(exported = false)
public interface OutboundDeliveryRepository extends JpaRepository<OutboundDelivery, Integer> {

  /** Deliveries due to be tried, oldest first. */
  List<OutboundDelivery> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
      DeliveryStatus status, Date now, Pageable page);

  /**
   * Claim a due delivery for one attempt by moving its next attempt to the end of a lease, so
   * another run or server doesn't post it at the same time.
   *
   * @return 1 if this caller has the row, 0 if it is no longer due or no longer pending
   */
  @Transactional
  @Modifying
  @Query(
      "update OutboundDelivery d set d.nextAttemptAt = :lease where d.id = :id"
          + " and d.status = com.justjournal.model.DeliveryStatus.PENDING"
          + " and d.nextAttemptAt <= :now")
  int claim(@Param("id") int id, @Param("now") Date now, @Param("lease") Date lease);

  /**
   * Record how an attempt went. An update rather than a save, so a row deleted meanwhile (the
   * account was deleted) stays deleted.
   *
   * @return 1 if the row still exists
   */
  @Transactional
  @Modifying
  @Query(
      "update OutboundDelivery d set d.status = :status, d.attempts = :attempts,"
          + " d.nextAttemptAt = :next, d.lastError = :error, d.finishedAt = :finished"
          + " where d.id = :id")
  int record(
      @Param("id") int id,
      @Param("status") DeliveryStatus status,
      @Param("attempts") int attempts,
      @Param("next") Date next,
      @Param("error") String error,
      @Param("finished") Date finished);

  /** Remove finished deliveries that ended before a cut off. */
  @Transactional
  @Modifying
  @Query("delete from OutboundDelivery d where d.status = :status and d.finishedAt < :before")
  int deleteFinished(@Param("status") DeliveryStatus status, @Param("before") Date before);
}
