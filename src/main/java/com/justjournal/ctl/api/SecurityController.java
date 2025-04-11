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

import static com.justjournal.core.Constants.PARAM_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.justjournal.core.Constants;
import com.justjournal.model.Security;

import java.util.List;

import com.justjournal.model.api.SecurityTo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** @author Lucas Holt */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final List<SecurityTo> securityList = List.of(new SecurityTo(Security.PRIVATE.getId(), Security.PRIVATE.getName()),
            new SecurityTo(Security.FRIENDS.getId(), Security.FRIENDS.getName()),
            new SecurityTo(Security.PUBLIC.getId(), Security.PUBLIC.getName()));


    @GetMapping(value = "{id}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<SecurityTo> getById(@PathVariable(PARAM_ID) final Integer id) {

        try {
            var val = Security.fromValue(id);
            SecurityTo s = securityList.stream().filter(p -> p.getId() == val.getId()).findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid id"));
            return ResponseEntity.ok().eTag(Integer.toString(s.hashCode())).body(s);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(headers = Constants.HEADER_ACCEPT_ALL, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SecurityTo>> getSecurityList() {
        return ResponseEntity.ok().eTag(Integer.toString(securityList.hashCode())).body(securityList);
    }
}