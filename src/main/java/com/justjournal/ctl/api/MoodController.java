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
package com.justjournal.ctl.api;

import static com.justjournal.core.Constants.PARAM_ID;

import com.justjournal.core.Constants;
import com.justjournal.model.Mood;
import com.justjournal.repository.MoodRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * List moods used for journal entries.
 *
 * @author Lucas Holt
 * @since 1.0
 */
@RestController
@RequestMapping("/api/mood")
public class MoodController {

  private final MoodRepository moodDao;

  @Autowired
  public MoodController(final MoodRepository moodDao) {
    this.moodDao = moodDao;
  }

  //  @Cacheable(value = "mood", key = "id")
  @GetMapping(value = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Mood> getById(@PathVariable(PARAM_ID) final Integer id) {
    final Mood m = moodDao.findById(id).orElse(null);
    if (m == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

    return ResponseEntity.ok().body(m);
  }

  /**
   * All moods usable by blogs
   *
   * @return mood list
   */
  //   @Cacheable("mood")
  @GetMapping(headers = Constants.HEADER_ACCEPT_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Mood>> getMoodList() {
    final List<Mood> list = moodDao.findAll();
    Collections.sort(list);
    return ResponseEntity.ok().body(list);
  }
}
