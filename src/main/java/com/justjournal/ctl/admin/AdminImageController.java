package com.justjournal.ctl.admin;

import com.justjournal.exception.BadRequestException;
import com.justjournal.exception.NotFoundException;
import com.justjournal.exception.ServiceException;
import com.justjournal.model.ModerationStatus;
import com.justjournal.services.AdminModerationService;
import com.justjournal.services.AdminModerationService.Kind;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pictures the content filter held for review, for the administrator to approve or reject.
 *
 * @author Lucas Holt
 */
@Slf4j
@Controller
@RequestMapping("/admin/images")
public class AdminImageController {

  static final String VIEW = "admin/layout";

  /**
   * Image types sent as themselves. Anything else, such as a legacy row with an odd mime type, is
   * sent as a download so it can't run as a page on the admin site.
   */
  static final Set<String> IMAGE_TYPES =
      Set.of(
          MediaType.IMAGE_JPEG_VALUE,
          MediaType.IMAGE_PNG_VALUE,
          MediaType.IMAGE_GIF_VALUE,
          "image/webp",
          "image/bmp",
          "image/tiff");

  private final AdminModerationService adminModerationService;

  public AdminImageController(final AdminModerationService adminModerationService) {
    this.adminModerationService = adminModerationService;
  }

  @GetMapping("")
  public String list(final Model model) {
    model.addAttribute("title", "Pictures to review");
    model.addAttribute("adminSection", "images");
    model.addAttribute("contentTemplate", "admin/images");
    model.addAttribute("pictures", adminModerationService.held());
    model.addAttribute("maxPerKind", AdminModerationService.MAX_PER_KIND);
    return VIEW;
  }

  /** A held picture's file. 404 for an unknown kind, or a picture that isn't held. */
  @GetMapping("/{kind}/{id}")
  public ResponseEntity<Resource> image(
      @PathVariable("kind") final String kind, @PathVariable("id") final int id)
      throws ServiceException {
    final AdminModerationService.HeldFile file = adminModerationService.file(kind(kind), id);
    return ResponseEntity.ok()
        .contentType(type(file.mimeType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .body(new InputStreamResource(file.stream()));
  }

  /** Approve or reject a held picture, then back to the list. */
  @PostMapping("/{kind}/{id}")
  public String decide(
      @PathVariable("kind") final String kind,
      @PathVariable("id") final int id,
      @RequestParam("decision") final String decision,
      final HttpSession session,
      final RedirectAttributes redirect) {
    final Kind picture = kind(kind);
    final ModerationStatus status = decision(decision);
    final String admin = String.valueOf(session.getAttribute(AdminSession.ADMIN_ATTR));

    final String name = picture.getLabel() + " " + id;
    if (adminModerationService.decide(picture, id, status, admin)) {
      redirect.addFlashAttribute(
          "message",
          name + (status == ModerationStatus.APPROVED ? " approved." : " rejected."));
    } else {
      redirect.addFlashAttribute(
          "error", name + " is no longer waiting for review; nothing was changed.");
    }
    return "redirect:/admin/images";
  }

  private static Kind kind(final String kind) {
    return Kind.fromPath(kind).orElseThrow(() -> new NotFoundException("No such picture kind"));
  }

  private static ModerationStatus decision(final String decision) {
    if (ModerationStatus.APPROVED.name().equals(decision)) return ModerationStatus.APPROVED;
    if (ModerationStatus.REJECTED.name().equals(decision)) return ModerationStatus.REJECTED;
    throw new BadRequestException("A decision is APPROVED or REJECTED");
  }

  private static MediaType type(final String mimeType) {
    if (mimeType != null) {
      final String trimmed = mimeType.trim().toLowerCase(Locale.ROOT);
      if (IMAGE_TYPES.contains(trimmed)) return MediaType.parseMediaType(trimmed);
      log.warn("Held picture has an unexpected mime type {}", mimeType);
    }
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
