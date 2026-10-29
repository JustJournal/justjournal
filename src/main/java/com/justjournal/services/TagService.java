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

import static com.justjournal.core.CacheKeys.TAG_KEY;

import com.justjournal.exception.ServiceException;
import com.justjournal.model.Tag;
import com.justjournal.repository.EntryTagsRepository;
import com.justjournal.repository.TagRepository;
import com.justjournal.utility.StopWords;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

/** @author Lucas Holt */
@Slf4j
@Service
public class TagService {

  private final EntryService entryService;

  private final TagRepository tagDao;

  private final EntryTagsRepository entryTagsRepository;

  private final ReactiveRedisTemplate<String, Tag> reactiveRedisTemplateTag;

  @Autowired
  public TagService(
      final TagRepository tagDao,
      final EntryTagsRepository entryTagsRepository,
      ReactiveRedisTemplate<String, Tag> reactiveRedisTemplateTag,
      final EntryService entryService) {
    this.tagDao = tagDao;
    this.entryTagsRepository = entryTagsRepository;
    this.reactiveRedisTemplateTag = reactiveRedisTemplateTag;
    this.entryService = entryService;
  }

  public Optional<Tag> getTag(@NonNull Integer id) {
    Optional<Tag> tag = reactiveRedisTemplateTag.opsForValue().get(TAG_KEY + id).blockOptional();

    if (tag.isPresent()) return tag;

    tag = tagDao.findById(id);

    if (tag.isPresent()) {
      reactiveRedisTemplateTag
          .opsForValue()
          .set(TAG_KEY + id, tag.get(), Duration.ofMinutes(10))
          .subscribe();
      return tag;
    }

    return Optional.empty();
  }

  public Flux<Tag> getTags() {
    final Map<String, Tag> tags = new HashMap<>();

    final Iterable<Tag> tagList = tagDao.findAll();
    for (final Tag t : tagList) {
      if (!tags.containsKey(t.getName())) {
        final long count = entryTagsRepository.countByTag(t);
        t.setCount(count);
        tags.put(t.getName(), t);
      }
    }

    return compute(Flux.fromIterable(tags.values()))
        .sorted(Comparator.comparingLong(Tag::getCount));
  }

  public ParallelFlux<Tag> getTags(final String username) throws ServiceException {
    return compute(entryService.getEntryTags(username));
  }

  private ParallelFlux<Tag> compute(Flux<Tag> tags) {
    // the cutoffs depend on the largest count, so it must be known before any tag is classified
    return tags.collectList()
        .flatMapMany(
            list -> {
              final TagWeight tagWeight = new TagWeight();
              for (final Tag tag : list) {
                if (tag.getCount() > tagWeight.largest) tagWeight.largest = tag.getCount();

                if (tag.getCount() < tagWeight.smallest) tagWeight.smallest = tag.getCount();
              }
              return Flux.fromIterable(list).map(tagWeight::calculateType);
            })
        .parallel()
        .runOn(Schedulers.parallel());
  }

  public void deleteTag(final int tagId) {
    tagDao.deleteById(tagId);
    evict(tagId);
  }

  /** Drop a deleted tag from the cache so getTag doesn't keep returning it. */
  private void evict(final int tagId) {
    reactiveRedisTemplateTag.opsForValue().delete(TAG_KEY + tagId).subscribe();
  }

  /**
   * Remove stop word tags, and their links to entries, that were saved before stop words were
   * rejected.
   *
   * @return number of tags removed
   */
  public int deleteStopWordTags() {
    int removed = 0;
    for (final Tag tag : tagDao.findAll()) {
      if (!StopWords.isStopWord(tag.getName())) continue;

      entryTagsRepository.deleteByTag(tag);
      tagDao.delete(tag);
      evict(tag.getId());
      log.info("Removed stop word tag {}", tag.getName());
      removed++;
    }
    return removed;
  }

  @Getter
  @Setter
  class TagWeight {
    long largest = 0;
    long smallest = 10;

    long calculateSmallestCutoff() {
      return largest / 3;
    }

    long calculateLargestCutoff() {
      return calculateSmallestCutoff() * 2;
    }

    Tag calculateType(final Tag tag) {
      if (tag.getCount() > calculateLargestCutoff()) tag.setType("TagCloudLarge");
      else if (tag.getCount() < calculateSmallestCutoff()) tag.setType("TagCloudSmall");
      else tag.setType("TagCloudMedium");

      return tag;
    }
  }
}
