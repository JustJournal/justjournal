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

import com.justjournal.model.UserImage;
import com.justjournal.repository.UserImageRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** @author Lucas Holt */
@Service
public class UserImageService {

  private final UserImageRepository userImageRepository;

  public UserImageService(UserImageRepository userImageRepository) {
    this.userImageRepository = userImageRepository;
  }

  public List<UserImage> getUserImages(final String username) {
    return userImageRepository.findByUsernameOrderByTitleTitleAsc(username);
  }

  /**
   * One picture with its neighbors in the gallery's title order, for the single picture page.
   *
   * @param username owner of the picture
   * @param id picture id
   * @return the picture, or empty if the user has no picture with that id
   */
  public Optional<PicturePage> getPicturePage(final String username, final int id) {
    final List<UserImage> images = getUserImages(username);
    for (int i = 0; i < images.size(); i++) {
      if (images.get(i).getId() == id) {
        return Optional.of(
            new PicturePage(
                images.get(i),
                i > 0 ? images.get(i - 1) : null,
                i < images.size() - 1 ? images.get(i + 1) : null));
      }
    }
    return Optional.empty();
  }

  /**
   * A picture and the pictures before and after it in the gallery.
   *
   * @param picture the picture
   * @param previous picture before it, or null if it's first
   * @param next picture after it, or null if it's last
   */
  public record PicturePage(UserImage picture, UserImage previous, UserImage next) {}
}
