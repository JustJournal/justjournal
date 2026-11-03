package com.justjournal.services;

import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.EntryImage;
import com.justjournal.model.EntryLink;
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.User;
import com.justjournal.model.UserImage;
import com.justjournal.model.UserPic;
import com.justjournal.repository.EntryImageRepository;
import com.justjournal.repository.EntryLinkRepository;
import com.justjournal.repository.UserImageRepository;
import com.justjournal.repository.UserPicRepository;
import com.justjournal.repository.UserRepository;
import com.justjournal.repository.cache.RecentBlogsRepository;
import com.justjournal.services.activitypub.ActivityPublisher;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The administrator's decisions on pictures the content filter held for review: album pictures,
 * avatars, post pictures and link preview pictures. A held picture is shown only to its owner until
 * it is approved here; a rejected one stays hidden. The model's category is kept either way.
 *
 * @author Lucas Holt
 */
@Slf4j
@Service
public class AdminModerationService {

  /** Most pictures of each kind listed at once, oldest first. */
  public static final int MAX_PER_KIND = 50;

  /** The kinds of picture that can be held, by the name used in admin addresses. */
  public enum Kind {
    ALBUM("album", "Album picture"),
    AVATAR("avatar", "Avatar"),
    POST("post", "Post picture"),
    LINK("link", "Link preview");

    private final String path;
    private final String label;

    Kind(final String path, final String label) {
      this.path = path;
      this.label = label;
    }

    /** The name used in admin addresses. */
    public String getPath() {
      return path;
    }

    public String getLabel() {
      return label;
    }

    /** @return the kind with this address name, if there is one */
    public static Optional<Kind> fromPath(final String path) {
      return Arrays.stream(values()).filter(kind -> kind.path.equals(path)).findFirst();
    }
  }

  /**
   * A picture held for review, as the admin list shows it.
   *
   * @param id the picture's row; for an avatar, its user's id
   * @param owner the owner's username, or null if the account is gone
   * @param category why the filter held it, or null
   * @param where the page it appears on once approved, or null if it isn't on one
   */
  public record HeldPicture(
      Kind kind, int id, int ownerId, String owner, String category, String where) {}

  /** A held picture's data, as stored, and its mime type. */
  public record HeldFile(InputStream stream, String mimeType) {}

  private final UserImageRepository userImageRepository;
  private final UserPicRepository userPicRepository;
  private final EntryImageRepository entryImageRepository;
  private final EntryLinkRepository entryLinkRepository;
  private final UserRepository userRepository;
  private final AlbumImageService albumImageService;
  private final ImageStorageService imageStorageService;
  private final StoredImageFiles files;
  private final RecentBlogsRepository recentBlogsRepository;
  private final ActivityPublisher activityPublisher;

  public AdminModerationService(
      final UserImageRepository userImageRepository,
      final UserPicRepository userPicRepository,
      final EntryImageRepository entryImageRepository,
      final EntryLinkRepository entryLinkRepository,
      final UserRepository userRepository,
      final AlbumImageService albumImageService,
      final ImageStorageService imageStorageService,
      final StoredImageFiles files,
      final RecentBlogsRepository recentBlogsRepository,
      final ActivityPublisher activityPublisher) {
    this.userImageRepository = userImageRepository;
    this.userPicRepository = userPicRepository;
    this.entryImageRepository = entryImageRepository;
    this.entryLinkRepository = entryLinkRepository;
    this.userRepository = userRepository;
    this.albumImageService = albumImageService;
    this.imageStorageService = imageStorageService;
    this.files = files;
    this.recentBlogsRepository = recentBlogsRepository;
    this.activityPublisher = activityPublisher;
  }

