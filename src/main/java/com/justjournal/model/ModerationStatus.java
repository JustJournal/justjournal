package com.justjournal.model;

/** The content filter's verdict on an uploaded picture or avatar. */
public enum ModerationStatus {
  /** Fine to show to everyone. Pictures uploaded before the filter existed count as approved. */
  APPROVED,
  /** Kept, but shown only to its owner until someone looks at it and approves it. */
  REVIEW,
  /** Not acceptable. An upload with this verdict is refused and not stored. */
  REJECTED
}
