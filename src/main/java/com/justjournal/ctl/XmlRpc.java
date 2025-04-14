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
package com.justjournal.ctl;

import com.justjournal.services.Blogger;
import com.justjournal.services.MetaWeblog;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import com.justjournal.xmlrpc.XmlRpcServlet;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * XML RPC endpoints for blogger and metaweblog legacy APIs.
 *
 * @author Lucas Holt
 */
@Slf4j
@Component
public final class XmlRpc extends XmlRpcServlet {
  private static final String BLOGGER = "blogger";
  private static final String METAWEBLOG = "metaWeblog";

  private final transient Blogger bloggerService;

  private final transient MetaWeblog metaWeblogService;

  public XmlRpc(@NotNull Blogger bloggerService, @NotNull MetaWeblog metaWeblogService) {
    this.bloggerService = bloggerService;
    this.metaWeblogService = metaWeblogService;
  }

  @Override
  public void init(final ServletConfig servletConfig) throws ServletException {
    super.init(servletConfig);

    log.info("Initializing XML RPC endpoints for blogger and metaWeblog legacy APIs...");

    // This is blogger 1.0 api which is no longer used by Google, but still popular with older
    // blogging clients.
    getXmlRpcServer().addInvocationHandler(BLOGGER, bloggerService);
    getXmlRpcServer().addInvocationHandler(METAWEBLOG, metaWeblogService);
  }
}
