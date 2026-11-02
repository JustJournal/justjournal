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
package com.justjournal.services;

import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Picture files in the image bucket: album pictures, pictures attached to blog posts, link
 * previews and their thumbnails. Failures are logged here and reported with messages fit for the
 * user.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class StoredImageFiles {

  /** MinIO's error code for an object that doesn't exist. */
  private static final String NO_SUCH_KEY = "NoSuchKey";

  @Getter
  @Setter
  @Value("${bucket.image:jjimages}")
  private String bucket;

  private final ImageStorageService storage;

  public StoredImageFiles(final ImageStorageService storage) {
    this.storage = storage;
  }

  /** Store a file, replacing one with the same name. */
  public void put(final String name, final String mimeType, final byte[] data)
      throws ServiceException {
    try {
      storage.uploadFile(bucket, name, mimeType, new ByteArrayInputStream(data));
    } catch (final Exception e) {
      log.error("Could not store picture {}", name, e);
      throw new ServiceException("Unable to store picture");
    }
  }

  /**
   * @throws NotFoundException if there is no such file
   */
  public InputStream get(final String name) throws ServiceException {
    try {
      return storage.downloadFile(bucket, name);
    } catch (final ErrorResponseException e) {
      if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
        throw new NotFoundException("Picture file missing");
      }
      log.error("Could not fetch picture {}", name, e);
      throw new ServiceException("Unable to fetch picture");
    } catch (final Exception e) {
      log.error("Could not fetch picture {}", name, e);
      throw new ServiceException("Unable to fetch picture");
    }
  }

  /** Best effort; a leftover file only takes space. */
  public void remove(final String name) {
    try {
      storage.deleteFile(bucket, name);
    } catch (final Exception e) {
      log.warn("Could not delete picture file {}", name, e);
    }
  }

  /** Remove the files once the transaction commits, or now if there is none. Nulls are skipped. */
  public void removeAfterCommit(final String... names) {
    afterCommit(
        () -> {
          for (final String name : names) {
            if (name != null) remove(name);
          }
        });
  }

  /**
   * Run once the transaction commits, or now if there is none. Files are removed once the rows are
   * gone for good, not before a rollback could undo it.
   */
  public static void afterCommit(final Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