  /** Up to {@link #MAX_PER_KIND} held pictures of each kind, oldest first. */
  @Transactional(readOnly = true)
  public List<HeldPicture> held() {
    final PageRequest page = PageRequest.of(0, MAX_PER_KIND);
    final List<UserImage> pictures =
        userImageRepository.findByModerationStatusOrderByIdAsc(ModerationStatus.REVIEW, page);
    final List<UserPic> avatars =
        userPicRepository.findByModerationStatusOrderByModifiedAsc(ModerationStatus.REVIEW, page);
    final List<EntryImage> postPictures =
        entryImageRepository.findByModerationStatusOrderByIdAsc(ModerationStatus.REVIEW, page);
    final List<EntryLink> links =
        entryLinkRepository.findByImageModerationStatusAndImageFilenameIsNotNullOrderByIdAsc(
            ModerationStatus.REVIEW, page);

    final Set<Integer> ownerIds = new HashSet<>();
    pictures.forEach(picture -> ownerIds.add(albumOwner(picture)));
    avatars.forEach(avatar -> ownerIds.add(avatar.getId()));
    postPictures.forEach(picture -> ownerIds.add(picture.getOwnerId()));
    links.forEach(link -> ownerIds.add(link.getOwnerId()));
    final Map<Integer, String> names = new HashMap<>();
    for (final User user : userRepository.findAllById(ownerIds)) {
      names.put(user.getId(), user.getUsername());
    }

    final List<HeldPicture> held = new ArrayList<>();
    for (final UserImage picture : pictures) {
      final String owner = names.get(albumOwner(picture));
      held.add(
          new HeldPicture(
              Kind.ALBUM,
              picture.getId(),
              albumOwner(picture),
              owner,
              picture.getModerationCategory(),
              owner == null ? null : "/users/" + owner + "/pictures/" + picture.getId()));
    }
    for (final UserPic avatar : avatars) {
      final String owner = names.get(avatar.getId());
      held.add(
          new HeldPicture(
              Kind.AVATAR,
              avatar.getId(),
              avatar.getId(),
              owner,
              avatar.getModerationCategory(),
              owner == null ? null : "/users/" + owner));
    }
    for (final EntryImage picture : postPictures) {
      final String owner = names.get(picture.getOwnerId());
      held.add(
          new HeldPicture(
              Kind.POST,
              picture.getId(),
              picture.getOwnerId(),
              owner,
              picture.getModerationCategory(),
              entryPage(owner, picture.getEntryId())));
    }
    for (final EntryLink link : links) {
      final String owner = names.get(link.getOwnerId());
      held.add(
          new HeldPicture(
              Kind.LINK,
              link.getId(),
              link.getOwnerId(),
              owner,
              link.getImageModerationCategory(),
              entryPage(owner, link.getEntryId())));
    }
    return held;
  }

  /**
   * The stored file of a picture that is held for review, for the administrator to look at.
   *
   * @throws NotFoundException if there is no such picture, or it isn't held for review
   */
  public HeldFile file(final Kind kind, final int id) throws ServiceException {
    switch (kind) {
      case ALBUM -> {
        final UserImage picture =
            userImageRepository
                .findById(id)
                .filter(AdminModerationService::held)
                .filter(image -> image.getUser() != null)
                .orElseThrow(() -> notHeld(kind, id));
        // the owner may see their own held picture
        final AlbumImageService.Picture stored =
            albumImageService.picture(id, picture.getUser().getId());
        return new HeldFile(stored.stream(), stored.mimeType());
      }
      case AVATAR -> {
        userPicRepository
            .findById(id)
            .filter(AdminModerationService::held)
            .orElseThrow(() -> notHeld(kind, id));
        final ImageStorageService.StoredAvatar avatar = imageStorageService.downloadAvatar(id);
        return new HeldFile(avatar.stream(), avatar.mimeType());
      }
      case POST -> {
        final EntryImage picture =
            entryImageRepository
                .findById(id)
                .filter(AdminModerationService::held)
                .orElseThrow(() -> notHeld(kind, id));
        // the picture as uploaded, as the filter judged it
        return new HeldFile(files.get(picture.getFilename()), picture.getMimeType());
      }
      case LINK -> {
        final EntryLink link =
            entryLinkRepository
                .findById(id)
                .filter(AdminModerationService::held)
                .orElseThrow(() -> notHeld(kind, id));
        // preview pictures are always stored as JPEG
        return new HeldFile(files.get(link.getImageFilename()), MediaType.IMAGE_JPEG_VALUE);
      }
      default -> throw notHeld(kind, id);
    }
  }

