package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.justjournal.exception.NotFoundException;
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
import com.justjournal.services.AdminModerationService.HeldFile;
import com.justjournal.services.AdminModerationService.HeldPicture;
import com.justjournal.services.AdminModerationService.Kind;
import com.justjournal.services.activitypub.ActivityPublisher;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

class AdminModerationServiceTests {

  private static final ModerationStatus REVIEW = ModerationStatus.REVIEW;
  private static final ModerationStatus APPROVED = ModerationStatus.APPROVED;
  private static final ModerationStatus REJECTED = ModerationStatus.REJECTED;

  private UserImageRepository userImageRepository;
  private UserPicRepository userPicRepository;
  private EntryImageRepository entryImageRepository;
  private EntryLinkRepository entryLinkRepository;
  private UserRepository userRepository;
  private AlbumImageService albumImageService;
  private ImageStorageService imageStorageService;
  private StoredImageFiles files;
  private RecentBlogsRepository recentBlogsRepository;
  private ActivityPublisher activityPublisher;
  private AdminModerationService service;

  @BeforeEach
  void setUp() {
    userImageRepository = mock(UserImageRepository.class);
    userPicRepository = mock(UserPicRepository.class);
    entryImageRepository = mock(EntryImageRepository.class);
    entryLinkRepository = mock(EntryLinkRepository.class);
    userRepository = mock(UserRepository.class);
    albumImageService = mock(AlbumImageService.class);
    imageStorageService = mock(ImageStorageService.class);
    files = mock(StoredImageFiles.class);
    recentBlogsRepository = mock(RecentBlogsRepository.class);
    activityPublisher = mock(ActivityPublisher.class);
    when(recentBlogsRepository.delete()).thenReturn(Mono.just(true));

    service =
        new AdminModerationService(
            userImageRepository,
            userPicRepository,
            entryImageRepository,
            entryLinkRepository,
            userRepository,
            albumImageService,
            imageStorageService,
            files,
            recentBlogsRepository,
            activityPublisher);
  }

  private static User user(final int id, final String username) {
    final User user = new User();
    user.setId(id);
    user.setUsername(username);
    return user;
  }

  private static UserImage picture(final int id, final int ownerId, final ModerationStatus status) {
    final UserImage image = new UserImage();
    image.setId(id);
    image.setUser(user(ownerId, "owner" + ownerId));
    image.setMimeType("image/png");
    image.setModerationStatus(status);
    image.setModerationCategory("sexy");
    return image;
  }

  private static UserPic avatar(final int userId, final ModerationStatus status) {
    final UserPic avatar = new UserPic();
    avatar.setId(userId);
    avatar.setModerationStatus(status);
    avatar.setModerationCategory("error");
    return avatar;
  }

  private static EntryImage postPicture(
      final int id, final Integer entryId, final ModerationStatus status) {
    final EntryImage image = new EntryImage();
    image.setId(id);
    image.setOwnerId(5);
    image.setEntryId(entryId);
    image.setFilename("entry/5/a.gif");
    image.setDisplay("entry/5/a_display.jpg");
    image.setMimeType("image/gif");
    image.setModerationStatus(status);
    image.setModerationCategory("animated");
    return image;
  }

  private static EntryLink link(
      final int id, final Integer entryId, final ModerationStatus status) {
    final EntryLink link = new EntryLink();
    link.setId(id);
    link.setOwnerId(6);
    link.setEntryId(entryId);
    link.setUrl("https://example.com/");
    link.setImageFilename("link/6/b.jpg");
    link.setImageModerationStatus(status);
    link.setImageModerationCategory("sexy");
    return link;
  }

  // held

