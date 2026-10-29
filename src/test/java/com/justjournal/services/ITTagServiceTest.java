package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.justjournal.Application;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryTag;
import com.justjournal.model.Tag;
import com.justjournal.repository.EntryRepository;
import com.justjournal.repository.EntryTagsRepository;
import com.justjournal.repository.TagRepository;
import com.justjournal.utility.StopWords;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@ActiveProfiles("it")
@Transactional
class ITTagServiceTest {
  @Autowired TagService tagService;
  @Autowired TagRepository tagRepository;
  @Autowired EntryRepository entryRepository;
  @Autowired EntryTagsRepository entryTagsRepository;
  @Autowired EntityManager entityManager;

  private Tag tag(final String name) {
    final Tag existing = tagRepository.findByName(name);
    return existing != null ? existing : tagRepository.save(new Tag(name));
  }

  private void link(final Entry entry, final Tag tag) {
    final EntryTag et = new EntryTag();
    et.setEntry(entry);
    et.setTag(tag);
    entryTagsRepository.save(et);
  }

  /** Several linked stop word tags in one transaction, as the scheduled cleanup runs it. */
  @Test
  void deleteStopWordTagsRemovesSeveralLinkedTags() {
    final List<Entry> entries = entryRepository.findAll();
    assumeFalse(entries.isEmpty(), "test database has no entries");
    final Entry entry = entries.get(0);

    link(entry, tag("the"));
    link(entry, tag("and"));
    link(entry, tag("jjcleanuptest"));
    entityManager.flush();
    entityManager.clear();

    assertTrue(tagService.deleteStopWordTags() >= 2);
    entityManager.flush();
    entityManager.clear();

    assertTrue(tagRepository.findByName("the") == null);
    assertTrue(tagRepository.findByName("and") == null);
    final List<EntryTag> remaining = entryTagsRepository.findByEntry(entryRepository.findById(entry.getId()).orElseThrow());
    assertFalse(remaining.stream().anyMatch(et -> StopWords.isStopWord(et.getTag().getName())));
    assertTrue(remaining.stream().anyMatch(et -> et.getTag().getName().equals("jjcleanuptest")));
  }
}
