package com.justjournal.services;

import com.justjournal.core.Settings;
import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.Entry;
import com.justjournal.model.EntryImage;
import com.justjournal.model.EntryLink;
import com.justjournal.model.FeedAttachments;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.api.EntryTo;
import com.justjournal.repository.EntryImageRepository;
import com.justjournal.repository.EntryLinkRepository;
import com.justjournal.repository.EntryRepository;
import com.justjournal.services.link.LinkRejectedException;
import com.justjournal.services.link.LinkSafetyService;
import com.justjournal.services.link.NormalizedUrl;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The picture and link attached to a blog entry. Pictures are uploaded on their own first (see
 * {@link EntryImageService}) and then claimed by the entry that refers to them when it is saved;
 * links are checked when the entry is saved and previewed later by {@link LinkPreviewService}.
 *
 * <p>Attachments live in their own tables rather than on the entry, so saving an entry from a
 * client that knows nothing about them, such as XML-RPC, leaves them alone.
 */
@Slf4j
@Service
public class EntryAttachmentService {

  /** What is attached to one entry; either may be null. */
  public record EntryAttachments(EntryImage image, EntryLink link) {
    public static final EntryAttachments NONE = new EntryAttachments(null, null);

    public boolean isEmpty() {
      return image == null && link == null;
    }
  }

  /**
   * A checked request to change an entry's attachments, made before the entry is saved so a bad
   * link refuses the save.
   *
   * @param imageId null to leave the picture alone, 0 to remove it, or the upload to attach
   * @param imageAlt null to leave the description alone
   * @param linkChanged whether the link is to change
   * @param link the new link, or null to remove it
   */
  public record Prepared(Integer imageId, String imageAlt, boolean linkChanged, NormalizedUrl link) {
    public static final Prepared UNCHANGED = new Prepared(null, null, false, null);
  }

  static final String INVALID_PICTURE_MESSAGE = "That picture can't be added to this entry.";

  private final EntryImageRepository entryImageRepository;
  private final EntryLinkRepository entryLinkRepository;
  private final EntryRepository entryRepository;
  private final EntryImageService entryImageService;
  private final EntryAccessService entryAccessService;
  private final StoredImageFiles files;
  private final LinkSafetyService linkSafetyService;
  private final Settings settings;

  /** Pictures uploaded but never put on an entry are removed after this many hours. */
  @Setter
  @Value("${entry-attachments.image.orphan-hours:24}")
  private int orphanHours = 24;

  public EntryAttachmentService(
      final EntryImageRepository entryImageRepository,
      final EntryLinkRepository entryLinkRepository,
      final EntryRepository entryRepository,
      final EntryImageService entryImageService,
      final EntryAccessService entryAccessService,
      final StoredImageFiles files,
      final LinkSafetyService linkSafetyService,
      final Settings settings) {
    this.entryImageRepository = entryImageRepository;
    this.entryLinkRepository = entryLinkRepository;
    this.entryRepository = entryRepository;
    this.entryImageService = entryImageService;
    this.entryAccessService = entryAccessService;
    this.files = files;
    this.linkSafetyService = linkSafetyService;
    this.settings = settings;
  }

  /**
   * Check a change to an entry's attachments before the entry is saved. The link is checked only
   * when it differs from the one already on the entry, so editing an old entry doesn't fail on a
   * site that has since gone away.
   *
   * @param userId the author
   * @param entryId the entry being edited, or 0 for a new one
   * @param imageId see {@link Prepared#imageId()}
   * @param imageAlt see {@link Prepared#imageAlt()}
   * @param link null to leave the link alone, blank to remove it
   * @throws BadRequestException with a message for the author when the picture or link can't be
   *     used
   */
  @Transactional(readOnly = true)
  public Prepared prepare(
      final int userId,
      final int entryId,
      final Integer imageId,
      final String imageAlt,
      final String link) {
    if (imageId != null && imageId > 0) {
      final EntryImage image =
          entryImageRepository
              .findById(imageId)
              .orElseThrow(() -> new BadRequestException(INVALID_PICTURE_MESSAGE));
      final boolean free = image.getEntryId() == null;
      final boolean ours = entryId > 0 && Integer.valueOf(entryId).equals(image.getEntryId());
      if (image.getOwnerId() != userId || !(free || ours)) {
        throw new BadRequestException(INVALID_PICTURE_MESSAGE);
      }
    }
    if (imageAlt != null && imageAlt.trim().length() > EntryImageService.MAX_ALT_LENGTH) {
      throw new BadRequestException(
          "The picture description can be at most "
              + EntryImageService.MAX_ALT_LENGTH
              + " characters.");
    }

    if (link == null) return new Prepared(imageId, imageAlt, false, null);
    final String wanted = link.trim();
    if (wanted.isEmpty()) return new Prepared(imageId, imageAlt, true, null);

    if (entryId > 0) {
      final boolean same =
          entryLinkRepository.findByEntryId(entryId).stream()
              .anyMatch(l -> wanted.equals(l.getUrl()));
      if (same) return new Prepared(imageId, imageAlt, false, null);
    }
    try {
      return new Prepared(imageId, imageAlt, true, linkSafetyService.checkForSave(wanted));
    } catch (final LinkRejectedException e) {
      log.info("Refused link for user {}: {}", userId, e.reason());
      throw new BadRequestException(e.getMessage());
    }
  }

