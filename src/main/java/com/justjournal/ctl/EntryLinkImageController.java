package com.justjournal.ctl;

import static com.justjournal.core.Constants.LOGIN_ATTRID;
import static com.justjournal.core.Constants.PARAM_ID;

import com.justjournal.exception.ServiceException;
import com.justjournal.services.EntryAttachmentService;
import com.justjournal.services.EntryImageService;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the preview picture of a link attached to a post. The picture was fetched from the linked
 * site and checked by the content filter; it is shown to whoever may read the post once approved,
 * and always to the post's author.
 */
@Controller
@RequestMapping("/EntryLinkImage")
public class EntryLinkImageController {

  /** The post can become private; a shared cache may show the picture until this runs out. */
  static final Duration CACHE_TIME = Duration.ofHours(1);

  private final EntryAttachmentService entryAttachmentService;

  public EntryLinkImageController(final EntryAttachmentService entryAttachmentService) {
    this.entryAttachmentService = entryAttachmentService;
  }

  @GetMapping(value = "/{id}")
  public ResponseEntity<Resource> get(@PathVariable(PARAM_ID) final int id, final HttpSession session)
      throws ServiceException {
    final EntryImageService.Picture picture = entryAttachmentService.linkImage(id, viewer(session));
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(
            picture.publicCacheable()
                ? CacheControl.maxAge(CACHE_TIME).cachePublic()
                : CacheControl.noStore())
        .body(new InputStreamResource(picture.stream()));
  }

  /** The logged in user, or 0 for a reader who isn't logged in. */
  private static int viewer(final HttpSession session) {
    final Integer userId = (Integer) session.getAttribute(LOGIN_ATTRID);
    return userId == null ? 0 : userId;
  }
}
