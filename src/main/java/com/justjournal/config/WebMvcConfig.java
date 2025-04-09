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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final ObjectMapper mapper;

  @Autowired
  public WebMvcConfig(ObjectMapper mapper) {
    this.mapper = mapper;
    mapper.registerModule(new Hibernate5JakartaModule());
    JakartaXmlBindAnnotationModule module = new JakartaXmlBindAnnotationModule();
    mapper.registerModule(module);
  }

  /**
   * Override default handlers. see
   * http://stackoverflow.com/questions/24837715/spring-boot-with-angularjs-html5mode
   *
   * @param registry
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    int cachePeriod = 300;

    registry
        .addResourceHandler("/robots.txt")
        .addResourceLocations("classpath:/static/robots.txt")
            .setCacheControl(CacheControl.maxAge(Duration.ofSeconds(cachePeriod)));


    registry
        .addResourceHandler("/static/**")
        .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.maxAge(Duration.ofSeconds(cachePeriod)));


    registry
        .addResourceHandler("/static/styles/**", "/styles/*.css", "/styles/*.map")
        .addResourceLocations("classpath:/static/styles/")
            .setCacheControl(CacheControl.maxAge(Duration.ofSeconds(cachePeriod)));

    // todo: move these files to an external source
    registry
        .addResourceHandler(
            "/software/*.zip", "/software/*.bz2", "/software/*.gz", "/software/unix/**")
        .addResourceLocations("classpath:/static/software/")
            .setCacheControl(CacheControl.maxAge(Duration.ofSeconds(cachePeriod)));

  }

  /** {@inheritDoc} */
  @Override
  public void configureMessageConverters(final List<HttpMessageConverter<?>> converters) {
    converters.add(new StringHttpMessageConverter());
    converters.add(new ByteArrayHttpMessageConverter());
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    // Do any additional configuration here
    return builder.build();
  }

  @Bean
  public ShallowEtagHeaderFilter shallowEtagHeaderFilter() {
    return new ShallowEtagHeaderFilter();
  }
}