  /**
   * Apply a prepared change to a saved entry. Anything replaced is deleted, its files once the
   * transaction commits.
   */
  @Transactional
  public void apply(final Entry saved, final Prepared prepared) {
    final int entryId = saved.getId();
    final int ownerId = saved.getUser().getId();

    final Optional<EntryImage> current = entryImageRepository.findByEntryId(entryId);
    final Integer wantedImage = prepared.imageId();
    if (wantedImage != null) {
      final boolean keep = current.isPresent() && current.get().getId() == wantedImage;
      if (!keep) {
        current.ifPresent(this::deleteImage);
        // the unique key on entry_id needs the old row gone before the new one takes its place
        entryImageRepository.flush();
        if (wantedImage > 0) {
          entryImageRepository
              .findById(wantedImage)
              .filter(image -> image.getOwnerId() == ownerId && image.getEntryId() == null)
              .ifPresent(
                  image -> {
                    image.setEntryId(entryId);
                    setAlt(image, prepared.imageAlt());
                    entryImageRepository.save(image);
                  });
        }
      } else {
        setAlt(current.get(), prepared.imageAlt());
        entryImageRepository.save(current.get());
      }
    } else if (prepared.imageAlt() != null && current.isPresent()) {
      setAlt(current.get(), prepared.imageAlt());
      entryImageRepository.save(current.get());
    }

    if (prepared.linkChanged()) {
      entryLinkRepository.findByEntryId(entryId).forEach(this::deleteLink);
      if (prepared.link() != null) {
        final EntryLink link = new EntryLink();
        link.setOwnerId(ownerId);
        link.setEntryId(entryId);
        link.setUrl(prepared.link().href().toString());
        link.setPreviewStatus(LinkPreviewStatus.PENDING);
        link.setNextAttemptAt(new Date());
        entryLinkRepository.save(link);
      }
    }
  }

  private static void setAlt(final EntryImage image, final String alt) {
    if (alt == null) return;
    final String trimmed = alt.trim();
    image.setAltText(trimmed.isEmpty() ? null : trimmed);
  }

