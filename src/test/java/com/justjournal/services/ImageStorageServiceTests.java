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
package com.justjournal.services;

import com.justjournal.exception.ServiceException;
import com.justjournal.model.AvatarSource;
import com.justjournal.model.PrefBool;
import com.justjournal.model.UserPic;
import com.justjournal.model.UserPref;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserPrefRepository;
import io.minio.MinioClient;
import io.minio.errors.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** @author Lucas Holt */
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
class ImageStorageServiceTests {

  @Mock private MinioClient minioClient;

  @Mock private UserImageService userImageService;

  @Mock private UserPicRepository userPicRepository;

  @Mock private UserPrefRepository userPrefRepository;

  @InjectMocks private ImageStorageService imageStorageService;

  @BeforeEach
  void setup() {
    imageStorageService.setAvatarBucket("testa");
  }

  @Test
  void testDeleteNoUser() {
    Throwable exception = assertThrows(IllegalArgumentException.class, ()->{
      when(userPrefRepository.findById(anyInt())).thenReturn(Optional.empty());
      imageStorageService.deleteAvatar(1);
    });
    assertEquals("userId", exception.getMessage());
  }

  @Test
  void testDeleteAvatar() throws ServiceException {
    UserPref userPref = new UserPref();
    userPref.setShowAvatar(PrefBool.Y);

    UserPic userPic = new UserPic();
    userPic.setFilename("test");
    userPic.setId(1);
    userPic.setMimeType("image/jpeg");

    when(userPrefRepository.findById(anyInt())).thenReturn(Optional.of(userPref));
    when(userPicRepository.findById(anyInt())).thenReturn(Optional.of(userPic));

    imageStorageService.deleteAvatar(1);

    verify(userPrefRepository, times(1)).save(any());
  }

  @Test
  void testUploadAvatar() throws ServiceException {
    UserPref userPref = new UserPref();
    userPref.setShowAvatar(PrefBool.Y);

    UserPic userPic = new UserPic();
    userPic.setFilename("test");
    userPic.setId(1);
    userPic.setMimeType("image/jpeg");

    when(userPrefRepository.findById(anyInt())).thenReturn(Optional.of(userPref));
    when(userPicRepository.findById(anyInt())).thenReturn(Optional.of(userPic));

    byte[] arr = new byte[] {1, 2, 3, 4, 5, 0};

    imageStorageService.uploadAvatar(
        1, "image/jpeg", AvatarSource.UPLOAD, new ByteArrayInputStream(arr));
    verify(userPrefRepository, times(1)).save(any());
  }

  @Test
  public void testDownloadAvatar() throws Exception {
    UserPic userPic = new UserPic();
    userPic.setFilename("test");
    userPic.setId(1);
    userPic.setMimeType("image/jpeg");
    when(userPicRepository.findById(anyInt())).thenReturn(Optional.of(userPic));
    imageStorageService.downloadAvatar(1);

    verify(minioClient, times(1)).getObject(any());
  }

  @Test
  void testGetAvatarFileNameJpeg() {
    String result = imageStorageService.getAvatarFileName(1, "image/jpeg");
    assertEquals("avatar_1.jpg", result);
  }

  @Test
  void testGetAvatarFileNameJpg() {
    String result = imageStorageService.getAvatarFileName(1, "image/jpg");
    assertEquals("avatar_1.jpg", result);
  }

  @Test
  void testGetAvatarFileNameGif() {
    String result = imageStorageService.getAvatarFileName(1, "image/gif");
    assertEquals("avatar_1.gif", result);
  }

  @Test
  void testGetAvatarFileNamePng() {
    String result = imageStorageService.getAvatarFileName(1, "image/png");
    assertEquals("avatar_1.png", result);
  }

  @Test
  void testGetAvatarFileNamePing() {
    String result = imageStorageService.getAvatarFileName(1, "image/ping");
    assertEquals("avatar_1.png", result);
  }
}
