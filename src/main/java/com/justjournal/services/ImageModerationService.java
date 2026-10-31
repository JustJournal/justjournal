package com.justjournal.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.justjournal.model.ModerationStatus;
import java.io.IOException;
import java.util.Locale;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Checks uploaded pictures for nudity and sexual content with a vision model on Ollama. The image
 * is scaled down and sent as a JPEG, so the model sees the same thing whatever was uploaded; an
 * animated GIF is judged by its first frame.
 *
 * <p>This is a nudity filter, not a tool for recognising child sexual abuse material. Known
 * material is found by matching hashes against lists kept by organisations such as NCMEC, through
 * services like PhotoDNA or Project Arachnid; a local model must not be pointed at that problem.
 */
@Slf4j
@Service
public class ImageModerationService {

  /** The verdict and what the model said. */
  public record Result(ModerationStatus status, String category, String reason) {
    public boolean rejected() {
      return status == ModerationStatus.REJECTED;
    }
  }

  /** Longest side of the image sent to the model, in pixels. */
  public static final int IMAGE_SIZE = 512;

  static final String CATEGORY_SAFE = "safe";
  static final String CATEGORY_SUGGESTIVE = "suggestive";
  static final String CATEGORY_NUDITY = "nudity";
  static final String CATEGORY_SEXUAL = "sexual";
  /** Not a model answer: the stored image has more frames than the model was shown. */
  static final String CATEGORY_ANIMATED = "animated";

  static final String PROMPT =
      "You are the content filter for a general audience blogging site. Classify the image into"
          + " exactly one category. 'safe': no nudity and nothing sexual; ordinary clothing,"
          + " swimwear worn at a beach or pool, sportswear, and non-sexual art or photography are"
          + " safe. 'suggestive': no exposed genitals, buttocks or female breasts, but the image is"
          + " sexual in nature, such as lingerie or underwear modelling, sexual poses, or clothing"
          + " that barely covers. 'nudity': exposed genitals, buttocks or female breasts, including"
          + " artistic, medical or other non-sexual nudity. 'sexual': a sexual act or pornography."
          + " Give the category and one short sentence saying why.";

  private static final JsonNode FORMAT = answerFormat();

  @Setter
  @Value("${moderation.enabled:false}")
  private boolean enabled;

  @Setter
  @Value("${moderation.ollama.model:gemma4:latest}")
  private String model;

  /** What becomes of a suggestive picture that shows no nudity. */
  @Setter
  @Value("${moderation.on-suggestive:REVIEW}")
  private ModerationStatus onSuggestive;

  /** What becomes of a picture the model couldn't be asked about. */
  @Setter
  @Value("${moderation.on-error:REVIEW}")
  private ModerationStatus onError;

  /** What becomes of an animated GIF the model found safe, having seen only its first frame. */
  @Setter
  @Value("${moderation.on-animated:REVIEW}")
  private ModerationStatus onAnimated;

  private final OllamaClient ollama;
  private final ImageService imageService;

  public ImageModerationService(final OllamaClient ollama, final ImageService imageService) {
    this.ollama = ollama;
    this.imageService = imageService;
  }

  /**
   * Judge an image. Never throws: when the filter is off the image is approved, and when the
   * model can't be reached or gives a nonsense answer the configured fallback applies.
   *
   * @param image the image as it will be stored, in any format ImageIO reads
   */
  public Result moderate(final byte[] image) {
    return moderate(image, image);
  }

  /**
   * Judge an image when the model is shown something other than what is stored, such as the
   * thumbnail of a large photo. The model sees a single frame, so when the stored image is
   * animated a safe verdict becomes the configured animated one instead.
   *
   * @param image what the model is shown, in any format ImageIO reads
   * @param stored the file that is kept if the verdict allows it
   */
  public Result moderate(final byte[] image, final byte[] stored) {
    if (!enabled) return new Result(ModerationStatus.APPROVED, "", "moderation is off");

    final boolean animated;
    final JsonNode answer;
    try {
      animated = imageService.frameCount(stored) > 1;
      answer = ollama.chat(model, PROMPT, imageService.thumbnail(image, IMAGE_SIZE), FORMAT);
    } catch (final IOException | RuntimeException e) {
      log.error("Could not moderate image: {}", e.getMessage());
      return new Result(onError, "error", e.getMessage());
    }

    final String category = answer.path("category").asText("").trim().toLowerCase(Locale.ROOT);
    final String reason = answer.path("reason").asText("");
    final ModerationStatus status =
        switch (category) {
          case CATEGORY_SAFE -> ModerationStatus.APPROVED;
          case CATEGORY_SUGGESTIVE -> onSuggestive;
          case CATEGORY_NUDITY, CATEGORY_SEXUAL -> ModerationStatus.REJECTED;
          default -> {
            log.error("Unexpected moderation answer: {}", answer);
            yield onError;
          }
        };
    if (status == ModerationStatus.APPROVED && animated) {
      log.debug("Image moderation: {} (animated): only the first frame was checked", onAnimated);
      return new Result(onAnimated, CATEGORY_ANIMATED, "only the first frame was checked");
    }
    log.debug("Image moderation: {} ({}): {}", status, category, reason);
    return new Result(status, category, reason);
  }

  private static JsonNode answerFormat() {
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    final ObjectNode properties = schema.putObject("properties");
    final ObjectNode category = properties.putObject("category");
    category.put("type", "string");
    category
        .putArray("enum")
        .add(CATEGORY_SAFE)
        .add(CATEGORY_SUGGESTIVE)
        .add(CATEGORY_NUDITY)
        .add(CATEGORY_SEXUAL);
    properties.putObject("reason").put("type", "string");
    schema.putArray("required").add("category").add("reason");
    return schema;
  }
}
