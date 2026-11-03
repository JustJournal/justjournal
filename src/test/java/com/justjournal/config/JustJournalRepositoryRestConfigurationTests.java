package com.justjournal.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.RepositoryDetectionStrategy;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class JustJournalRepositoryRestConfigurationTests {

  private static List<Class<?>> repositories() throws ClassNotFoundException {
    final ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
            return definition.getMetadata().isInterface();
          }
        };
    scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

    final List<Class<?>> found = new ArrayList<>();
    for (BeanDefinition definition :
        scanner.findCandidateComponents("com.justjournal.repository")) {
      found.add(Class.forName(definition.getBeanClassName()));
    }
    return found;
  }

  @Test
  void noRepositoryIsExported() throws Exception {
    final RepositoryRestConfiguration config = mock(RepositoryRestConfiguration.class);
    final ArgumentCaptor<RepositoryDetectionStrategy> strategy =
        ArgumentCaptor.forClass(RepositoryDetectionStrategy.class);

    new JustJournalRepositoryRestConfiguration()
        .repositoryRestConfigurer()
        .configureRepositoryRestConfiguration(config, mock(CorsRegistry.class));
    verify(config).setRepositoryDetectionStrategy(strategy.capture());

    final List<Class<?>> repositories = repositories();
    // UserRepository and EntryRepository were exported before; make sure the scan sees them
    assertTrue(repositories.size() > 30, "found " + repositories.size() + " repositories");
    for (Class<?> repository : repositories) {
      assertFalse(
          strategy.getValue().isExported(new DefaultRepositoryMetadata(repository)),
          repository.getSimpleName() + " is exported");
    }
  }
}