  @Test
  void held_listsEveryKindWithOwnerAndWhereItAppears() {
    when(userImageRepository.findByModerationStatusOrderByIdAsc(eq(REVIEW), any()))
        .thenReturn(List.of(picture(1, 4, REVIEW)));
    when(userPicRepository.findByModerationStatusOrderByModifiedAsc(eq(REVIEW), any()))
        .thenReturn(List.of(avatar(4, REVIEW)));
    when(entryImageRepository.findByModerationStatusOrderByIdAsc(eq(REVIEW), any()))
        .thenReturn(List.of(postPicture(2, 30, REVIEW), postPicture(3, null, REVIEW)));
    when(entryLinkRepository.findByImageModerationStatusAndImageFilenameIsNotNullOrderByIdAsc(
            eq(REVIEW), any()))
        .thenReturn(List.of(link(9, 31, REVIEW)));
    when(userRepository.findAllById(any()))
        .thenReturn(List.of(user(4, "alice"), user(5, "bob"), user(6, "carol")));

    final List<HeldPicture> held = service.held();

    assertEquals(
        List.of(
            new HeldPicture(Kind.ALBUM, 1, 4, "alice", "sexy", "/users/alice/pictures/1"),
            new HeldPicture(Kind.AVATAR, 4, 4, "alice", "error", "/users/alice"),
            new HeldPicture(Kind.POST, 2, 5, "bob", "animated", "/users/bob/entry/30"),
            new HeldPicture(Kind.POST, 3, 5, "bob", "animated", null),
            new HeldPicture(Kind.LINK, 9, 6, "carol", "sexy", "/users/carol/entry/31")),
        held);
  }

  @Test
  void held_capsEachKind() {
    service.held();

    verify(userImageRepository)
        .findByModerationStatusOrderByIdAsc(
            REVIEW, Pageable.ofSize(AdminModerationService.MAX_PER_KIND));
    verify(entryLinkRepository)
        .findByImageModerationStatusAndImageFilenameIsNotNullOrderByIdAsc(
            REVIEW, Pageable.ofSize(AdminModerationService.MAX_PER_KIND));
  }

  @Test
  void held_ownerGone_noLink() {
    when(entryImageRepository.findByModerationStatusOrderByIdAsc(eq(REVIEW), any()))
        .thenReturn(List.of(postPicture(2, 30, REVIEW)));

    final HeldPicture held = service.held().get(0);

    assertNull(held.owner());
    assertNull(held.where());
  }

  // file

  @Test
  void file_album_readAsItsOwner() throws Exception {
    when(userImageRepository.findById(1)).thenReturn(Optional.of(picture(1, 4, REVIEW)));
    when(albumImageService.picture(1, 4))
        .thenReturn(
            new AlbumImageService.Picture(
                new ByteArrayInputStream(new byte[] {1, 2}), "image/png", false));

    final HeldFile file = service.file(Kind.ALBUM, 1);

    assertEquals("image/png", file.mimeType());
    assertArrayEquals(new byte[] {1, 2}, file.stream().readAllBytes());
  }

  @Test
  void file_avatar() throws Exception {
    when(userPicRepository.findById(4)).thenReturn(Optional.of(avatar(4, REVIEW)));
    when(imageStorageService.downloadAvatar(4))
        .thenReturn(
            new ImageStorageService.StoredAvatar(
                new ByteArrayInputStream(new byte[] {3}), "image/jpeg"));

    assertEquals("image/jpeg", service.file(Kind.AVATAR, 4).mimeType());
  }

  @Test
  void file_post_originalWithStoredType() throws Exception {
    when(entryImageRepository.findById(2)).thenReturn(Optional.of(postPicture(2, 30, REVIEW)));
    when(files.get("entry/5/a.gif")).thenReturn(new ByteArrayInputStream(new byte[] {4}));

    final HeldFile file = service.file(Kind.POST, 2);

    assertEquals("image/gif", file.mimeType());
    assertArrayEquals(new byte[] {4}, file.stream().readAllBytes());
  }

  @Test
  void file_link_isJpeg() throws Exception {
    when(entryLinkRepository.findById(9)).thenReturn(Optional.of(link(9, 31, REVIEW)));
    when(files.get("link/6/b.jpg")).thenReturn(new ByteArrayInputStream(new byte[] {5}));

    assertEquals("image/jpeg", service.file(Kind.LINK, 9).mimeType());
  }