  /**
   * Approve or reject a held picture. Nothing changes if it is no longer held, such as when the
   * content filter or another admin decided first, or the owner deleted it.
   *
   * @param decision APPROVED or REJECTED
   * @param admin who decided, for the log
   * @return true if the picture was held and now has the decision
   * @throws IllegalArgumentException if the decision is anything else
   */
  @Transactional
  public boolean decide(
      final Kind kind, final int id, final ModerationStatus decision, final String admin) {
    if (decision != ModerationStatus.APPROVED && decision != ModerationStatus.REJECTED) {
      throw new IllegalArgumentException("A decision is APPROVED or REJECTED");
    }
    final boolean approved = decision == ModerationStatus.APPROVED;
    final boolean changed =
        switch (kind) {
          case ALBUM -> decideAlbum(id, decision);
          case AVATAR -> decideAvatar(id, decision, approved);
          case POST -> decidePost(id, decision, approved);
          case LINK -> decideLink(id, decision, approved);
        };
    if (changed) {
      log.info("{} {} {} by admin {}", kind.getLabel(), id, decision, admin);
    } else {
      log.info(
          "{} {} is no longer held for review; {} by admin {} ignored",
          kind.getLabel(),
          id,
          decision,
          admin);
    }
    return changed;
  }

  /** Album pictures are read live by the pictures page and feed; nothing else to refresh. */
  private boolean decideAlbum(final int id, final ModerationStatus decision) {
    return userImageRepository
        .findById(id)
        .filter(AdminModerationService::held)
        .map(
            picture -> {
              picture.setModerationStatus(decision);
              userImageRepository.save(picture);
              return true;
            })
        .orElse(false);
  }

  private boolean decideAvatar(
      final int userId, final ModerationStatus decision, final boolean approved) {
    return userPicRepository
        .findById(userId)
        .filter(AdminModerationService::held)
        .map(
            avatar -> {
              avatar.setModerationStatus(decision);
              userPicRepository.save(avatar);
              // an approved avatar is now the actor's icon
              if (approved) activityPublisher.profileChanged(userId);
              return true;
            })
        .orElse(false);
  }

  private boolean decidePost(
      final int id, final ModerationStatus decision, final boolean approved) {
    return entryImageRepository
        .findById(id)
        .filter(AdminModerationService::held)
        .map(
            picture -> {
              picture.setModerationStatus(decision);
              entryImageRepository.save(picture);
              // an approved picture now shows on the post, and in feeds of it
              if (approved && picture.getEntryId() != null) {
                activityPublisher.entryAttachmentsChanged(picture.getEntryId());
                forgetRecentBlogs();
              }
              return true;
            })
        .orElse(false);
  }

  private boolean decideLink(
      final int id, final ModerationStatus decision, final boolean approved) {
    return entryLinkRepository
        .findById(id)
        .filter(AdminModerationService::held)
        .map(
            link -> {
              link.setImageModerationStatus(decision);
              entryLinkRepository.save(link);
              // the card's picture now shows in feeds; it isn't federated, so nothing to publish
              if (approved && link.getEntryId() != null) forgetRecentBlogs();
              return true;
            })
        .orElse(false);
  }

  /** The cached recent posts feed carries approved post and link pictures; drop it once saved. */
  private void forgetRecentBlogs() {
    StoredImageFiles.afterCommit(() -> recentBlogsRepository.delete().subscribe());
  }

  private static int albumOwner(final UserImage picture) {
    return picture.getUser() == null ? 0 : picture.getUser().getId();
  }

  private static String entryPage(final String owner, final Integer entryId) {
    return owner == null || entryId == null ? null : "/users/" + owner + "/entry/" + entryId;
  }

  private static NotFoundException notHeld(final Kind kind, final int id) {
    return new NotFoundException("No " + kind.getPath() + " picture " + id + " held for review");
  }

  private static boolean held(final UserImage picture) {
    return picture.getModerationStatus() == ModerationStatus.REVIEW;
  }

  private static boolean held(final UserPic avatar) {
    return avatar.getModerationStatus() == ModerationStatus.REVIEW;
  }

  private static boolean held(final EntryImage picture) {
    return picture.getModerationStatus() == ModerationStatus.REVIEW;
  }

  private static boolean held(final EntryLink link) {
    return link.getImageModerationStatus() == ModerationStatus.REVIEW
        && link.getImageFilename() != null;
  }
}
