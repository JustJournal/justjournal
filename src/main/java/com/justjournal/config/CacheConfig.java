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
package com.justjournal.config;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.justjournal.model.Tag;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** @author Lucas Holt */
@Slf4j
@EnableCaching
@Configuration
public class CacheConfig {
  @Value("${spring.redis.host:localhost}")
  private String hostname;

  @Value("${spring.redis.port:6379}")
  private Integer port;

  private static final DateTimeFormatter FORMATTER = ISO_LOCAL_DATE_TIME;

  private final ObjectMapper objectMapper;

  public CacheConfig(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Primary
  @Bean
  public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
    log.info(String.format("CacheConfig: Redis Host is %s and port is %d", hostname, port));
    return new LettuceConnectionFactory(hostname, port);
  }

  @Bean
  public ReactiveRedisTemplate<String, Tag> reactiveRedisTemplateTag(
      final ReactiveRedisConnectionFactory connectionFactory) {
    final Jackson2JsonRedisSerializer<Tag> valSerializer =
        new Jackson2JsonRedisSerializer<>(objectMapper, Tag.class);

    final RedisSerializationContext.RedisSerializationContextBuilder<String, Tag> builder =
        RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

    final RedisSerializationContext<String, Tag> context =
        builder.hashValue(valSerializer).value(valSerializer).build();

    return new ReactiveRedisTemplate<>(connectionFactory, context);
  }

  @Bean
  public ReactiveRedisTemplate<String, String> reactiveRedisTemplateString(
      final ReactiveRedisConnectionFactory connectionFactory) {
    return new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
  }

  @PostConstruct
  private void postConstruct() {
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateSerializer());
    javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateDeserializer());

    objectMapper.registerModule(new Jdk8Module()).registerModule(javaTimeModule);
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public class LocalDateSerializer extends JsonSerializer<LocalDateTime> {

    @Override
    public void serialize(
        final LocalDateTime value, final JsonGenerator gen, final SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.format(FORMATTER));
    }
  }

  public class LocalDateDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      if (p == null || p.getValueAsString() == null) {
        return null;
      }
      return LocalDateTime.parse(p.getValueAsString(), FORMATTER);
    }
  }
}
