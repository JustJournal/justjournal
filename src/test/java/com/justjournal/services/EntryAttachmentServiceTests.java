package com.justjournal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justjournal.core.Settings;
import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryImage;
import com.justjournal.model.EntryLink;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.User;
import com.justjournal.model.api.EntryTo;
import com.justjournal.repository.EntryImageRepository;
import com.justjournal.repository.EntryLinkRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.services.EntryAttachmentService.EntryAttachments;
import com.justjournal.services.EntryAttachmentService.Prepared;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.LinkSafetyService;
import com.justjournal.services.link.NormalizedUrl;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EntryAttachmentServiceTests {

  private static final int OWNER = 5;
  private static final int OTHER = 9;
  private static final String LINK = "https://example.com/story";

  private EntryImageRepository images;
  private EntryLinkRepository links;
  private EntryRepository entries;
  private EntryImageService entryImageService;
  private EntryAccessService access;
  private StoredImageFiles files;
  private LinkSafetyService safety;
  private EntryAttachmentService service;
  private Entry entry;

  @BeforeEach
  void setUp() {
    images = mock(EntryImageRepository.class);
    links = mock(EntryLinkRepository.class);
    entries = mock(EntryRepository.class);
    entryImageService = mock(EntryImageService.class);
    access = mock(EntryAccessService.class);
    files = mock(StoredImageFiles.class);
    safety = mock(LinkSafetyService.class);
    final Settings settings = mock(Settings.class);
    when(settings.getBaseUri()).thenReturn("https://www.justjournal.com/");
    service =
        new EntryAttachmentService(
            images, links, entries, entryImageService, access, files, safety, settings);

    final User owner = new User();
    owner.setId(OWNER);
    entry = new Entry();
    entry.setId(30);
    entry.setUser(owner);
    when(images.findByEntryId(anyInt())).thenReturn(Optional.empty());
    when(links.findByEntryId(anyInt())).thenReturn(List.of());
  }

  private static EntryImage image(final int id, final int ownerId, final Integer entryId) {
    final EntryImage image = new EntryImage();
    image.setId(id);
    image.setOwnerId(ownerId);
    image.setEntryId(entryId);
    image.setFilename("entry/" + ownerId + "/" + id + ".jpg");
    image.setDisplay("entry/" + ownerId + "/" + id + "_display.jpg");
    image.setMimeType("image/jpeg");
    image.setSize(1234);
    image.setWidth(800);
    image.setHeight(600);
    image.setModerationStatus(ModerationStatus.APPROVED);
    return image;
  }

  private static EntryLink link(final int id, final String url, final LinkPreviewStatus status) {
    final EntryLink link = new EntryLink();
    link.setId(id);
    link.setOwnerId(OWNER);
    link.setEntryId(30);
    link.setUrl(url);
    link.setPreviewStatus(status);
    return link;
  }

  private static NormalizedUrl normalized(final String url) {
    return new NormalizedUrl(URI.create(url), URI.create(url), URI.create(url).getHost());
  }

  // prepare

  @Test
  void ownFreePictureCanBeAttached() {
    when(images.findById(3)).thenReturn(Optional.of(image(3, OWNER, null)));

    final Prepared prepared = service.prepare(OWNER, 0, 3, "a cat", null);

    assertEquals(3, prepared.imageId());
    assertFalse(prepared.linkChanged());
  }

  @Test
  void someoneElsesPictureIsRefused() {
    when(images.findById(3)).thenReturn(Optional.of(image(3, OTHER, null)));

    assertThrows(BadRequestException.class, () -> service.prepare(OWNER, 0, 3, null, null));
  }

  @Test
  void pictureOnAnotherEntryIsRefused() {
    when(images.findById(3)).thenReturn(Optional.of(image(3, OWNER, 31)));

    assertThrows(BadRequestException.class, () -> service.prepare(OWNER, 30, 3, null, null));
  }

  @Test
  void pictureAlreadyOnThisEntryIsFine() {
    when(images.findById(3)).thenReturn(Optional.of(image(3, OWNER, 30)));

    assertEquals(3, service.prepare(OWNER, 30, 3, null, null).imageId());
  }

  @Test
  void missingPictureIsRefused() {
    when(images.findById(3)).thenReturn(Optional.empty());

    assertThrows(BadRequestException.class, () -> service.prepare(OWNER, 0, 3, null, null));
  }

  @Test
  void overlongAltIsRefused() {
    final String alt = "x".repeat(EntryImageService.MAX_ALT_LENGTH + 1);

    assertThrows(BadRequestException.class, () -> service.prepare(OWNER, 0, null, alt, null));
  }

  @Test
  void newLinkIsChecked() throws Exception {
    when(safety.checkForSave(LINK)).thenReturn(normalized(LINK));

    final Prepared prepared = service.prepare(OWNER, 0, null, null, "  " + LINK + " ");

    assertTrue(prepared.linkChanged());
    assertEquals(URI.create(LINK), prepared.link().href());
  }

  @Test
  void refusedLinkBecomesABadRequestWithTheCheckersMessage() throws Exception {
    when(safety.checkForSave("https://bit.ly/x"))
        .thenThrow(
            new LinkRejectedException(
                LinkRejectedException.Reason.SHORTENER, LinkRejectedException.SHORTENER_MESSAGE));

    final BadRequestException e =
        assertThrows(
            BadRequestException.class,
            () -> service.prepare(OWNER, 0, null, null, "https://bit.ly/x"));
    assertEquals(LinkRejectedException.SHORTENER_MESSAGE, e.getMessage());
  }

  @Test
  void unchangedLinkIsNotCheckedAgain() throws Exception {
    when(links.findByEntryId(30)).thenReturn(List.of(link(4, LINK, LinkPreviewStatus.READY)));

    final Prepared prepared = service.prepare(OWNER, 30, null, null, LINK);

    assertFalse(prepared.linkChanged());
    verify(safety, never()).checkForSave(anyString());
  }

  @Test
  void blankLinkRemovesIt() throws Exception {
    final Prepared prepared = service.prepare(OWNER, 30, null, null, "  ");

    assertTrue(prepared.linkChanged());
    assertNull(prepared.link());
    verify(safety, never()).checkForSave(anyString());
  }

  // apply

  @Test
  void attachesTheNewPictureAndRemovesTheOldOne() {
    final EntryImage old = image(2, OWNER, 30);
    final EntryImage fresh = image(3, OWNER, null);
    when(images.findByEntryId(30)).thenReturn(Optional.of(old));
    when(images.findById(3)).thenReturn(Optional.of(fresh));

    assertTrue(service.apply(entry, new Prepared(3, " a cat ", false, null)));

    verify(images).delete(old);
    verify(entryImageService).deleteFiles(old);
    verify(images).flush();
    assertEquals(30, fresh.getEntryId());
    assertEquals("a cat", fresh.getAltText());
    verify(images).save(fresh);
  }

  @Test
  void zeroRemovesThePicture() {
    final EntryImage old = image(2, OWNER, 30);
    when(images.findByEntryId(30)).thenReturn(Optional.of(old));

    assertTrue(service.apply(entry, new Prepared(0, null, false, null)));

    verify(images).delete(old);
    verify(images, never()).save(any());
  }

  @Test
  void keepingThePictureOnlyUpdatesItsAlt() {
    final EntryImage current = image(2, OWNER, 30);
    when(images.findByEntryId(30)).thenReturn(Optional.of(current));

    // no description before or after: nothing changed
    assertFalse(service.apply(entry, new Prepared(2, "", false, null)));

    verify(images, never()).delete(any());
    assertNull(current.getAltText());
    verify(images).save(current);
  }

  @Test
  void newDescriptionOnTheSamePicture_isAChange() {
    final EntryImage current = image(2, OWNER, 30);
    when(images.findByEntryId(30)).thenReturn(Optional.of(current));

    assertTrue(service.apply(entry, new Prepared(2, "a dog", false, null)));
    assertFalse(service.apply(entry, new Prepared(null, " a dog ", false, null)));

    assertEquals("a dog", current.getAltText());
  }

  @Test
  void pictureClaimedMeanwhileIsNotTaken() {
    when(images.findById(3)).thenReturn(Optional.of(image(3, OWNER, 31)));

    assertFalse(service.apply(entry, new Prepared(3, null, false, null)));

    verify(images, never()).save(any());
  }

  @Test
  void newLinkReplacesTheOldWithAPendingRow() {
    final EntryLink old = link(4, "https://old.example/", LinkPreviewStatus.READY);
    old.setImageFilename("link/5/old.jpg");
    when(links.findByEntryId(30)).thenReturn(List.of(old));

    assertTrue(service.apply(entry, new Prepared(null, null, true, normalized(LINK))));

    verify(links).delete(old);
    verify(files).removeAfterCommit("link/5/old.jpg");
    final ArgumentCaptor<EntryLink> saved = ArgumentCaptor.forClass(EntryLink.class);
    verify(links).save(saved.capture());
    assertEquals(LINK, saved.getValue().getUrl());
    assertEquals(30, saved.getValue().getEntryId());
    assertEquals(OWNER, saved.getValue().getOwnerId());
    assertEquals(LinkPreviewStatus.PENDING, saved.getValue().getPreviewStatus());
  }

  @Test
  void unchangedLinkIsLeftAlone() {
    assertFalse(service.apply(entry, Prepared.UNCHANGED));

    verify(links, never()).delete(any());
    verify(links, never()).save(any());
  }

  // reading

  @Test
  void forEntriesGroupsByEntry() {
    final Entry second = new Entry();
    second.setId(31);
    second.setUser(entry.getUser());
    final EntryImage picture = image(3, OWNER, 30);
    final EntryLink onFirst = link(4, LINK, LinkPreviewStatus.READY);
    final EntryLink onSecond = link(5, LINK, LinkPreviewStatus.PENDING);
    onSecond.setEntryId(31);
    when(images.findByEntryIdIn(List.of(30, 31))).thenReturn(List.of(picture));
    when(links.findByEntryIdIn(List.of(30, 31))).thenReturn(List.of(onFirst, onSecond));

    final Map<Integer, EntryAttachments> result = service.forEntries(List.of(entry, second));

    assertSame(picture, result.get(30).image());
    assertSame(onFirst, result.get(30).link());
    assertNull(result.get(31).image());
    assertSame(onSecond, result.get(31).link());
  }

  @Test
  void feedCarriesOnlyWhatAnyoneMaySee() {
    final EntryImage picture = image(3, OWNER, 30);
    picture.setAltText("a cat");
    final EntryLink card = link(4, LINK, LinkPreviewStatus.READY);
    card.setTitle("A story");
    card.setImageFilename("link/5/x.jpg");
    card.setImageModerationStatus(ModerationStatus.REVIEW);
    when(images.findByEntryIdIn(any())).thenReturn(List.of(picture));
    when(links.findByEntryIdIn(any())).thenReturn(List.of(card));
    when(access.isPubliclyVisible(entry)).thenReturn(true);
    when(entryImageService.publicCacheable(picture, entry)).thenReturn(true);

    final FeedAttachments feed = service.forFeed(List.of(entry)).get(30);

    assertEquals("https://www.justjournal.com/EntryImage/3", feed.imageUrl());
    assertEquals("https://www.justjournal.com/EntryImage/3/display", feed.imageThumbnailUrl());
    assertEquals(1234, feed.imageLength());
    assertEquals("a cat", feed.imageAlt());
    assertEquals(LINK, feed.linkUrl());
    assertEquals("A story", feed.linkTitle());
    // the preview picture is still waiting for review
    assertNull(feed.linkImageUrl());
  }

  @Test
  void feedLeavesOutPrivateEntriesHeldPicturesAndBlockedLinks() {
    final Entry hidden = new Entry();
    hidden.setId(31);
    hidden.setUser(entry.getUser());
    final EntryImage held = image(3, OWNER, 30);
    final EntryLink blocked = link(4, LINK, LinkPreviewStatus.BLOCKED);
    final EntryLink onHidden = link(5, LINK, LinkPreviewStatus.READY);
    onHidden.setEntryId(31);
    when(images.findByEntryIdIn(any())).thenReturn(List.of(held));
    when(links.findByEntryIdIn(any())).thenReturn(List.of(blocked, onHidden));
    when(access.isPubliclyVisible(entry)).thenReturn(true);
    when(access.isPubliclyVisible(hidden)).thenReturn(false);
    when(entryImageService.publicCacheable(held, entry)).thenReturn(false);

    assertTrue(service.forFeed(List.of(entry, hidden)).isEmpty());
  }

  @Test
  void decorateShowsTheOwnerTheirHeldPictureAndBlockedLink() {
    final EntryImage held = image(3, OWNER, 30);
    held.setModerationStatus(ModerationStatus.REVIEW);
    when(images.findByEntryId(30)).thenReturn(Optional.of(held));
    when(links.findByEntryId(30)).thenReturn(List.of(link(4, LINK, LinkPreviewStatus.BLOCKED)));
    when(entryImageService.visible(held, entry, OWNER)).thenReturn(true);
    final EntryTo to = EntryTo.builder().build();

    service.decorate(entry, to, OWNER);

    assertEquals(3, to.getImageId());
    assertEquals("/EntryImage/3/display", to.getImageUrl());
    assertTrue(to.getImageNotice() != null && !to.getImageNotice().isBlank());
    assertEquals(LINK, to.getLink());
    assertEquals("BLOCKED", to.getLinkStatus());
  }

  @Test
  void decorateHidesHeldPictureAndBlockedLinkFromOthers() {
    final EntryImage held = image(3, OWNER, 30);
    held.setModerationStatus(ModerationStatus.REVIEW);
    when(images.findByEntryId(30)).thenReturn(Optional.of(held));
    when(links.findByEntryId(30)).thenReturn(List.of(link(4, LINK, LinkPreviewStatus.BLOCKED)));
    when(entryImageService.visible(held, entry, OTHER)).thenReturn(false);
    final EntryTo to = EntryTo.builder().build();

    service.decorate(entry, to, OTHER);

    assertNull(to.getImageId());
    assertNull(to.getLink());
  }

  // link preview pictures

  private EntryLink readyCard(final ModerationStatus status) {
    final EntryLink card = link(4, LINK, LinkPreviewStatus.READY);
    card.setImageFilename("link/5/x.jpg");
    card.setImageModerationStatus(status);
    when(links.findById(4)).thenReturn(Optional.of(card));
    when(entries.findById(30)).thenReturn(Optional.of(entry));
    return card;
  }

  @Test
  void approvedPreviewPictureOnAPublicEntryIsCacheable() throws Exception {
    readyCard(ModerationStatus.APPROVED);
    final InputStream stream = new ByteArrayInputStream(new byte[] {1});
    when(files.get("link/5/x.jpg")).thenReturn(stream);
    when(access.canView(entry, 0)).thenReturn(true);
    when(access.isPubliclyVisible(entry)).thenReturn(true);

    final EntryImageService.Picture picture = service.linkImage(4, 0);

    assertSame(stream, picture.stream());
    assertTrue(picture.publicCacheable());
  }

  @Test
  void heldPreviewPictureIsOnlyForTheOwner() throws Exception {
    readyCard(ModerationStatus.REVIEW);
    when(access.canView(any(), anyInt())).thenReturn(true);

    assertThrows(NotFoundException.class, () -> service.linkImage(4, OTHER));
    assertFalse(service.linkImage(4, OWNER).publicCacheable());
  }

  @Test
  void previewPictureOnAnEntryTheReaderCannotSeeIsHidden() {
    readyCard(ModerationStatus.APPROVED);
    when(access.canView(entry, OTHER)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> service.linkImage(4, OTHER));
  }

  @Test
  void linkWithoutAPictureIsNotFound() {
    when(links.findById(4)).thenReturn(Optional.of(link(4, LINK, LinkPreviewStatus.READY)));

    assertThrows(NotFoundException.class, () -> service.linkImage(4, OWNER));
  }

  // deleting

  @Test
  void deleteForEntryRemovesBoth() {
    final EntryImage picture = image(3, OWNER, 30);
    final EntryLink card = link(4, LINK, LinkPreviewStatus.READY);
    when(images.findByEntryId(30)).thenReturn(Optional.of(picture));
    when(links.findByEntryId(30)).thenReturn(List.of(card));

    service.deleteForEntry(30);

    verify(images).delete(picture);
    verify(entryImageService).deleteFiles(picture);
    verify(links).delete(card);
  }

  @Test
  void deleteAllForOwnerIncludesUnattachedUploads() {
    final EntryImage free = image(3, OWNER, null);
    when(images.findByOwnerId(OWNER)).thenReturn(List.of(free));
    when(links.findByOwnerId(OWNER)).thenReturn(List.of());

    service.deleteAllForOwner(OWNER);

    verify(images).delete(free);
  }

  @Test
  void removeOrphansCountsWhatItRemoved() {
    when(images.findByEntryIdIsNullAndCreatedBefore(any(), any()))
        .thenReturn(List.of(image(3, OWNER, null)));
    final EntryLink orphan = link(4, LINK, LinkPreviewStatus.READY);
    orphan.setEntryId(null);
    when(links.findByEntryIdIsNull(any())).thenReturn(List.of(orphan));

    assertEquals(2, service.removeOrphans());
    verify(links).delete(orphan);
  }
}
