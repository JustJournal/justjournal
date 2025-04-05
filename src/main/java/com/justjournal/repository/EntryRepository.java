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
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Entry Data Access
 *
 * @author Lucas Holt
 */
@Repository
public interface EntryRepository extends JpaRepository<Entry, Integer> {

  List<Entry> findByUser(@Param("user") User user);

  @Query(
      "select e from Entry e, User u where e.user = u and LOWER(u.username) ="
          + " LOWER(:username)")
  List<Entry> findByUsername(@Param("username") String username);

  @Query(
      "select e from Entry e, User u, EntryTag et, Tag t where e.user = u and"
          + " LOWER(u.username) = LOWER(:username) and t.name = LOWER(:tag) and et.tag = t"
          + " and et.entry = e")
  List<Entry> findByUsernameAndTag(@Param("username") String username, @Param("tag") String tag);

  @Query(
      "select e from Entry e, User u, EntryTag et, Tag t where e.user = u and"
          + " LOWER(u.username) = LOWER(:username) and e.security = :security and t.name ="
          + " LOWER(:tag) and et.tag = t and et.entry = e")
  List<Entry> findByUsernameAndSecurityAndTag(
      @Param("username") String username,
      @Param("security") Security security,
      @Param("tag") String tag);

  @Query(
      "select e from Entry e, User u, Friend f where e.user = f.friend and"
          + " LOWER(u.username) = LOWER(:username) and e.security = com.justjournal.model.Security.PUBLIC"
          + " and f.user = u and e.draft = :draft")
  Page<Entry> findByUserFriends(
      @Param("username") String username, @Param("draft") PrefBool draft, Pageable pageable);

  @Query(
      "select e from Entry e, User u where e.user = u and LOWER(u.username) ="
          + " LOWER(:username) and e.security = :security")
  List<Entry> findByUsernameAndSecurity(
      @Param("username") String username, @Param("security") Security security);

  List<Entry> findByUserAndSecurityOrderByDateDesc(
      @Param("user") User user, @Param("security") Security security);

  List<Entry> findByUserAndSecurityAndDraftOrderByDateDesc(
      @Param("user") User user,
      @Param("security") Security security,
      @Param("draft") PrefBool draft);

  Page<Entry> findByUserAndSecurityOrderByDateDesc(
      @Param("user") User user, @Param("security") Security security, Pageable pageable);

  Page<Entry> findByUserAndSecurityAndDraft(
      @Param("user") User user,
      @Param("security") Security security,
      @Param("draft") PrefBool draft,
      Pageable pageable);

  @Query(
      "select new Entry(e.id, e.subject) from Entry e, User u where e.user = u and"
          + " LOWER(u.username) = LOWER(:username) and e.security = :security and e.draft ="
          + " :draft")
  Page<Entry> findByUserAndSecurityAndDraftWithSubjectOnly(
      @Param("username") String username,
      @Param("security") Security security,
      @Param("draft") PrefBool draft,
      Pageable pageable);

  Page<Entry> findByUserOrderByDateDesc(User user, Pageable pageable);

  Page<Entry> findBySecurityOrderByDateDesc(Security security, Pageable pageable);

  @Query(
      "select e from Entry e, User u where e.user= u and LOWER(u.username) ="
          + " LOWER(:username) and e.date >= :startDate and e.date <= :endDate")
  List<Entry> findByUsernameAndDate(
      @Param("username") String username,
      @Param("startDate") Date startDate,
      @Param("endDate") Date endDate);

  @Query(
      "SELECT count(eh) FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username)"
          + " AND YEAR(eh.date)= :yr AND us = eh.user")
  Long calendarCount(@Param("yr") int year, @Param("username") String username);

  @Query(
      "SELECT count(eh) FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username)"
          + " AND YEAR(eh.date)= :yr AND MONTH(eh.date)= :mn AND us = eh.user")
  Long calendarCount(
      @Param("yr") int year, @Param("mn") int month, @Param("username") String username);

  @Query(
      "SELECT eh FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username) AND"
          + " YEAR(eh.date) = :yr AND us = eh.user and eh.security = :security")
  List<Entry> findByUsernameAndYearAndSecurity(
      @Param("username") String username,
      @Param("yr") int year,
      @Param("security") Security security);

  @Query(
      "SELECT eh FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username) AND"
          + " YEAR(eh.date) = :yr AND us = eh.user")
  List<Entry> findByUsernameAndYear(@Param("username") String username, @Param("yr") int year);

  @Query(
      "SELECT eh FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username) AND"
          + " YEAR(eh.date) = :yr AND MONTH(eh.date) = :month AND us = eh.user")
  List<Entry> findByUsernameAndYearAndMonth(
      @Param("username") String username, @Param("yr") int year, @Param("month") int month);

  @Query(
      "SELECT eh FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username) AND"
          + " YEAR(eh.date) = :yr AND MONTH(eh.date) = :month AND us = eh.user and"
          + " eh.security = :security")
  List<Entry> findByUsernameAndYearAndMonthAndSecurity(
      @Param("username") String username,
      @Param("yr") int year,
      @Param("month") int month,
      @Param("security") Security security);

  @Query(
      "SELECT eh FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username) AND"
          + " YEAR(eh.date) = :yr AND MONTH(eh.date) = :month AND DAY(eh.date) = :day AND us"
          + " = eh.user and eh.security = :security")
  List<Entry> findByUsernameAndYearAndMonthAndDay(
      @Param("username") String username,
      @Param("yr") int year,
      @Param("month") int month,
      @Param("day") int day);

  @Query(
      "SELECT eh FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username) AND"
          + " YEAR(eh.date) = :yr AND MONTH(eh.date) = :month AND DAY(eh.date) = :day AND us"
          + " = eh.user and eh.security = :security")
  List<Entry> findByUsernameAndYearAndMonthAndDayAndSecurity(
      @Param("username") String username,
      @Param("yr") int year,
      @Param("month") int month,
      @Param("day") int day,
      @Param("security") Security security);

  Long countBySecurity(@Param("security") Security security);

  @Query(
      "SELECT count(eh) FROM User us, Entry eh WHERE LOWER(us.username) = LOWER(:username)"
          + " AND us = eh.user")
  Long countByUsername(@Param("username") String username);
}
