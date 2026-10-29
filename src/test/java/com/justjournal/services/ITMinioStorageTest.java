package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.*;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in compatibility test against a disposable MinIO server. Set JJ_MINIO_TEST_ENDPOINT,
 * JJ_MINIO_TEST_ACCESS_KEY, and JJ_MINIO_TEST_SECRET_KEY, then run with
 * {@code ./mvnw -Ptest -Dtest=ITMinioStorageTest -Dmaven.test.failure.ignore=false test}.
 * Only the randomly named bucket created by this test is changed or removed.
 */
@EnabledIfEnvironmentVariable(named = "JJ_MINIO_TEST_ENDPOINT", matches = ".+")
class ITMinioStorageTest {

  @Test
  void storageOperationsRoundTrip() throws Exception {
    try (MinioClient client =
        MinioClient.builder()
            .endpoint(System.getenv("JJ_MINIO_TEST_ENDPOINT"))
            .credentials(
                System.getenv("JJ_MINIO_TEST_ACCESS_KEY"),
                System.getenv("JJ_MINIO_TEST_SECRET_KEY"))
            .build()) {
      ImageStorageService storage = new ImageStorageService(client, null, null, null);
      String bucket = "jj-sdk-test-" + UUID.randomUUID();
      String[] objects = {"known-length", "multipart-stream"};
      assertFalse(client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
      try {
        for (int index = 0; index < objects.length; index++) {
          String object = objects[index];
          byte[] content = new byte[index == 0 ? 32 : 10 * 1024 * 1024 + 123];
          new Random(42).nextBytes(content);
          // Exercise both service branches, including a stream larger than one multipart part.
          try (InputStream input =
              index == 0
                  ? new ByteArrayInputStream(content)
                  : new FilterInputStream(new ByteArrayInputStream(content)) {}) {
            storage.uploadFile(bucket, object, "image/png", input);
          }
          assertTrue(client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
          var metadata =
              client.statObject(StatObjectArgs.builder().bucket(bucket).object(object).build());
          assertEquals(content.length, metadata.size());
          assertEquals("image/png", metadata.contentType());
          try (InputStream downloaded = storage.downloadFile(bucket, object)) {
            assertArrayEquals(content, downloaded.readAllBytes());
          }
          storage.deleteFile(bucket, object);
          ErrorResponseException missing =
              assertThrows(
                  ErrorResponseException.class,
                  () -> storage.downloadFile(bucket, object));
          assertEquals("NoSuchKey", missing.errorResponse().code());
        }
      } finally {
        if (client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
          for (String object : objects) storage.deleteFile(bucket, object);
          client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
      }
    }
  }
}
