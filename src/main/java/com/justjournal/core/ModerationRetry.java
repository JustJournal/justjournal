package com.justjournal.core;

import com.justjournal.model.ModerationStatus;
import com.justjournal.model.UserImage;
import com.justjournal.model.UserPic;
import com.justjournal.repository.UserImageRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.services.AlbumImageService;
import com.justjournal.services.ImageModerationService;
import com.justjournal.services.ImageStorageService;
import java.io.InputStream;
import java.util.List;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Judges again the pictures and avatars that were held for review only because the content filter
 * could not be asked when they were uploaded, such as when no Ollama server answered. Pictures the
 * model itself held, and anything a person has since approved, are left alone.
 *
 * <p>Files are read and the model is asked outside any transaction; each verdict is then written
 * in a short one of its own.
 */
@Slf4j
@Component
public class ModerationRetry {

  /** Most pictures, and most avatars, judged per run, so a long outage is worked off gradually. */
  static final int BATCH_SIZE = 25;

  private final UserImageRepository userImageRepository;
  private final UserPicRepository userPicRepository;
  private final AlbumImageService albumImageService;
  private final ImageStorageService imageStorageService;
  private final ImageModerationService moderation;
  private final TransactionTemplate transactionTemplate;

  /** Off when the filter is off: nothing new would be learnt by asking. */
  @Setter
  @Value("${moderation.enabled:false}")
  private boolean enabled;

  public ModerationRetry(
      final UserImageRepository userImageRepository,
      final UserPicRepository userPicRepository,
      final AlbumImageService albumImageService,
      final ImageStorageService imageStorageService,
      final ImageModerationService moderation,
      final TransactionTemplate transactionTemplate) {
    this.userImageRepository = userImageRepository;
    this.userPicRepository = userPicRepository;
    this.albumImageService = albumImageService;
    this.imageStorageService = imageStorageService;
    this.moderation = moderation;
    this.transactionTemplate = transactionTemplate;
  }

  /** A batch of pictures, then a batch of avatars, unless the filter turns out to be down. */
  @Scheduled(fixedDelay = 1000 * 60 * 15, initialDelay = 1000 * 60 * 3)
  public void run() {
    if (!enabled) return;
    if (retryPictures()) retryAvatars();
  }

  /**
   * @return false if the filter still cannot be reached, so there is no point going on
   */
  boolean retryPictures() {
    final List<UserImage> held =
        transactionTemplate.execute(
            status ->
                userImageRepository.findByModerationStatusAndModerationCategoryOrderByIdAsc(
                    ModerationStatus.REVIEW,
                    ImageModerationService.CATEGORY_ERROR,
                    PageRequest.of(0, BATCH_SIZE)));
    if (held == null) return true;

    for (final UserImage image : held) {
      final ImageModerationService.Result verdict;
      // the owner may see their own held picture, whatever its state
      try (InputStream stream =
          albumImageService.picture(image.getId(), image.getUser().getId()).stream()) {
        verdict = moderation.moderate(stream.readAllBytes());
      } catch (final Exception e) {
        log.warn("Could not read picture {} to judge it again: {}", image.getId(), e.getMessage());
        continue;
      }
      if (filterDown(verdict)) return false;

      transactionTemplate.executeWithoutResult(
          status ->
              userImageRepository
                  .findById(image.getId())
                  .filter(ModerationRetry::stillHeldForError)
                  .ifPresent(
                      current -> {
                        current.setModerationStatus(verdict.status());
                        current.setModerationCategory(verdict.category());
                        userImageRepository.save(current);
                        log.info(
                            "Picture {} judged again: {} ({})",
                            current.getId(),
                            verdict.status(),
                            verdict.category());
                      }));
    }
    return true;
  }

  void retryAvatars() {
    final List<UserPic> held =
        transactionTemplate.execute(
            status ->
                userPicRepository.findByModerationStatusAndModerationCategoryOrderByModifiedAsc(
                    ModerationStatus.REVIEW,
                    ImageModerationService.CATEGORY_ERROR,
                    PageRequest.of(0, BATCH_SIZE)));
    if (held == null) return;

    for (final UserPic avatar : held) {
      final ImageModerationService.Result verdict;
      try (InputStream stream = imageStorageService.downloadAvatar(avatar.getId()).stream()) {
        verdict = moderation.moderate(stream.readAllBytes());
      } catch (final Exception e) {
        log.warn(
            "Could not read the avatar of user {} to judge it again: {}",
            avatar.getId(),
            e.getMessage());
        continue;
      }
      if (filterDown(verdict)) return;

      transactionTemplate.executeWithoutResult(
          status ->
              userPicRepository
                  .findById(avatar.getId())
                  .filter(ModerationRetry::stillHeldForError)
                  .ifPresent(
                      current -> {
                        current.setModerationStatus(verdict.status());
                        current.setModerationCategory(verdict.category());
                        userPicRepository.save(current);
                        log.info(
                            "Avatar of user {} judged again: {} ({})",
                            current.getId(),
                            verdict.status(),
                            verdict.category());
                      }));
    }
  }

  private static boolean filterDown(final ImageModerationService.Result verdict) {
    if (!ImageModerationService.CATEGORY_ERROR.equals(verdict.category())) return false;
    log.info("The content filter is still unreachable; held pictures will be tried again later");
    return true;
  }

  /** Someone may have decided about the picture while the model was being asked. */
  private static boolean stillHeldForError(final UserImage image) {
    return image.getModerationStatus() == ModerationStatus.REVIEW
        && ImageModerationService.CATEGORY_ERROR.equals(image.getModerationCategory());
  }

  private static boolean stillHeldForError(final UserPic avatar) {
    return avatar.getModerationStatus() == ModerationStatus.REVIEW
        && ImageModerationService.CATEGORY_ERROR.equals(avatar.getModerationCategory());
  }
}
