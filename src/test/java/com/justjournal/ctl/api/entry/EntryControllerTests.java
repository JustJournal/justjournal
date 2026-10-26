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
package com.justjournal.ctl.api.entry;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.LOGIN_ATTRNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.model.Entry;
import com.justjournal.model.FormatType;
import com.justjournal.model.Location;
import com.justjournal.model.Security;
import com.justjournal.model.User;
import com.justjournal.model.api.EntryTo;
import com.justjournal.repository.CommentRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.LocationRepository;
import com.justjournal.repository.MoodRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.services.EntryService;
import com.justjournal.services.TrackbackService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import reactor.core.publisher.Mono;

/** Verifies entry bodies are cleaned before they are saved through the REST API. */
@ExtendWith(MockitoExtension.class)
class EntryControllerTests {

  private static final String BROKEN_HTML = "<p>hello <textarea>oops";

  @Mock private CommentRepository commentRepository;
  @Mock private EntryRepository entryRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private MoodRepository moodRepository;
  @Mock private UserRepository userRepository;
  @Mock private EntryService entryService;
  @Mock private TrackbackService trackbackService;
  @Mock private Settings settings;
  @Mock private RecentBlogsRepository recentBlogsRepository;

  @InjectMocks private EntryController entryController;

  private MockHttpSession session;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    session = new MockHttpSession();
    session.setAttribute(LOGIN_ATTRNAME, "laffer1");
    session.setAttribute(LOGIN_ATTRID, 1);
    response = new MockHttpServletResponse();

    final User user = new User();
    user.setId(1);
    when(userRepository.findById(1)).thenReturn(Optional.of(user));
    lenient().when(locationRepository.findById(anyInt())).thenReturn(Optional.of(new Location()));
    lenient().when(recentBlogsRepository.delete()).thenReturn(Mono.just(true));
  }

  private EntryTo entryTo(final String format) {
    return EntryTo.builder()
        .subject("test")
        .body(BROKEN_HTML)
        .format(format)
        .security(Security.PRIVATE)
        .build();
  }

  private Entry postAndCapture(final String format) {
    when(entryRepository.saveAndFlush(any(Entry.class)))
        .thenAnswer(
            inv -> {
              final Entry e = inv.getArgument(0);
              e.setId(42);
              return e;
            });

    entryController.post(entryTo(format), session, response, new ExtendedModelMap());

    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  @Test
  void postCleansHtmlBody() {
    final Entry saved = postAndCapture("HTML");

    assertEquals(200, response.getStatus());
    assertEquals(FormatType.HTML, saved.getFormat());
    assertTrue(saved.getBody().contains("</textarea>"), saved.getBody());
  }

  @Test
  void postLeavesTextBodyAlone() {
    final Entry saved = postAndCapture("TEXT");

    assertEquals(FormatType.TEXT, saved.getFormat());
    assertEquals(BROKEN_HTML, saved.getBody());
  }

  @Test
  void postLeavesMarkdownBodyAlone() {
    final Entry saved = postAndCapture("MARKDOWN");

    assertEquals(FormatType.MARKDOWN, saved.getFormat());
    assertFalse(saved.getBody().contains("</textarea>"), saved.getBody());
  }

  @Test
  void putCleansHtmlBody() {
    when(entryRepository.findById(0)).thenReturn(Optional.empty());
    when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

    entryController.put(entryTo("HTML"), session, response);

    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    assertTrue(captor.getValue().getBody().contains("</textarea>"), captor.getValue().getBody());
  }
}
