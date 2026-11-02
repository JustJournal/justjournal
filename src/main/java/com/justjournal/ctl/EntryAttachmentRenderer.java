package com.justjournal.ctl;

import com.justjournal.model.Entry;
import com.justjournal.model.EntryImage;
import com.justjournal.model.EntryLink;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.model.ModerationStatus;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.EntryAttachmentService.EntryAttachments;
import com.justjournal.services.EntryImageService;
import com.justjournal.utility.Xml;
import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The HTML for the picture and link card shown between an entry's subject and its body. Everything
 * that came from a user or a linked site is escaped; the card always links to the address the
 * author gave, never one the linked page claims for itself.
 */
@Component
public class EntryAttachmentRenderer {

  private final EntryAccessService entryAccessService;
  private final EntryImageService entryImageService;

  public EntryAttachmentRenderer(
      final EntryAccessService entryAccessService, final EntryImageService entryImageService) {
    this.entryAccessService = entryAccessService;
    this.entryImageService = entryImageService;
  }

  /**
   * @param entry the entry the attachments belong to
   * @param attachments what is attached to it
   * @param viewerId the logged in user, or 0
   * @return the HTML, empty when there is nothing the viewer may see
   */
  public String html(final Entry entry, final EntryAttachments attachments, final int viewerId) {
    if (attachments == null || attachments.isEmpty()) return "";
    if (!entryAccessService.canView(entry, viewerId)) return "";

    final boolean owner = viewerId > 0 && viewerId == entry.getUser().getId();
    final StringBuilder sb = new StringBuilder();
    image(sb, entry, attachments.image(), viewerId, owner);
    link(sb, attachments.link(), owner);
    return sb.toString();
  }

  private void image(
      final StringBuilder sb,
      final Entry entry,
      final EntryImage image,
      final int viewerId,
      final boolean owner) {
    if (image == null || !entryImageService.visible(image, entry, viewerId)) return;

    final String alt =
        image.getAltText() == null || image.getAltText().isBlank()
            ? entry.getSubject()
            : image.getAltText();
    sb.append("<figure class=\"entry-image\"><a href=\"/EntryImage/").append(image.getId());
    sb.append("\" data-lightbox=\"entry-").append(entry.getId());
    sb.append("\" data-title=\"").append(Xml.cleanString(image.getAltText())).append("\">");
    sb.append("<img src=\"/EntryImage/").append(image.getId()).append("/display\"");
    sb.append(" width=\"").append(image.getWidth()).append('"');
    sb.append(" height=\"").append(image.getHeight()).append('"');
    sb.append(" alt=\"").append(Xml.cleanString(alt)).append('"');
    sb.append(" loading=\"lazy\" decoding=\"async\"></a>");
    if (owner && image.getModerationStatus() != ModerationStatus.APPROVED) {
      sb.append("<figcaption>Awaiting review: only you can see this picture.</figcaption>");
    }
    sb.append("</figure>\n");
  }

  private static void link(final StringBuilder sb, final EntryLink link, final boolean owner) {
    if (link == null) return;
    if (link.getPreviewStatus() == LinkPreviewStatus.BLOCKED) {
      if (owner) {
        sb.append("<p class=\"entry-attachment-note\">The link on this entry was found on a list of")
            .append(" unsafe sites, so it isn't shown.</p>\n");
      }
      return;
    }

    final URI uri = safeUri(link.getUrl());
    if (uri == null) return;
    final String host = asciiHost(uri);
    final boolean ready = link.getPreviewStatus() == LinkPreviewStatus.READY;

    sb.append("<aside class=\"entry-link-card");
    if (!ready) sb.append(" entry-link-card--plain");
    sb.append("\"><a class=\"entry-link\" href=\"").append(Xml.cleanString(uri.toString()));
    sb.append("\" rel=\"nofollow ugc noopener noreferrer\" target=\"_blank\"");
    sb.append(" referrerpolicy=\"no-referrer\">");

    // the author always sees the preview picture; others once it is approved
    final boolean showImage =
        ready
            && link.getImageFilename() != null
            && (owner || EntryAttachmentService.linkImageApproved(link));
    if (showImage) {
      sb.append("<img class=\"entry-link-image\" src=\"/EntryLinkImage/").append(link.getId());
      sb.append('"');
      if (link.getImageWidth() != null && link.getImageHeight() != null) {
        sb.append(" width=\"").append(link.getImageWidth()).append('"');
        sb.append(" height=\"").append(link.getImageHeight()).append('"');
      }
      sb.append(" alt=\"\" loading=\"lazy\" decoding=\"async\">");
    }

    sb.append("<span class=\"entry-link-text\">");
    final String site =
        ready && link.getSiteName() != null && !link.getSiteName().isBlank()
            ? link.getSiteName() + " · " + host
            : host;
    sb.append("<span class=\"entry-link-site\">").append(Xml.cleanString(site)).append("</span>");
    final String title = ready ? EntryAttachmentService.linkTitle(link) : link.getUrl();
    sb.append("<span class=\"entry-link-title\">").append(Xml.cleanString(title)).append("</span>");
    if (ready && link.getDescription() != null && !link.getDescription().isBlank()) {
      sb.append("<span class=\"entry-link-desc\">");
      sb.append(Xml.cleanString(link.getDescription()));
      sb.append("</span>");
    }
    sb.append("</span></a></aside>\n");
  }

  /** The stored link again, but only if it is still a plain http or https address. */
  private static URI safeUri(final String url) {
    if (url == null) return null;
    try {
      final URI uri = new URI(url);
      final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!scheme.equals("http") && !scheme.equals("https")) return null;
      if (uri.getHost() == null) return null;
      return uri;
    } catch (final Exception e) {
      return null;
    }
  }

  /** The host in punycode, so a look-alike name in another script can't pass for a known site. */
  private static String asciiHost(final URI uri) {
    try {
      return IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
    } catch (final IllegalArgumentException e) {
      return uri.getHost();
    }
  }
}
