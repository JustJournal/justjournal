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
package com.justjournal.services;

import com.justjournal.exception.ServiceException;
import com.justjournal.model.*;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryTagsRepository;
import com.justjournal.repository.TagRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.utility.HTMLUtil;
import com.justjournal.utility.Xml;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/** @author Lucas Holt */
@Slf4j
@Service
public class EntryService {
  private static final int MAX_RECENT_ENTRIES = 5;

  private final EntryRepository entryDao;

  private final UserRepository userRepository;


  private final TagRepository tagDao;

  private final EntryTagsRepository entryTagsRepository;

  public EntryService(EntryRepository entryDao, UserRepository userRepository, TagRepository tagDao, EntryTagsRepository entryTagsRepository) {
    this.entryDao = entryDao;
    this.userRepository = userRepository;
    this.tagDao = tagDao;
    this.entryTagsRepository = entryTagsRepository;
  }

  private Flux<RecentEntry> getRecentEntryObservable(Page<Entry> entries) {
    return Flux.fromIterable(entries)
        .map(
            o -> {
              final RecentEntry recentEntry = new RecentEntry();
              recentEntry.setId(o.getId());
              recentEntry.setSubject(Xml.cleanString(o.getSubject()));
              return recentEntry;
            });
  }

  /**
   * Get the recent blog entries list for the sidebar, but only use public entries.
   *
   * @param username blog user
   * @return subject & entry id data
   */
  @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
  public Flux<RecentEntry> getRecentEntriesPublic(final String username) throws ServiceException {
    try {
      final Pageable page =
          PageRequest.of(0, MAX_RECENT_ENTRIES, Sort.by(Sort.Direction.DESC, "date", "id"));
      final Page<Entry> entries =
          entryDao.findByUserAndSecurityAndDraftWithSubjectOnly(
              username, Security.PUBLIC, PrefBool.N, page);
      return getRecentEntryObservable(entries);
    } catch (final Exception e) {
      log.error(e.getMessage());
      throw new ServiceException("Unable to retrieve recent blog entries for " + username, e);
    }
  }

  /**
   * Get the recent blog entries list for the sidebar.
   *
   * @param username blog username
   * @return subject & entry id data
   */
  @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
  public Flux<RecentEntry> getRecentEntries(final String username) throws ServiceException {
    final User user = userRepository.findByUsername(username);
    if (user == null) {
      log.warn("username not found in getRecentEntrieswith %s", username);
      return null;
    }

    try {
      final Pageable page =
          PageRequest.of(0, MAX_RECENT_ENTRIES, Sort.Direction.DESC, "date", "id");
      final Page<Entry> entries = entryDao.findByUserOrderByDateDesc(user, page);
      return getRecentEntryObservable(entries);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      throw new ServiceException("Unable to retrieve recent blog entries for " + username);
    }
  }

  @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
  public Entry getPublicEntry(final int id, final String username) throws ServiceException {
    try {
      final Entry entry = entryDao.findById(id).orElse(null);
      if (entry == null) return null;

      if (entry.getUser().getUsername().equalsIgnoreCase(username)
            && entry.getSecurity() == Security.PUBLIC
            && entry.getDraft().equals(PrefBool.N)) // public
          return entry;
      return null;
    } catch (final Exception e) {
      log.error(e.getMessage());
      throw new ServiceException(e);
    }
  }

  @Transactional
  public List<Entry> getPublicEntries(final String username) throws ServiceException {
    try {
      final User user = userRepository.findByUsername(username);
      if (user == null) {
        return Collections.emptyList();
      }
      return entryDao.findByUserAndSecurityAndDraftOrderByDateDesc(
          user, Security.PUBLIC, PrefBool.N);
    } catch (final Exception e) {
      log.error(e.getMessage());
      throw new ServiceException(e);
    }
  }

