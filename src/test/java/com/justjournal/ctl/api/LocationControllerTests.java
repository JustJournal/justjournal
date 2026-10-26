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
package com.justjournal.ctl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.justjournal.config.WebMvcConfig;
import com.justjournal.model.Location;
import com.justjournal.repository.LocationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/** Location responses must carry an ETag derived from their content. */
@ExtendWith(MockitoExtension.class)
class LocationControllerTests {

  private static final String LIST_URL = "/api/location";
  private static final String ITEM_URL = "/api/location/1";

  @Mock private LocationRepository locationRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new LocationController(locationRepository))
            .addMappedInterceptors(new String[] {"/api/**"}, WebMvcConfig.apiCacheInterceptor())
            .addFilters(new ShallowEtagHeaderFilter())
            .build();
  }

  private Location location(final String title) {
    final Location l = new Location();
    l.setId(1);
    l.setTitle(title);
    return l;
  }

  private List<Location> locations(final String title) {
    return new ArrayList<>(List.of(location(title)));
  }

  private String etag(final String url) throws Exception {
    return mockMvc
        .perform(get(url).header("Accept", "*/*"))
        .andReturn()
        .getResponse()
        .getHeader("ETag");
  }

  @Test
  void listEtagChangesWhenTitleChanges() throws Exception {
    when(locationRepository.findAll()).thenReturn(locations("Home"), locations("Work"));

    final String first = etag(LIST_URL);
    final String second = etag(LIST_URL);

    assertNotNull(first);
    assertNotEquals(first, second);
  }

  @Test
  void unchangedListRevalidatesWith304() throws Exception {
    when(locationRepository.findAll()).thenReturn(locations("Home"), locations("Home"));

    final String etag = etag(LIST_URL);
    final MvcResult again =
        mockMvc
            .perform(get(LIST_URL).header("Accept", "*/*").header("If-None-Match", etag))
            .andReturn();

    assertEquals(304, again.getResponse().getStatus());
  }

  @Test
  void itemEtagChangesWhenTitleChanges() throws Exception {
    when(locationRepository.findById(1))
        .thenReturn(Optional.of(location("Home")), Optional.of(location("Work")));

    final String first = etag(ITEM_URL);
    final String second = etag(ITEM_URL);

    assertNotNull(first);
    assertNotEquals(first, second);
  }

  @Test
  void unchangedItemRevalidatesWith304() throws Exception {
    when(locationRepository.findById(1))
        .thenReturn(Optional.of(location("Home")), Optional.of(location("Home")));

    final String etag = etag(ITEM_URL);
    final MvcResult again =
        mockMvc.perform(get(ITEM_URL).header("If-None-Match", etag)).andReturn();

    assertEquals(304, again.getResponse().getStatus());
  }

  @Test
  void missingLocationIsNotFound() throws Exception {
    when(locationRepository.findById(1)).thenReturn(Optional.empty());

    assertEquals(404, mockMvc.perform(get(ITEM_URL)).andReturn().getResponse().getStatus());
  }
}