  @Test
  void file_notHeld_notFound() throws Exception {
    when(userImageRepository.findById(1)).thenReturn(Optional.of(picture(1, 4, APPROVED)));
    when(userPicRepository.findById(4)).thenReturn(Optional.of(avatar(4, REJECTED)));
    when(entryImageRepository.findById(2)).thenReturn(Optional.of(postPicture(2, 30, APPROVED)));
    final EntryLink noImage = link(9, 31, REVIEW);
    noImage.setImageFilename(null);
    when(entryLinkRepository.findById(9)).thenReturn(Optional.of(noImage));

    assertThrows(NotFoundException.class, () -> service.file(Kind.ALBUM, 1));
    assertThrows(NotFoundException.class, () -> service.file(Kind.AVATAR, 4));
    assertThrows(NotFoundException.class, () -> service.file(Kind.POST, 2));
    assertThrows(NotFoundException.class, () -> service.file(Kind.LINK, 9));
    assertThrows(NotFoundException.class, () -> service.file(Kind.POST, 99));
    verify(albumImageService, never()).picture(anyInt(), anyInt());
    verify(imageStorageService, never()).downloadAvatar(anyInt());
    verify(files, never()).get(anyString());
  }

  // decide

  @Test
  void decide_album_approved() {
    final UserImage image = picture(1, 4, REVIEW);
    when(userImageRepository.findById(1)).thenReturn(Optional.of(image));

    assertTrue(service.decide(Kind.ALBUM, 1, APPROVED, "admin"));

    assertEquals(APPROVED, image.getModerationStatus());
    assertEquals("sexy", image.getModerationCategory());
    verify(userImageRepository).save(image);
    verifyNoInteractions(activityPublisher, recentBlogsRepository);
  }

  @Test
  void decide_album_rejected() {
    final UserImage image = picture(1, 4, REVIEW);
    when(userImageRepository.findById(1)).thenReturn(Optional.of(image));

    assertTrue(service.decide(Kind.ALBUM, 1, REJECTED, "admin"));

    assertEquals(REJECTED, image.getModerationStatus());
    verify(userImageRepository).save(image);
  }

  @Test
  void decide_avatar_approved_publishesProfile() {
    final UserPic pic = avatar(4, REVIEW);
    when(userPicRepository.findById(4)).thenReturn(Optional.of(pic));

    assertTrue(service.decide(Kind.AVATAR, 4, APPROVED, "admin"));

    assertEquals(APPROVED, pic.getModerationStatus());
    assertEquals("error", pic.getModerationCategory());
    verify(userPicRepository).save(pic);
    verify(activityPublisher).profileChanged(4);
  }

  @Test
  void decide_avatar_rejected_publishesNothing() {
    final UserPic pic = avatar(4, REVIEW);
    when(userPicRepository.findById(4)).thenReturn(Optional.of(pic));

    assertTrue(service.decide(Kind.AVATAR, 4, REJECTED, "admin"));

    assertEquals(REJECTED, pic.getModerationStatus());
    verify(userPicRepository).save(pic);
    verifyNoInteractions(activityPublisher);
  }

  @Test
  void decide_post_approved_publishesAttachmentsAndForgetsRecentBlogs() {
    final EntryImage image = postPicture(2, 30, REVIEW);
    when(entryImageRepository.findById(2)).thenReturn(Optional.of(image));

    assertTrue(service.decide(Kind.POST, 2, APPROVED, "admin"));

    assertEquals(APPROVED, image.getModerationStatus());
    assertEquals("animated", image.getModerationCategory());
    verify(entryImageRepository).save(image);
    verify(activityPublisher).entryAttachmentsChanged(30);
    verify(recentBlogsRepository).delete();
  }

  @Test
  void decide_post_approvedWithoutEntry_publishesNothing() {
    final EntryImage image = postPicture(3, null, REVIEW);
    when(entryImageRepository.findById(3)).thenReturn(Optional.of(image));

    assertTrue(service.decide(Kind.POST, 3, APPROVED, "admin"));

    assertEquals(APPROVED, image.getModerationStatus());
    verifyNoInteractions(activityPublisher, recentBlogsRepository);
  }

