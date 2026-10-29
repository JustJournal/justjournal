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
package com.justjournal.repository;


import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** @author Lucas Holt */
@Repository
public interface EntryTagsRepository extends JpaRepository<EntryTag, Integer> {

  @Query(
      "select et from EntryTag et, Entry e, User u where et.entry = e and e.user = u and"
          + " LOWER(u.username) = LOWER(:username)")
  List<EntryTag> findByUsername(@Param("username") String username);

  List<EntryTag> findByTag(@Param("tag") Tag tag);

  List<EntryTag> findByEntry(@Param("entry") Entry entry);

  EntryTag findByEntryAndTag(@Param("entry") Entry entry, @Param("tag") Tag tag);

  long countByTag(@Param("tag") Tag tag);

  /**
   * Delete every link to a tag without loading the links. Loading them also loads their entries,
   * whose cascading tag sets would still hold the deleted links at the next flush.
   *
   * @param tag tag to unlink
   * @return number of links deleted
   */
  @Modifying
  @Query("delete from EntryTag et where et.tag = :tag")
  int deleteByTag(@Param("tag") Tag tag);
}
