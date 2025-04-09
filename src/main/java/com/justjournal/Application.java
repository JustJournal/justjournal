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
package com.justjournal;

import com.justjournal.core.Settings;
import com.justjournal.ctl.LoginAccount;
import com.justjournal.ctl.UpdateJournal;
import com.justjournal.ctl.XmlRpc;
import com.justjournal.repository.*;
import com.justjournal.services.BingService;
import com.justjournal.services.TrackbackService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableScheduling
@EnableAsync
@EnableTransactionManagement
@EnableJpaRepositories
@SpringBootApplication(
    exclude = {
      GsonAutoConfiguration.class,
      XADataSourceAutoConfiguration.class,
      Neo4jRepositoriesAutoConfiguration.class,
      MongoRepositoriesAutoConfiguration.class
    })
public class Application extends SpringBootServletInitializer {

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  public Login webLogin(UserRepository userRepository) {
    return new Login(userRepository);
  }

  @Bean
  public ServletRegistrationBean<UpdateJournal> updateJournalServlet(Settings settings, EntryRepository entryRepository, UserRepository user,
                                                                     LocationRepository locationDao, MoodRepository moodDao, Login webLogin, TrackbackService trackbackService, BingService bingService) {
    return new ServletRegistrationBean<>(new UpdateJournal(settings, entryRepository, user, locationDao, moodDao, webLogin, trackbackService, bingService), "/updateJournal");
  }

  @Bean
  public ServletRegistrationBean<LoginAccount> loginAccountServlet(Login webLogin) {
    return new ServletRegistrationBean<>(new LoginAccount(webLogin), "/loginAccount");
  }

  @Bean
  public ServletRegistrationBean<XmlRpc> xmlrpc() {
    return new ServletRegistrationBean<>(new XmlRpc(), "/xml-rpc/*");
  }
}