  @Test
  void decide_post_rejected_publishesNothing() {
    final EntryImage image = postPicture(2, 30, REVIEW);
    when(entryImageRepository.findById(2)).thenReturn(Optional.of(image));

    assertTrue(service.decide(Kind.POST, 2, REJECTED, "admin"));

    assertEquals(REJECTED, image.getModerationStatus());
    verify(entryImageRepository).save(image);
    verifyNoInteractions(activityPublisher, recentBlogsRepository);
  }

  @Test
  void decide_link_approved_forgetsRecentBlogsOnly() {
    final EntryLink link = link(9, 31, REVIEW);
    when(entryLinkRepository.findById(9)).thenReturn(Optional.of(link));

    assertTrue(service.decide(Kind.LINK, 9, APPROVED, "admin"));

    assertEquals(APPROVED, link.getImageModerationStatus());
    assertEquals("sexy", link.getImageModerationCategory());
    verify(entryLinkRepository).save(link);
    verify(recentBlogsRepository).delete();
    verifyNoInteractions(activityPublisher);
  }

  @Test
  void decide_link_rejected() {
    final EntryLink link = link(9, 31, REVIEW);
    when(entryLinkRepository.findById(9)).thenReturn(Optional.of(link));

    assertTrue(service.decide(Kind.LINK, 9, REJECTED, "admin"));

    assertEquals(REJECTED, link.getImageModerationStatus());
    verify(entryLinkRepository).save(link);
    verifyNoInteractions(activityPublisher, recentBlogsRepository);
  }

  @Test
  void decide_notHeld_leftAlone() {
    final UserImage image = picture(1, 4, APPROVED);
    when(userImageRepository.findById(1)).thenReturn(Optional.of(image));
    final UserPic pic = avatar(4, REJECTED);
    when(userPicRepository.findById(4)).thenReturn(Optional.of(pic));
    final EntryImage postImage = postPicture(2, 30, APPROVED);
    when(entryImageRepository.findById(2)).thenReturn(Optional.of(postImage));
    final EntryLink link = link(9, 31, APPROVED);
    when(entryLinkRepository.findById(9)).thenReturn(Optional.of(link));

    assertFalse(service.decide(Kind.ALBUM, 1, REJECTED, "admin"));
    assertFalse(service.decide(Kind.AVATAR, 4, APPROVED, "admin"));
    assertFalse(service.decide(Kind.POST, 2, REJECTED, "admin"));
    assertFalse(service.decide(Kind.LINK, 9, REJECTED, "admin"));
    assertFalse(service.decide(Kind.POST, 99, APPROVED, "admin"));

    assertEquals(APPROVED, image.getModerationStatus());
    assertEquals(REJECTED, pic.getModerationStatus());
    assertEquals(APPROVED, postImage.getModerationStatus());
    assertEquals(APPROVED, link.getImageModerationStatus());
    verify(userImageRepository, never()).save(any());
    verify(userPicRepository, never()).save(any());
    verify(entryImageRepository, never()).save(any());
    verify(entryLinkRepository, never()).save(any());
    verifyNoInteractions(activityPublisher, recentBlogsRepository);
  }

  @Test
  void decide_review_refused() {
    assertThrows(
        IllegalArgumentException.class, () -> service.decide(Kind.ALBUM, 1, REVIEW, "admin"));
    assertThrows(
        IllegalArgumentException.class, () -> service.decide(Kind.ALBUM, 1, null, "admin"));
    verifyNoInteractions(userImageRepository);
  }

  @Test
  void kind_fromPath() {
    assertEquals(Optional.of(Kind.ALBUM), Kind.fromPath("album"));
    assertEquals(Optional.of(Kind.AVATAR), Kind.fromPath("avatar"));
    assertEquals(Optional.of(Kind.POST), Kind.fromPath("post"));
    assertEquals(Optional.of(Kind.LINK), Kind.fromPath("link"));
    assertEquals(Optional.empty(), Kind.fromPath("ALBUM"));
    assertEquals(Optional.empty(), Kind.fromPath("other"));
    assertEquals(Optional.empty(), Kind.fromPath(null));
  }
}
