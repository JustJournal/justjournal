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


import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Lucas Holt
 */
@Slf4j
@Configuration
public class MinioConfig {
    @Value("${app.minio.host:localhost}")
    private String minioHost;

    @Value("${app.minio.port:9000}")
    private int port;

    @Value("${app.minio.secure:false}")
    private Boolean secure;

    @Value("${app.minio.accessKey}")
    private String minioAccessKey;

    @Value("${app.minio.secretKey}")
    private String minioSecretKey;


    @Bean
    public MinioClient minioClient() {
        log.info("Initializing MinIO client with host: {}, port: {}, secure: {}, accessKey: {}", minioHost, port, secure, minioAccessKey);
        if (StringUtils.isEmpty(minioAccessKey) || StringUtils.isEmpty(minioSecretKey)) {
            log.warn("MinIO access key or secret key not provided. Using anonymous access.");
            return MinioClient.builder()                    .
                    endpoint(minioHost, port, secure)
                    .build();
        } else {
            return MinioClient.builder()
                    .endpoint(minioHost, port, secure)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();
        }
    }
}
