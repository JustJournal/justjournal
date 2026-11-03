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
 * Access account information for a specific user or all users of Just Journal.
 *
 * @author Lucas Holt
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

  @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username)")
  User findByUsername(@Param("username") String username);

  @Query(
      "SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username) and u.password ="
          + " :password")
  User findByUsernameAndPassword(
      @Param("username") String username, @Param("password") String password);

  @Query(
      "SELECT u FROM User u, Journal j where j.user = u and j.ownerViewOnly = false"
          + " and u.disabled = false")
  List<User> getPublicUsers();

  /** Ids and usernames of the disabled accounts, as {@code [id, username]} rows. */
  @Query("SELECT u.id, u.username FROM User u WHERE u.disabled = true")
  List<Object[]> findDisabledAccounts();

  /** Accounts whose username contains the text, ignoring case, for the admin account list. */
  Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

  /** Users who logged in after the given time. */
  long countByLastLoginAfter(Date since);
}