  /** The attachments of each entry that has any, by entry id. */
  @Transactional(readOnly = true)
  public Map<Integer, EntryAttachments> forEntries(final Collection<Entry> entries) {
    final List<Integer> ids = entries.stream().map(Entry::getId).toList();
    final Map<Integer, EntryAttachments> result = new HashMap<>();
    if (ids.isEmpty()) return result;

    for (final EntryImage image : entryImageRepository.findByEntryIdIn(ids)) {
      result.put(image.getEntryId(), new EntryAttachments(image, null));
    }
    for (final EntryLink link : entryLinkRepository.findByEntryIdIn(ids)) {
      final EntryAttachments had = result.getOrDefault(link.getEntryId(), EntryAttachments.NONE);
      result.put(link.getEntryId(), new EntryAttachments(had.image(), link));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public EntryAttachments forEntry(final int entryId) {
    final EntryImage image = entryImageRepository.findByEntryId(entryId).orElse(null);
    final EntryLink link = entryLinkRepository.findByEntryId(entryId).stream().findFirst().orElse(null);
    return new EntryAttachments(image, link);
  }

  /**
   * What feeds may carry for each entry: only approved pictures and links that weren't blocked,
   * on entries anyone may read.
   */
  @Transactional(readOnly = true)
  public Map<Integer, FeedAttachments> forFeed(final Collection<Entry> entries) {
    final Map<Integer, EntryAttachments> all = forEntries(entries);
    final Map<Integer, FeedAttachments> result = new HashMap<>();
    for (final Entry entry : entries) {
      final EntryAttachments a = all.get(entry.getId());
      if (a == null || !entryAccessService.isPubliclyVisible(entry)) continue;
      final FeedAttachments feed = feedAttachments(entry, a);
      if (feed.hasImage() || feed.hasLink()) result.put(entry.getId(), feed);
    }
    return result;
  }

  private FeedAttachments feedAttachments(final Entry entry, final EntryAttachments a) {
    final String base = settings.getBaseUri();
    final EntryImage image = a.image();
    final boolean showImage = image != null && entryImageService.publicCacheable(image, entry);
    final EntryLink link = a.link();
    final boolean showLink = link != null && link.getPreviewStatus() != LinkPreviewStatus.BLOCKED;
    final boolean showLinkImage = showLink && linkImageApproved(link);
    return new FeedAttachments(
        showImage ? base + "EntryImage/" + image.getId() : null,
        showImage ? base + "EntryImage/" + image.getId() + "/display" : null,
        showImage ? image.getMimeType() : null,
        showImage ? image.getSize() : 0,
        showImage ? image.getWidth() : 0,
        showImage ? image.getHeight() : 0,
        showImage ? image.getAltText() : null,
        showLink ? link.getUrl() : null,
        showLink ? linkTitle(link) : null,
        showLink ? link.getDescription() : null,
        showLinkImage ? base + "EntryLinkImage/" + link.getId() : null,
        showLinkImage ? nullToZero(link.getImageWidth()) : 0,
        showLinkImage ? nullToZero(link.getImageHeight()) : 0);
  }

  /** The page's title, else the link itself. */
  public static String linkTitle(final EntryLink link) {
    final String title = link.getTitle();
    return title == null || title.isBlank() ? link.getUrl() : title;
  }

  /** Whether the link's preview picture exists and may be shown to anyone. */
  public static boolean linkImageApproved(final EntryLink link) {
    return link.getImageFilename() != null
        && link.getImageModerationStatus() == ModerationStatus.APPROVED;
  }

  private static int nullToZero(final Integer value) {
    return value == null ? 0 : value;
  }

  /**
   * The preview picture of a link, for someone allowed to see it: its owner, or anyone who may read
   * the entry once the picture is approved.
   *
   * @param viewerId the logged in user, or 0
   * @throws NotFoundException if there is no such picture, or the viewer may not see it
   */
  @Transactional(readOnly = true)
  public EntryImageService.Picture linkImage(final int linkId, final int viewerId)
      throws ServiceException {
    final EntryLink link =
        entryLinkRepository
            .findById(linkId)
            .orElseThrow(() -> new NotFoundException("No picture " + linkId));
    if (link.getImageFilename() == null) throw new NotFoundException("No picture " + linkId);
    final Entry entry =
        link.getEntryId() == null ? null : entryRepository.findById(link.getEntryId()).orElse(null);

    final boolean attached =
        entry != null
            && link.getEntryId() != null
            && link.getEntryId() == entry.getId()
            && entry.getUser().getId() == link.getOwnerId();
    final boolean owner = viewerId > 0 && viewerId == link.getOwnerId();
    final boolean shown =
        attached
            && linkImageApproved(link)
            && link.getPreviewStatus() == LinkPreviewStatus.READY
            && entryAccessService.canView(entry, viewerId);
    if (!owner && !shown) throw new NotFoundException("No picture " + linkId);

    final boolean publicCacheable = shown && entryAccessService.isPubliclyVisible(entry);
    return new EntryImageService.Picture(
        files.get(link.getImageFilename()), MediaType.IMAGE_JPEG_VALUE, publicCacheable);
  }

  /**
   * Fill in what the editor and API clients need to know about an entry's attachments. Only what
   * the viewer may see is included.
   */
  @Transactional(readOnly = true)
  public void decorate(final Entry entry, final EntryTo to, final int viewerId) {
    final EntryAttachments a = forEntry(entry.getId());
    final boolean owner = viewerId > 0 && viewerId == entry.getUser().getId();

    final EntryImage image = a.image();
    if (image != null && entryImageService.visible(image, entry, viewerId)) {
      to.setImageId(image.getId());
      to.setImageAlt(image.getAltText());
      to.setImageUrl("/EntryImage/" + image.getId() + "/display");
      if (owner && image.getModerationStatus() != ModerationStatus.APPROVED) {
        to.setImageNotice(
            "The content filter wasn't sure about this picture, so only you can see it until it"
                + " has been reviewed.");
      }
    }

    final EntryLink link = a.link();
    if (link != null && (owner || link.getPreviewStatus() != LinkPreviewStatus.BLOCKED)) {
      to.setLink(link.getUrl());
      to.setLinkStatus(link.getPreviewStatus().name());
    }
  }

  /** Delete what is attached to an entry, before the entry itself is deleted. */
  @Transactional
  public void deleteForEntry(final int entryId) {
    entryImageRepository.findByEntryId(entryId).ifPresent(this::deleteImage);
    entryLinkRepository.findByEntryId(entryId).forEach(this::deleteLink);
  }

  /** Delete every attachment a user has, attached or not, when the account is deleted. */
  @Transactional
  public void deleteAllForOwner(final int ownerId) {
    entryImageRepository.findByOwnerId(ownerId).forEach(this::deleteImage);
    entryLinkRepository.findByOwnerId(ownerId).forEach(this::deleteLink);
  }

  /**
   * Remove pictures uploaded but never put on an entry, and attachments whose entry is gone. Works
   * in batches, so a large backlog is cleared over several runs.
   *
   * @return how many were removed
   */
  @Transactional
  public int removeOrphans() {
    final Date cutoff = new Date(System.currentTimeMillis() - orphanHours * 3_600_000L);
    final List<EntryImage> images =
        entryImageRepository.findByEntryIdIsNullAndCreatedBefore(cutoff, PageRequest.of(0, 100));
    images.forEach(this::deleteImage);
    final List<EntryLink> links = entryLinkRepository.findByEntryIdIsNull(PageRequest.of(0, 100));
    links.forEach(this::deleteLink);
    return images.size() + links.size();
  }

  private void deleteImage(final EntryImage image) {
    entryImageRepository.delete(image);
    entryImageService.deleteFiles(image);
  }

  private void deleteLink(final EntryLink link) {
    entryLinkRepository.delete(link);
    files.removeAfterCommit(link.getImageFilename());
  }
}
