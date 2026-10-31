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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Tag;
import com.justjournal.services.TagService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

/** The tag cloud lists a user's tags ordered by how often they are used. */
class TagCloudControllerTests {

  private final TagService tagService = mock(TagService.class);
  private final TagCloudController controller = new TagCloudController(tagService);

  private static Tag tag(final String name, final long count) {
    final Tag tag = new Tag(name);
    tag.setCount(count);
    return tag;
  }

  @Test
  void getTags_sortsByCount() throws ServiceException {
    when(tagService.getTags("ada"))
        .thenReturn(Flux.just(tag("java", 5), tag("bsd", 1), tag("blog", 3)).parallel());

    final ResponseEntity<java.util.Collection<Tag>> result = controller.getTags("ada");

    assertEquals(HttpStatus.OK, result.getStatusCode());
    final List<String> names = new ArrayList<>();
    result.getBody().forEach(t -> names.add(t.getName()));
    assertEquals(List.of("bsd", "blog", "java"), names);
  }

  @Test
  void getTags_emptyIsStillOk() throws ServiceException {
    when(tagService.getTags("ada")).thenReturn(Flux.<Tag>empty().parallel());

    final ResponseEntity<java.util.Collection<Tag>> result = controller.getTags("ada");

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals(0, result.getBody().size());
  }

  @Test
  void getTags_invalidUsername_isNotFound() {
    assertThrows(NotFoundException.class, () -> controller.getTags("no!"));
  }
}
