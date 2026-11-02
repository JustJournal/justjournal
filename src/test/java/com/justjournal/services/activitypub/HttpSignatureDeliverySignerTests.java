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
package com.justjournal.services.activitypub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpSignatureDeliverySignerTests {

  private static final String BOB = "https://www.justjournal.com/users/bob";
  private static final URI INBOX = URI.create("https://remote.social/users/alice/inbox");

  @Test
  void sign_postsTheBodyToTheTargetAsTheActor() {
    final HttpSignatureService service = mock(HttpSignatureService.class);
    final byte[] body = "{\"type\":\"Accept\"}".getBytes(StandardCharsets.UTF_8);
    final Map<String, String> headers = Map.of("Signature", "x");
    when(service.sign("POST", INBOX, body, BOB)).thenReturn(headers);

    assertSame(headers, new HttpSignatureDeliverySigner(service).sign(BOB, INBOX, body));
  }

  @Test
  void sign_withTheActorsKey_verifiesAtTheInbox() throws Exception {
    final Instant now = Instant.parse("2026-11-02T12:00:00Z");
    final ActorKeyService actorKeyService = mock(ActorKeyService.class);
    when(actorKeyService.privateKeyFor(BOB)).thenReturn(TestKeys.privateKey());
    // the receiving server is remote.social
    when(actorKeyService.instanceActorId()).thenReturn("https://remote.social/actor");
    final RemoteKeyResolver resolver = mock(RemoteKeyResolver.class);
    when(resolver.resolve(BOB + "#main-key"))
        .thenReturn(new RemoteKey(BOB + "#main-key", BOB, TestKeys.PUBLIC_PEM, 0));
    final HttpSignatureService service =
        new HttpSignatureService(actorKeyService, resolver, Clock.fixed(now, ZoneOffset.UTC));
    final byte[] body = "{\"type\":\"Accept\"}".getBytes(StandardCharsets.UTF_8);

    final Map<String, String> headers =
        new HttpSignatureDeliverySigner(service).sign(BOB, INBOX, body);

    final Map<String, List<String>> received = new HashMap<>();
    headers.forEach((name, value) -> received.put(name, List.of(value)));
    assertEquals(
        new VerifiedSignature(BOB + "#main-key", BOB),
        service.verify("POST", "/users/alice/inbox", received, body));
  }
}
