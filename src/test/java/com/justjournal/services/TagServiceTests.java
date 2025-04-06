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

import com.justjournal.Application;
import com.justjournal.model.Tag;
import com.justjournal.repository.EntryTagsRepository;
import com.justjournal.repository.TagRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.thymeleaf.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** @author Lucas Holt */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class TagServiceTests {

  @Mock
  TagRepository tagRepository;

  @Mock
  EntryTagsRepository entryTagsRepository;

  @InjectMocks
  private TagService tagService;

  @Test
  void testGetTags() {
    var data = List.of(new Tag("test"), new Tag("test"));
    for (Tag tag : data) {
      tag.setId(1);
      tag.setType("small");
    }
    when(entryTagsRepository.countByTag(any(Tag.class))).thenReturn(2L);
    when(tagRepository.findAll()).thenReturn(data);
    List<Tag> tags = tagService.getTags().toStream().toList();

    Assertions.assertFalse(tags.isEmpty());

    for (Tag tag : tags) {
      Assertions.assertTrue(tag.getId() > 0);
      Assertions.assertFalse(StringUtils.isEmpty(tag.getName()));
      Assertions.assertFalse(StringUtils.isEmpty(tag.getType()));
      Assertions.assertTrue(tag.getCount() > 0);
    }
  }

  @Test
  void testGetTagsEmptyList() {
    when(tagRepository.findAll()).thenReturn(Collections.emptyList());

    List<Tag> tags = tagService.getTags().toStream().toList();

    Assertions.assertTrue(tags.isEmpty());
    Mockito.verify(tagRepository, times(1)).findAll();
  }

  @Test
  void deleteTag_shouldCallRepositoryDeleteById() {
    // Arrange
    int tagId = 1;

    // Act
    tagService.deleteTag(tagId);

    // Assert
    Mockito.verify(tagRepository, times(1)).deleteById(tagId);
  }
}