  @Transactional
  public Page<Entry> getPublicEntries(final String username, final Pageable pageable)
      throws ServiceException {
    try {
      final User user = userRepository.findByUsername(username);
      if (user == null) {
        return null;
      }
      return entryDao.findByUserAndSecurityAndDraft(
          user, Security.PUBLIC, PrefBool.N, pageable);
    } catch (final Exception e) {
      log.error(e.getMessage());
      throw new ServiceException(e);
    }
  }

  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = Isolation.READ_UNCOMMITTED)
  public Page<Entry> getEntries(final String username, final Pageable pageable)
      throws ServiceException {
    try {
      final User user = userRepository.findByUsername(username);
      if (user == null) {
        return null;
      }
      return entryDao.findByUserOrderByDateDesc(user, pageable);
    } catch (final Exception e) {
      log.error(e.getMessage());
      throw new ServiceException(e);
    }
  }

  /**
   * Get Friend public blog entries.
   *
   * @param username
   * @return
   */
  @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
  public List<Entry> getFriendsEntries(final String username) throws ServiceException {
    try {
      final Pageable page = PageRequest.of(0, 20, Sort.Direction.DESC, "date", "id");
      final Page<Entry> fe = entryDao.findByUserFriends(username, PrefBool.N, page);

      return fe.getContent();
    } catch (final Exception e) {
      log.error(e.getMessage());
      throw new ServiceException(e);
    }
  }

  /**
   * Get all the tags used on entries by a particular user
   *
   * @param username
   * @return
   */
  @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
  public Flux<Tag> getEntryTags(@NonNull final String username) throws ServiceException {
    try {
      final Map<String, Tag> tags = new HashMap<>();

      final List<Tag> tagList = tagDao.findByUsername(username);
      for (final Tag t : tagList) {
        if (!tags.containsKey(t.getName())) {
          t.setCount(1);
          tags.put(t.getName(), t);
        } else {
          final Tag tag = tags.get(t.getName());
          tag.setCount(t.getCount() + 1);
          tags.put(t.getName(), tag);
        }
      }
      return Flux.fromIterable(tags.values()).sort(Comparator.comparingLong(Tag::getCount));
    } catch (final Exception e) {
      log.error(e.getMessage());
      throw new ServiceException(e);
    }
  }

  /**
   * Add and remove tags on an entry from a list.
   *
   * @param entry entry to modify
   * @param tags tags that should be present (removes anything not listed)
   */
  public void applyTags(final Entry entry, final Set<String> tags) {
    final List<EntryTag> entryTags = entryTagsRepository.findByEntry(entry);

    // find tags that are missing in the db and add them.
    for (final String tag : tags) {
      boolean found = false;
      for (final EntryTag entryTag : entryTags) {
        if (entryTag.getTag().getName().equalsIgnoreCase(tag)) {
          // in list
          found = true;
          break;
        }
      }

      if (!found) {
        addTagToEntry(entry, tag);
      }
    }

    for (final EntryTag entryTag : entryTags) {
      boolean found = false;

      for (final String tag : tags) {
        if (tag.equalsIgnoreCase(entryTag.getTag().getName())) {
          found = true;
          break;
        }
      }

      if (!found) removeTagFromEntry(entry, entryTag.getTag().getName());
    }
  }

  /**
   * Add a tag to an entry
   *
   * @param entry entry to add tags to
   * @param tag tag name
   */
  public void addTagToEntry(final Entry entry, final String tag) {
    Tag t = tagDao.findByName(tag);
    if (t == null) t = tagDao.save(new Tag(tag));

    final EntryTag ets = entryTagsRepository.findByEntryAndTag(entry, t);
    if (ets == null) {
      final EntryTag et = new EntryTag();
      et.setTag(t);
      et.setEntry(entry);
      entryTagsRepository.save(et);
    }
  }

  /**
   * Remove tag from entry
   *
   * @param entry entry to modify
   * @param tag tag name
   */
  public void removeTagFromEntry(final Entry entry, final String tag) {
    final Tag t = tagDao.findByName(tag);
    if (t == null) return;

    final EntryTag ets = entryTagsRepository.findByEntryAndTag(entry, t);
    if (ets != null) {

      entryTagsRepository.delete(ets);
    }
  }

  @Autowired
  private MarkdownService markdownService;

  /**
   * Converts body to text
   * @param format
   * @param body
   * @return
   */
  public String convertBody(FormatType format, String body) {
    if (format == FormatType.MARKDOWN) {
      body = markdownService.convertToText(body);
    } else if (format == FormatType.HTML) {
      body = HTMLUtil.textFromHTML(body);
    }
    return body;
  }

  public String convertBodyToHtml(FormatType format, String body) {
    if (format == FormatType.MARKDOWN) {
      body = markdownService.convertToText(body);
    } else if (format == FormatType.TEXT) {
      body = body.replaceAll("\\n", "<br/>"); // TODO: match users controller formatting style
    }
    return body;
  }
}
