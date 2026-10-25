package com.justjournal.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Thread pool for {@code @Scheduled} jobs.
 *
 * <p>Spring Boot's default scheduler has a single thread, so one long job (the statistics refresh,
 * the RSS refresh) held up every other job, including the mail queue that should run every two
 * minutes. Each fixed-delay job still never overlaps with itself.
 */
@Configuration
public class SchedulingConfig {

  static final int POOL_SIZE = 4;

  @Bean
  public ThreadPoolTaskScheduler taskScheduler(final ThreadPoolTaskSchedulerBuilder builder) {
    return builder.poolSize(POOL_SIZE).threadNamePrefix("scheduling-").build();
  }
}
