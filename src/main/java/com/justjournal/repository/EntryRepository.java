/*
 * Copyright (c) 2014 Lucas Holt
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
import com.justjournal.model.Security;
import com.justjournal.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

/**
 * Entry Data Access
 *
 * @author Lucas Holt
 */
public interface EntryRepository extends CrudRepository<Entry, Integer> {

    @Query("select e from entry e, user u where e.uid = u.id and LOWER(u.userName) = LOWER(:userName)")
    public List<Entry> findByUsername(@Param("username") String username);

    public List<Entry> findByUsernameAndSecurity(String username, Security security);

    public List<Entry> findByUserAndSecurityOrderByDateDesc(User user, Security security);

    public List<Entry> findByUserAndSecurityOrderByDateDesc(User user, Security security, Pageable pageable);

    public List<Entry> findByUserOrderByDateDesc(User user, Pageable pageable);

    public Iterable<Entry> findBySecurityOrderByDateDesc(Security security);

    @Query("select e from entry e, user u where e.uid = u.id and LOWER(u.userName) = LOWER(:userName) and e.date >= :startDate and e.date <= :endDate")
    public List<Entry> findByUsernameAndDate(@Param("username") String username,
                                             @Param("startDate") Date startDate,
                                             @Param("endDate") Date endDate);

    @Query("SELECT count(eh) FROM user us, entry eh WHERE us.username = :username AND YEAR(eh.date)= :yr AND us.id = eh.uid")
    public Long calendarCount(@Param("yr") int year, @Param("username") String username);

    @Query("SELECT eh FROM user us, entry eh WHERE us.username = :username AND YEAR(eh.date) = :yr AND us.id = eh.uid and eh.security = :security")
    public List<Entry> findByUsernameAndYearAndSecurity(@Param("username") String username, @Param("yr") int year, Security security);

    @Query("SELECT eh FROM user us, entry eh WHERE us.username = :username AND YEAR(eh.date) = :yr AND us.id = eh.uid")
    public List<Entry> findByUsernameAndYear(@Param("username") String username, @Param("yr") int year);

    @Query("SELECT eh FROM user us, entry eh WHERE us.username = :username AND YEAR(eh.date) = :yr AND MONTH(eh.date) = :month AND us.id = eh.uid")
    public List<Entry> findByUsernameAndYearAndMonth(@Param("username") String username, @Param("yr") int year, @Param("month") int month);

    @Query("SELECT eh FROM user us, entry eh WHERE us.username = :username AND YEAR(eh.date) = :yr AND MONTH(eh.date) = :month AND us.id = eh.uid and eh.security = :security")
    public List<Entry> findByUsernameAndYearAndMonthAndSecurity(@Param("username") String username, @Param("yr") int year, @Param("month") int month, Security security);

}
