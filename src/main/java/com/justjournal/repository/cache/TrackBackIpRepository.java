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
package com.justjournal.repository.cache;

import static com.justjournal.core.CacheKeys.TRACKBACK_IP_KEY;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** @author Lucas Holt */
@Component
public class TrackBackIpRepository {

  private static final int IP_CACHE_SECONDS = 5;

  ReactiveRedisTemplate<String, String> reactiveRedisTemplateString;

  private final ReactiveValueOperations<String, String> valOperations;

  private final Set<String> invalidIpAddresses = Set.of("127.0.0.1", "localhost", "0.0.0.0", "::1");

  @Autowired
  public TrackBackIpRepository(ReactiveRedisTemplate<String, String> reactiveRedisTemplateString) {
    this.reactiveRedisTemplateString = reactiveRedisTemplateString;
    this.valOperations = reactiveRedisTemplateString.opsForValue();
  }

  public Mono<Boolean> saveIpAddress(String ip, int seconds) {
    if (ip == null || ip.isEmpty() || invalidIpAddresses.stream().anyMatch(ip::equalsIgnoreCase)) {
      return Mono.fromSupplier(() -> false);
    }
    return valOperations.set(TRACKBACK_IP_KEY + ip, ip, Duration.ofSeconds(seconds));
  }

  public Mono<Boolean> saveIpAddress(String ip) {
    return saveIpAddress(ip, IP_CACHE_SECONDS);
  }

  public Mono<String> getIpAddress(String ip) {
    return valOperations.get(TRACKBACK_IP_KEY + ip);
  }

  public Mono<Boolean> deleteIpAddress(String ip) {
    return valOperations.delete(TRACKBACK_IP_KEY + ip);
  }
}
