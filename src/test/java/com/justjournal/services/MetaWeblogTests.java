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
package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.Login;
import com.justjournal.core.Settings;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.FormatType;
import com.justjournal.model.Journal;
import com.justjournal.model.Location;
import com.justjournal.model.Mood;
import com.justjournal.model.PrefBool;
import com.justjournal.model.Security;
import com.justjournal.model.Tag;
import com.justjournal.model.User;
import com.justjournal.model.UserContact;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryTagsRepository;
import com.justjournal.repository.LocationRepository;
import com.justjournal.repository.MoodRepository;
import com.justjournal.repository.TagRepository;
import com.justjournal.repository.UserRepository;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

/** Verifies entry bodies are cleaned before they are saved through the MetaWeblog API. */
@ExtendWith(MockitoExtension.class)
class MetaWeblogTests {

  private static final String BROKEN_HTML = "<p>hello <textarea>oops";

  @Mock private EntryRepository entryRepository;
  @Mock private Login webLogin;
  @Mock private UserRepository userRepository;
  @Mock private MoodRepository moodDao;
  @Mock private LocationRepository locationDao;
  @Mock private TagRepository tagDao;
  @Mock private Settings settings;
  @Mock private EntryTagsRepository entryTagsRepository;
  @Mock private EntryService entryService;

  @InjectMocks private MetaWeblog metaWeblog;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(1);
    user.setUsername("laffer1");
    user.setName("Lucas");
    final Journal journal = new Journal();
    journal.setName("Lucas's Journal");
    journal.setPingServices(false);
    user.getJournals().add(journal);

