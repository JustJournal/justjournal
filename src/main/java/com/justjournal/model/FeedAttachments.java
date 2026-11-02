package com.justjournal.model;

/**
 * The picture and link attached to an entry, as feeds show them. Only what anyone may see is put
 * in here: an approved picture and a finished link preview on a public entry. URLs are absolute.
 *
 * @param imageUrl the picture as uploaded, or null when there is none
 * @param imageThumbnailUrl the picture scaled down for display
 * @param imageType MIME type of the uploaded picture
 * @param imageLength size of the uploaded picture in bytes
 * @param imageWidth width of the scaled down picture
 * @param imageHeight height of the scaled down picture
 * @param imageAlt description of the picture, may be empty
 * @param linkUrl the attached link, or null when there is none
 * @param linkTitle title of the linked page, or the link itself
 * @param linkDescription description of the linked page, may be empty
 * @param linkImageUrl preview picture of the linked page, or null
 * @param linkImageWidth width of the preview picture
 * @param linkImageHeight height of the preview picture
 */
public record FeedAttachments(
    String imageUrl,
    String imageThumbnailUrl,
    String imageType,
    long imageLength,
    int imageWidth,
    int imageHeight,
    String imageAlt,
    String linkUrl,
    String linkTitle,
    String linkDescription,
    String linkImageUrl,
    int linkImageWidth,
    int linkImageHeight) {

  public static final FeedAttachments NONE =
      new FeedAttachments(null, null, null, 0, 0, 0, null, null, null, null, null, 0, 0);

  public boolean hasImage() {
    return imageUrl != null;
  }

  public boolean hasLink() {
    return linkUrl != null;
  }

  public boolean hasLinkImage() {
    return linkImageUrl != null;
  }

  /** The best picture to show for the entry: its own, else the link's preview. */
  public String previewImageUrl() {
    if (hasImage()) return imageThumbnailUrl;
    return linkImageUrl;
  }
}
