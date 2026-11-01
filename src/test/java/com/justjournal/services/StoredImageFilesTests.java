package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class StoredImageFilesTests {

  private ImageStorageService storage;
  private StoredImageFiles files;

  @BeforeEach
  void setUp() {
    storage = mock(ImageStorageService.class);
    files = new StoredImageFiles(storage);
    files.setBucket("pics");
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static ErrorResponseException minioError(final String code) {
    final ErrorResponse response = mock(ErrorResponse.class);
    when(response.code()).thenReturn(code);
    final ErrorResponseException e = mock(ErrorResponseException.class);
    when(e.errorResponse()).thenReturn(response);
    return e;
  }

  @Test
  void get_returnsTheStream() throws Exception {
    final InputStream stream = new ByteArrayInputStream(new byte[] {1});
    when(storage.downloadFile("pics", "a.jpg")).thenReturn(stream);

    assertEquals(stream, files.get("a.jpg"));
  }

  @Test
  void get_noSuchKey_notFound() throws Exception {
    final ErrorResponseException missing = minioError("NoSuchKey");
    when(storage.downloadFile("pics", "a.jpg")).thenThrow(missing);

    assertThrows(NotFoundException.class, () -> files.get("a.jpg"));
  }

  @Test
  void get_otherError_serviceException() throws Exception {
    final ErrorResponseException denied = minioError("AccessDenied");
    when(storage.downloadFile("pics", "a.jpg")).thenThrow(denied);

    assertThrows(ServiceException.class, () -> files.get("a.jpg"));
  }

  @Test
  void put_failure_serviceException() throws Exception {
    doThrow(new IOException("down")).when(storage).uploadFile(any(), any(), any(), any());

    final ServiceException e =
        assertThrows(ServiceException.class, () -> files.put("a.jpg", "image/jpeg", new byte[] {1}));
    assertTrue(e.getMessage().endsWith("Unable to store picture"), e.getMessage());
  }

  @Test
  void remove_failure_ignored() throws Exception {
    doThrow(new MinioException("down")).when(storage).deleteFile("pics", "a.jpg");

    files.remove("a.jpg");

    verify(storage).deleteFile("pics", "a.jpg");
  }

  @Test
  void removeAfterCommit_noTransaction_removedNowSkippingNulls() throws Exception {
    files.removeAfterCommit("a.jpg", null, "b.jpg");

    verify(storage).deleteFile("pics", "a.jpg");
    verify(storage).deleteFile("pics", "b.jpg");
    verify(storage, never()).deleteFile("pics", null);
  }

  @Test
  void removeAfterCommit_inTransaction_waitsForCommit() throws Exception {
    TransactionSynchronizationManager.initSynchronization();

    files.removeAfterCommit("a.jpg");
    verify(storage, never()).deleteFile(any(), any());

    for (final TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
      s.afterCommit();
    }
    verify(storage).deleteFile("pics", "a.jpg");
  }
}