    lenient().when(webLogin.validate("laffer1", "secret")).thenReturn(1);
    lenient().when(userRepository.findById(1)).thenReturn(Optional.of(user));
    lenient().when(locationDao.findById(anyInt())).thenReturn(Optional.of(new Location()));
  }

  private Map<String, Object> content() {
    final Map<String, Object> content = new HashMap<>();
    content.put("title", "test");
    content.put("description", BROKEN_HTML);
    content.put("categories", new String[0]);
    return content;
  }

  @Test
  void newPostCleansBody() {
    when(entryRepository.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));

    metaWeblog.newPost("", "laffer1", "secret", content(), true);

    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    final Entry saved = captor.getValue();
    assertTrue(saved.getBody().contains("</textarea>"), saved.getBody());
    assertEquals(FormatType.HTML, saved.getFormat());
    assertEquals(PrefBool.N, saved.getAutoFormat());
  }

  @Test
  void editPostCleansBody() {
    final Entry existing = new Entry();
    existing.setId(5);
    existing.setUser(user);
    existing.setFormat(FormatType.TEXT);
    existing.setAutoFormat(PrefBool.Y);
    when(entryRepository.findById(5)).thenReturn(Optional.of(existing));

    metaWeblog.editPost("5", "laffer1", "secret", content(), true);

    verify(entryRepository).save(existing);
    assertTrue(existing.getBody().contains("</textarea>"), existing.getBody());
    assertEquals(FormatType.HTML, existing.getFormat());
    assertEquals(PrefBool.N, existing.getAutoFormat());
  }

  /**
   * XML-RPC requests do not get open-session-in-view, so without a transaction the "Not Specified"
   * location (id 0) is detached and Hibernate writes a NULL location.
   */
  @Test
  void postMethodsAreTransactional() {
    Arrays.stream(MetaWeblog.class.getMethods())
        .filter(m -> m.getName().equals("newPost") || m.getName().equals("editPost"))
        .forEach(m -> assertTrue(m.isAnnotationPresent(Transactional.class), m.getName()));
  }
  // ---- the rest of the API ------------------------------------------------------------------

  private static final String AUTH_FAULT = "User authentication failed: laffer1";

  private static void assertFault(final Object result, final int code, final String message) {
    assertTrue(result instanceof Map, String.valueOf(result));
    final Map<?, ?> fault = (Map<?, ?>) result;
    assertEquals(code, fault.get("faultCode"));
    assertEquals(message, fault.get("faultString"));
    assertEquals(2, fault.size());
  }

  private Entry ownedEntry(final int id) {
    final Entry entry = new Entry(id, "Subject " + id);
    entry.setUser(user);
    entry.setBody("<p>body " + id + "</p>");
    entry.setFormat(FormatType.HTML);
    entry.setDate(new Date(1_700_000_000_000L));
    return entry;
  }

  private static Entry someoneElses(final int id) {
    final User other = new User();
    other.setId(2);
    other.setUsername("other");
    final Entry entry = new Entry(id, "Not mine");
    entry.setUser(other);
    return entry;
  }

  @Test
  void usersInfo_describesTheAccount() {
    final UserContact contact = new UserContact();
    contact.setEmail("lucas@example.com");
    ReflectionTestUtils.setField(user, "userContact", contact);
    when(settings.getBaseUri()).thenReturn("http://localhost/");

    final HashMap<String, Serializable> info = metaWeblog.getUsersInfo("key", "laffer1", "secret");

    assertEquals("laffer1", info.get("nickname"));
    assertEquals(1, info.get("userid"));
    assertEquals("http://localhost/users/laffer1", info.get("url"));
    assertEquals("lucas@example.com", info.get("email"));
    assertEquals("Lucas", info.get("firstname"));
  }

  @Test
  void usersInfo_badPasswordOrMissingAccount_isAFault() {
    assertFault(metaWeblog.getUsersInfo("key", "laffer1", "wrong"), 4, AUTH_FAULT);

    when(userRepository.findById(1)).thenReturn(Optional.empty());
    assertFault(metaWeblog.getUsersInfo("key", "laffer1", "secret"), 4, AUTH_FAULT);
  }

  @Test
  void usersBlogs_listsTheOneJournal() {
    when(settings.getBaseUri()).thenReturn("http://localhost/");

    final ArrayList<HashMap<Object, Serializable>> blogs =
        metaWeblog.getUsersBlogs("key", "laffer1", "secret");

    assertEquals(1, blogs.size());
    assertEquals("http://localhost/users/laffer1", blogs.get(0).get("url"));
    assertEquals(1, blogs.get(0).get("blogid"));
    assertEquals("Lucas's Journal", blogs.get(0).get("blogName"));
  }

  @Test
  void usersBlogs_badPasswordOrMissingAccount_isAFault() {
    assertFault(metaWeblog.getUsersBlogs("key", "laffer1", "wrong").get(0), 4, AUTH_FAULT);

    when(userRepository.findById(1)).thenReturn(Optional.empty());
    assertFault(metaWeblog.getUsersBlogs("key", "laffer1", "secret").get(0), 4, AUTH_FAULT);
  }

  @Test
  void newPost_returnsTheNewIdAndFillsInTheDefaults() {
    final Mood unspecified = new Mood();
    unspecified.setId(12);
    when(moodDao.findById(12)).thenReturn(unspecified);
    when(entryRepository.save(any(Entry.class)))
        .thenAnswer(
            inv -> {
              ((Entry) inv.getArgument(0)).setId(77);
              return inv.getArgument(0);
            });

    final Serializable result = metaWeblog.newPost("", "laffer1", "secret", content(), false);

    assertEquals("77", result);
    final ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
    verify(entryRepository).save(captor.capture());
    final Entry saved = captor.getValue();
    assertEquals(user, saved.getUser());
    assertEquals("test", saved.getSubject());
    assertEquals(Security.PUBLIC, saved.getSecurity());
    assertEquals(12, saved.getMood().getId());
    assertEquals(PrefBool.Y, saved.getDraft());
    assertEquals(PrefBool.Y, saved.getAllowComments());
    assertEquals(PrefBool.Y, saved.getEmailComments());
    assertEquals("", saved.getMusic());
  }

  @Test
  void newPost_badPasswordOrMissingAccount_isAFault() {
    assertFault(metaWeblog.newPost("", "laffer1", "wrong", content(), true), 2041, AUTH_FAULT);

    when(userRepository.findById(1)).thenReturn(Optional.empty());
    assertFault(metaWeblog.newPost("", "laffer1", "secret", content(), true), 2041, AUTH_FAULT);
    verify(entryRepository, never()).save(any());
  }

  @Test
  void deletePost_removesTheCallersOwnEntryOnly() {
    when(entryRepository.findById(5)).thenReturn(Optional.of(ownedEntry(5)));
    when(entryRepository.findById(6)).thenReturn(Optional.of(someoneElses(6)));

    assertEquals(true, metaWeblog.deletePost("key", "5", "laffer1", "secret", true));
    assertEquals(true, metaWeblog.deletePost("key", "6", "laffer1", "secret", true));

    verify(entryRepository).deleteById(5);
    verify(entryRepository, never()).deleteById(6);
  }

  @Test
  void deletePost_badInput_isAFault() {
    assertFault(metaWeblog.deletePost("key", "5", "laffer1", "wrong", true), 4, AUTH_FAULT);
    assertFault(metaWeblog.deletePost("key", "0", "laffer1", "secret", true), 4, "Invalid entry id 0");
    assertFault(
        metaWeblog.deletePost("key", "abc", "laffer1", "secret", true), 4, "Invalid entry id abc");
    when(entryRepository.findById(9)).thenReturn(Optional.empty());
    assertFault(metaWeblog.deletePost("key", "9", "laffer1", "secret", true), 4, "Invalid entry id 9");
    verify(entryRepository, never()).deleteById(anyInt());
  }

  @Test
  void deletePost_repositoryFailure_isAFault() {
    when(entryRepository.findById(5)).thenThrow(new IllegalStateException("db"));

    assertFault(
        metaWeblog.deletePost("key", "5", "laffer1", "secret", true), 4, "Unable to delete entry id 5");
  }

  @Test
  void editPost_appliesTheCategoriesThatExistAsTags() {
    final Entry existing = ownedEntry(5);
    when(entryRepository.findById(5)).thenReturn(Optional.of(existing));
    final Tag java = new Tag("java");
    when(tagDao.findByName("java")).thenReturn(java);
    when(tagDao.findByName("unknown")).thenReturn(null);
    final Map<String, Object> content = content();
    content.put("categories", new String[] {"java", "the", "unknown"});

    assertEquals(true, metaWeblog.editPost("5", "laffer1", "secret", content, false));

    assertEquals(PrefBool.Y, existing.getDraft());
    assertEquals("test", existing.getSubject());
    final ArgumentCaptor<EntryTag> captor = ArgumentCaptor.forClass(EntryTag.class);
    verify(entryTagsRepository).save(captor.capture());
    assertEquals(java, captor.getValue().getTag());
    assertEquals(existing, captor.getValue().getEntry());
    verify(tagDao, never()).findByName("the"); // stop words are skipped
  }

  @Test
  void editPost_someoneElsesEntry_isLeftAlone() {
    when(entryRepository.findById(6)).thenReturn(Optional.of(someoneElses(6)));

    assertEquals(true, metaWeblog.editPost("6", "laffer1", "secret", content(), true));

    verify(entryRepository, never()).save(any());
  }

  @Test
  void editPost_badInput_isAFault() {
    assertFault(metaWeblog.editPost("5", "laffer1", "wrong", content(), true), 4, AUTH_FAULT);
    assertFault(metaWeblog.editPost("0", "laffer1", "secret", content(), true), 4, "Invalid entry id 0");
    assertFault(
        metaWeblog.editPost("abc", "laffer1", "secret", content(), true), 4, "Invalid entry id abc");
    when(entryRepository.findById(9)).thenReturn(Optional.empty());
    assertFault(metaWeblog.editPost("9", "laffer1", "secret", content(), true), 4, "Invalid entry id 9");
  }

  @Test
  void editPost_saveFailure_isAFault() {
    when(entryRepository.findById(5)).thenReturn(Optional.of(ownedEntry(5)));
    when(entryRepository.save(any(Entry.class))).thenThrow(new IllegalStateException("db"));

    assertFault(metaWeblog.editPost("5", "laffer1", "secret", content(), true), 4, AUTH_FAULT);
  }

  private void describeEntries() {
    lenient().when(settings.getBlogBaseUrl(anyString()))
        .thenAnswer(inv -> "http://localhost/users/" + inv.getArgument(0));
    lenient().when(entryService.convertBody(any(), anyString()))
        .thenAnswer(inv -> "converted " + inv.getArgument(1));
  }

  @Test
  void recentPosts_describeUpToTheRequestedNumberOfEntries() {
    describeEntries();
    final Entry first = ownedEntry(5);
    final EntryTag tagged = new EntryTag();
    tagged.setTag(new Tag("java"));
    first.getTags().add(tagged);
    when(entryRepository.findByUsername("laffer1"))
        .thenReturn(List.of(first, ownedEntry(4), ownedEntry(3)));

    @SuppressWarnings("unchecked")
    final List<HashMap<Object, Serializable>> posts =
        (List<HashMap<Object, Serializable>>) metaWeblog.getRecentPosts("", "laffer1", "secret", 2);

    assertEquals(2, posts.size());
    final HashMap<Object, Serializable> post = posts.get(0);
    assertEquals("http://localhost/users/laffer1/entry/5", post.get("link"));
    assertEquals("http://localhost/users/laffer1/entry/5", post.get("permaLink"));
    assertEquals("5", post.get("postid"));
    assertEquals("1", post.get("userid"));
    assertEquals("Subject 5", post.get("title"));
    assertEquals("converted <p>body 5</p>", post.get("description"));
    assertEquals("converted <p>body 5</p>", post.get("content"));
    assertEquals("converted <p>body 5</p>", post.get("mt_excerpt"));
    assertEquals(0, post.get("mt_allow_pings"));
    assertEquals(1, post.get("mt_allow_comments"));
    assertTrue(post.get("dateCreated") instanceof String);
    assertEquals("java", ((String[]) post.get("categories"))[0]);
    assertEquals(0, ((String[]) posts.get(1).get("categories")).length);
  }

  @Test
  void recentPosts_badPassword_isAFault() {
    assertFault(metaWeblog.getRecentPosts("", "laffer1", "wrong", 2), 4, AUTH_FAULT);
    verify(entryRepository, never()).findByUsername(anyString());
  }

  @Test
  void post_describesOneOwnedEntry() {
    describeEntries();
    when(entryRepository.findById(5)).thenReturn(Optional.of(ownedEntry(5)));

    final HashMap<String, Serializable> post = metaWeblog.getPost("5", "laffer1", "secret");

    assertEquals("http://localhost/users/laffer1/entry/5", post.get("link"));
    assertEquals("5", post.get("postid"));
    assertEquals("Subject 5", post.get("title"));
    assertEquals("converted <p>body 5</p>", post.get("description"));
    assertEquals(0, ((String[]) post.get("categories")).length);
  }

  @Test
  void post_badPasswordMissingOrNotOwned_isAFault() {
    assertFault(metaWeblog.getPost("5", "laffer1", "wrong"), 4, AUTH_FAULT);
    when(entryRepository.findById(9)).thenReturn(Optional.empty());
    assertFault(metaWeblog.getPost("9", "laffer1", "secret"), 4, "Invalid entry id 9");
    when(entryRepository.findById(6)).thenReturn(Optional.of(someoneElses(6)));
    assertFault(metaWeblog.getPost("6", "laffer1", "secret"), 4, AUTH_FAULT);
  }

  @Test
  void categories_areTheUsersTags() throws ServiceException {
    when(entryService.getEntryTags("laffer1")).thenReturn(Flux.just(new Tag("java"), new Tag("spring")));

    @SuppressWarnings("unchecked")
    final List<HashMap<Object, Serializable>> categories =
        (List<HashMap<Object, Serializable>>) metaWeblog.getCategories("", "laffer1", "secret");

    assertEquals(2, categories.size());
    assertEquals("java", categories.get(0).get("title"));
    assertEquals("java", categories.get(0).get("description"));
    assertEquals("spring", categories.get(1).get("title"));
  }

  @Test
  void categories_badPasswordOrFailure() throws ServiceException {
    assertFault(metaWeblog.getCategories("", "laffer1", "wrong"), 4, AUTH_FAULT);

    when(entryService.getEntryTags("laffer1")).thenThrow(new ServiceException("down"));
    assertTrue(((List<?>) metaWeblog.getCategories("", "laffer1", "secret")).isEmpty());
    assertFalse(metaWeblog.getCategories("", "laffer1", "secret") instanceof Map);
  }
}
