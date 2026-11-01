package com.justjournal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.User;
import com.justjournal.model.UserImage;
import com.justjournal.model.UserPic;
import com.justjournal.repository.UserImageRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.services.AlbumImageService;
import com.justjournal.services.ImageModerationService;
import com.justjournal.services.ImageStorageService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ModerationRetryTests {

  private static final String ERROR = ImageModerationService.CATEGORY_ERROR;

  private UserImageRepository userImageRepository;
  private UserPicRepository userPicRepository;
  private AlbumImageService albumImageService;
  private ImageStorageService imageStorageService;
  private ImageModerationService moderation;
  private ModerationRetry retry;

  @BeforeEach
  void setUp() {
    userImageRepository = mock(UserImageRepository.class);
    userPicRepository = mock(UserPicRepository.class);
    albumImageService = mock(AlbumImageService.class);
    imageStorageService = mock(ImageStorageService.class);
    moderation = mock(ImageModerationService.class);
    final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

    retry =
        new ModerationRetry(
            userImageRepository,
            userPicRepository,
            albumImageService,
            imageStorageService,
            moderation,
            new TransactionTemplate(transactionManager));
    retry.setEnabled(true);
  }

  private static UserImage heldPicture(final int id, final int ownerId) {
    final User owner = new User();
    owner.setId(ownerId);
    final UserImage image = new UserImage();
    image.setId(id);
    image.setUser(owner);
    image.setModerationStatus(ModerationStatus.REVIEW);
    image.setModerationCategory(ERROR);
    return image;
  }

  private static UserPic heldAvatar(final int userId) {
    final UserPic avatar = new UserPic();
    avatar.setId(userId);
    avatar.setModerationStatus(ModerationStatus.REVIEW);
    avatar.setModerationCategory(ERROR);
    return avatar;
  }

  private void heldPictures(final UserImage... images) throws Exception {
    when(userImageRepository.findByModerationStatusAndModerationCategoryOrderByIdAsc(
            eq(ModerationStatus.REVIEW), eq(ERROR), any()))
        .thenReturn(List.of(images));
    for (final UserImage image : images) {
      when(albumImageService.picture(image.getId(), image.getUser().getId()))
          .thenReturn(
              new AlbumImageService.Picture(
                  new ByteArrayInputStream(new byte[] {1, 2, 3}), "image/jpeg", false));
      when(userImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
    }
  }

  private void heldAvatars(final UserPic... avatars) throws Exception {
    when(userPicRepository.findByModerationStatusAndModerationCategoryOrderByModifiedAsc(
            eq(ModerationStatus.REVIEW), eq(ERROR), any()))
        .thenReturn(List.of(avatars));
    for (final UserPic avatar : avatars) {
      when(imageStorageService.downloadAvatar(avatar.getId()))
          .thenReturn(
              new ImageStorageService.StoredAvatar(
                  new ByteArrayInputStream(new byte[] {4, 5, 6}), "image/png"));
      when(userPicRepository.findById(avatar.getId())).thenReturn(Optional.of(avatar));
    }
  }

  private void verdict(final ModerationStatus status, final String category) {
    when(moderation.moderate(any(byte[].class)))
        .thenReturn(new ImageModerationService.Result(status, category, "test"));
  }

  @Test
  void run_filterOff_asksNothing() {
    retry.setEnabled(false);

    retry.run();

    verify(userImageRepository, never())
        .findByModerationStatusAndModerationCategoryOrderByIdAsc(any(), any(), any());
    verify(moderation, never()).moderate(any(byte[].class));
  }

  @Test
  void run_errorHeldPictureAndAvatar_judgedAgainAndApproved() throws Exception {
    final UserImage picture = heldPicture(7, 3);
    final UserPic avatar = heldAvatar(9);
    heldPictures(picture);
    heldAvatars(avatar);
    verdict(ModerationStatus.APPROVED, "safe");

    retry.run();

    assertEquals(ModerationStatus.APPROVED, picture.getModerationStatus());
    assertEquals("safe", picture.getModerationCategory());
    verify(userImageRepository).save(picture);
    assertEquals(ModerationStatus.APPROVED, avatar.getModerationStatus());
    assertEquals("safe", avatar.getModerationCategory());
    verify(userPicRepository).save(avatar);
  }

  @Test
  void run_modelHoldsItAgain_recordedWithTheNewReason() throws Exception {
    final UserImage picture = heldPicture(7, 3);
    heldPictures(picture);
    verdict(ModerationStatus.REVIEW, "suggestive");

    retry.run();

    assertEquals(ModerationStatus.REVIEW, picture.getModerationStatus());
    assertEquals("suggestive", picture.getModerationCategory());
    verify(userImageRepository).save(picture);
  }

  @Test
  void run_filterStillDown_stopsWithoutChangingAnything() throws Exception {
    heldPictures(heldPicture(7, 3), heldPicture(8, 3));
    verdict(ModerationStatus.REVIEW, ERROR);

    retry.run();

    verify(moderation).moderate(any(byte[].class));
    verify(userImageRepository, never()).save(any());
    verify(userPicRepository, never())
        .findByModerationStatusAndModerationCategoryOrderByModifiedAsc(any(), any(), any());
  }

  @Test
  void run_decidedByAPersonMeanwhile_notOverwritten() throws Exception {
    final UserImage picture = heldPicture(7, 3);
    heldPictures(picture);
    final UserImage approvedMeanwhile = heldPicture(7, 3);
    approvedMeanwhile.setModerationStatus(ModerationStatus.APPROVED);
    approvedMeanwhile.setModerationCategory(ERROR);
    when(userImageRepository.findById(7)).thenReturn(Optional.of(approvedMeanwhile));
    verdict(ModerationStatus.REJECTED, "nudity");

    retry.run();

    assertEquals(ModerationStatus.APPROVED, approvedMeanwhile.getModerationStatus());
    verify(userImageRepository, never()).save(any());
  }

  @Test
  void run_unreadablePicture_skipped_othersStillJudged() throws Exception {
    final UserImage missing = heldPicture(7, 3);
    final UserImage fine = heldPicture(8, 4);
    heldPictures(missing, fine);
    when(albumImageService.picture(7, 3)).thenThrow(new NotFoundException("Picture file missing"));
    verdict(ModerationStatus.APPROVED, "safe");

    retry.run();

    verify(userImageRepository, never()).save(missing);
    verify(userImageRepository).save(fine);
    verify(moderation).moderate(any(byte[].class));
  }

  @Test
  void run_onlyErrorHeldItemsAreAskedFor() throws Exception {
    retry.run();

    verify(userImageRepository)
        .findByModerationStatusAndModerationCategoryOrderByIdAsc(
            eq(ModerationStatus.REVIEW), eq(ERROR), any());
    verify(userPicRepository)
        .findByModerationStatusAndModerationCategoryOrderByModifiedAsc(
            eq(ModerationStatus.REVIEW), eq(ERROR), any());
    verify(albumImageService, never()).picture(anyInt(), anyInt());
  }
}
