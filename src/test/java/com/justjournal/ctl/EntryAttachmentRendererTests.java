package com.justjournal.ctl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.justjournal.model.Entry;
import com.justjournal.model.EntryImage;
import com.justjournal.model.EntryLink;
import com.justjournal.model.LinkPreviewStatus;
import com.justjournal.model.ModerationStatus;
import com.justjournal.model.User;
import com.justjournal.services.EntryAccessService;
import com.justjournal.services.EntryAttachmentService.EntryAttachments;
import com.justjournal.services.EntryImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntryAttachmentRendererTests {

  private static final int OWNER = 5;
  private static final int READER = 9;

  private EntryAccessService access;
  private EntryImageService images;
  private EntryAttachmentRenderer renderer;
  private Entry entry;

  @BeforeEach
  void setUp() {
    access = mock(EntryAccessService.class);
    images = mock(EntryImageService.class);
    renderer = new EntryAttachmentRenderer(access, images);
    when(access.canView(any(), anyInt())).thenReturn(true);
    when(images.visible(any(), any(), anyInt())).thenReturn(true);

    final User owner = new User();
    owner.setId(OWNER);
    owner.setUsername("bob");
    entry = new Entry();
    entry.setId(30);
    entry.setUser(owner);
    entry.setSubject("A day out");
  }

  private static EntryImage image(final ModerationStatus status) {
    final EntryImage image = new EntryImage();
    image.setId(3);
    image.setOwnerId(OWNER);
    image.setEntryId(30);
    image.setWidth(800);
    image.setHeight(600);
    image.setModerationStatus(status);
    return image;
  }

  private static EntryLink link(final LinkPreviewStatus status) {
    final EntryLink link = new EntryLink();
    link.setId(4);
    link.setOwnerId(OWNER);
    link.setEntryId(30);
    link.setUrl("https://example.com/a?b=1&c=2");
    link.setPreviewStatus(status);
    return link;
  }

  @Test
  void pictureShowsItsDisplayImageAndFallsBackToTheSubjectForAlt() {
    final String html =
        renderer.html(entry, new EntryAttachments(image(ModerationStatus.APPROVED), null), READER);

    assertTrue(html.contains("<a href=\"/EntryImage/3\""), html);
    assertTrue(html.contains("<img src=\"/EntryImage/3/display\" width=\"800\" height=\"600\""), html);
    assertTrue(html.contains("alt=\"A day out\""), html);
    assertFalse(html.contains("Awaiting review"), html);
  }

  @Test
  void altTextIsEscaped() {
    final EntryImage image = image(ModerationStatus.APPROVED);
    image.setAltText("A \"cat\" <b>& dog</b>");

    final String html = renderer.html(entry, new EntryAttachments(image, null), READER);

    assertTrue(html.contains("alt=\"A &quot;cat&quot; &lt;b&gt;&amp; dog&lt;/b&gt;\""), html);
    assertFalse(html.contains("<b>"), html);
  }

  @Test
  void ownerIsToldAPictureAwaitsReview() {
    final String html =
        renderer.html(entry, new EntryAttachments(image(ModerationStatus.REVIEW), null), OWNER);

    assertTrue(html.contains("Awaiting review"), html);
  }

  @Test
  void hiddenPictureIsLeftOut() {
    when(images.visible(any(), any(), anyInt())).thenReturn(false);

    assertEquals(
        "", renderer.html(entry, new EntryAttachments(image(ModerationStatus.REVIEW), null), READER));
  }

  @Test
  void nothingIsShownWhenTheReaderMayNotSeeTheEntry() {
    when(access.canView(any(), anyInt())).thenReturn(false);

    assertEquals(
        "",
        renderer.html(
            entry,
            new EntryAttachments(image(ModerationStatus.APPROVED), link(LinkPreviewStatus.READY)),
            READER));
  }

  @Test
  void pendingLinkIsAPlainCardWithTheEscapedAddress() {
    final String html =
        renderer.html(entry, new EntryAttachments(null, link(LinkPreviewStatus.PENDING)), READER);

    assertTrue(html.contains("entry-link-card entry-link-card--plain"), html);
    assertTrue(html.contains("href=\"https://example.com/a?b=1&amp;c=2\""), html);
    assertTrue(html.contains("rel=\"nofollow ugc noopener noreferrer\" target=\"_blank\""), html);
    assertTrue(html.contains("<span class=\"entry-link-site\">example.com</span>"), html);
    assertFalse(html.contains("EntryLinkImage"), html);
  }

  @Test
  void readyLinkShowsItsPreviewEscaped() {
    final EntryLink link = link(LinkPreviewStatus.READY);
    link.setTitle("\"><script>alert(1)</script>");
    link.setDescription("Fish & chips");
    link.setSiteName("Example");
    link.setImageFilename("link/5/x.jpg");
    link.setImageWidth(600);
    link.setImageHeight(315);
    link.setImageModerationStatus(ModerationStatus.APPROVED);

    final String html = renderer.html(entry, new EntryAttachments(null, link), READER);

    assertFalse(html.contains("<script>"), html);
    assertTrue(html.contains("&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;"), html);
    assertTrue(html.contains("Fish &amp; chips"), html);
    assertTrue(html.contains("Example · example.com"), html);
    assertTrue(html.contains("<img class=\"entry-link-image\" src=\"/EntryLinkImage/4\" width=\"600\" height=\"315\""), html);
  }

  @Test
  void previewPictureAwaitingReviewIsOnlyShownToTheOwner() {
    final EntryLink link = link(LinkPreviewStatus.READY);
    link.setImageFilename("link/5/x.jpg");
    link.setImageModerationStatus(ModerationStatus.REVIEW);

    assertFalse(renderer.html(entry, new EntryAttachments(null, link), READER).contains("EntryLinkImage"));
    assertTrue(renderer.html(entry, new EntryAttachments(null, link), OWNER).contains("EntryLinkImage"));
  }

  @Test
  void blockedLinkIsHiddenAndTheOwnerIsTold() {
    final EntryAttachments blocked = new EntryAttachments(null, link(LinkPreviewStatus.BLOCKED));

    assertEquals("", renderer.html(entry, blocked, READER));
    final String owner = renderer.html(entry, blocked, OWNER);
    assertTrue(owner.contains("isn't shown"), owner);
    assertFalse(owner.contains("example.com"), owner);
  }

  @Test
  void linkThatIsNoLongerHttpIsNotRendered() {
    final EntryLink link = link(LinkPreviewStatus.READY);
    link.setUrl("javascript:alert(1)");

    assertEquals("", renderer.html(entry, new EntryAttachments(null, link), READER));
  }

  @Test
  void lookAlikeHostIsShownInPunycode() {
    final EntryLink link = link(LinkPreviewStatus.PENDING);
    link.setUrl("https://xn--pple-43d.com/");

    final String html = renderer.html(entry, new EntryAttachments(null, link), READER);

    assertTrue(html.contains("<span class=\"entry-link-site\">xn--pple-43d.com</span>"), html);
  }
}
