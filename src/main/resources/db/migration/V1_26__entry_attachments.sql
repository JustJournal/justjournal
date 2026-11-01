-- A picture and a link preview that a writer can attach to a blog post. Each is its own upload,
-- owned by the writer and shown only to those who may see the post. entry_id stays null until a
-- save attaches the row to its post; unattached rows are seen only by their owner and are cleaned
-- up by a scheduled job. Deleting a post sets entry_id back to null for the same job to find.
-- The old entry.attach_image and attach_file columns are not used.
CREATE TABLE IF NOT EXISTS `entry_image` (
  `id`                  INT UNSIGNED                           NOT NULL AUTO_INCREMENT,
  `owner`               INT UNSIGNED                           NOT NULL,
  `entry_id`            INT UNSIGNED                                    DEFAULT NULL,
  `mimetype`            VARCHAR(45)                            NOT NULL,
  -- the picture as uploaded, entry/{owner}/{uuid}.{ext} in the image bucket
  `filename`            VARCHAR(150)                           NOT NULL,
  -- a JPEG of at most 1024 pixels a side, turned the right way up, shown on the post
  `display`             VARCHAR(150)                           NOT NULL,
  `size`                BIGINT UNSIGNED                        NOT NULL,
  -- of the display image
  `width`               INT UNSIGNED                           NOT NULL,
  `height`              INT UNSIGNED                           NOT NULL,
  `alt_text`            VARCHAR(255)                                    DEFAULT NULL,
  `moderation_status`   ENUM ('APPROVED', 'REVIEW', 'REJECTED') NOT NULL DEFAULT 'APPROVED',
  `moderation_category` VARCHAR(16)                                     DEFAULT NULL,
  `created`             DATETIME                               NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `entry_image_entry` (`entry_id`),
  KEY `entry_image_owner` (`owner`),
  KEY `entry_image_moderation` (`moderation_status`, `moderation_category`),
  CONSTRAINT `fk_entry_image_entry` FOREIGN KEY (`entry_id`) REFERENCES `entry` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- A link and the Open Graph preview a background job fetches for it. url is the checked,
-- normalized link and the only address ever rendered as a link; final_url is where redirects
-- ended. The preview image is stored as link/{owner}/{uuid}.jpg in the image bucket.
CREATE TABLE IF NOT EXISTS `entry_link` (
  `id`                        INT UNSIGNED                                   NOT NULL AUTO_INCREMENT,
  `owner`                     INT UNSIGNED                                   NOT NULL,
  `entry_id`                  INT UNSIGNED                                            DEFAULT NULL,
  `url`                       VARCHAR(1024)                                  NOT NULL,
  `final_url`                 VARCHAR(1024)                                           DEFAULT NULL,
  `title`                     VARCHAR(300)                                            DEFAULT NULL,
  `description`               VARCHAR(1000)                                           DEFAULT NULL,
  `site_name`                 VARCHAR(200)                                            DEFAULT NULL,
  `image_filename`            VARCHAR(150)                                            DEFAULT NULL,
  `image_width`               INT UNSIGNED                                            DEFAULT NULL,
  `image_height`              INT UNSIGNED                                            DEFAULT NULL,
  `image_moderation_status`   ENUM ('APPROVED', 'REVIEW', 'REJECTED')                 DEFAULT NULL,
  `image_moderation_category` VARCHAR(16)                                             DEFAULT NULL,
  `preview_status`            ENUM ('PENDING', 'READY', 'FAILED', 'BLOCKED') NOT NULL DEFAULT 'PENDING',
  `failure_reason`            VARCHAR(255)                                            DEFAULT NULL,
  `attempts`                  INT UNSIGNED                                   NOT NULL DEFAULT 0,
  -- when the job may next fetch a PENDING preview; also the lease a running fetch holds
  `next_attempt_at`           DATETIME                                                DEFAULT NULL,
  `fetched_at`                DATETIME                                                DEFAULT NULL,
  `created`                   DATETIME                                       NOT NULL,
  PRIMARY KEY (`id`),
  KEY `entry_link_entry` (`entry_id`),
  KEY `entry_link_owner` (`owner`),
  KEY `entry_link_due` (`preview_status`, `next_attempt_at`),
  KEY `entry_link_moderation` (`image_moderation_status`, `image_moderation_category`),
  CONSTRAINT `fk_entry_link_entry` FOREIGN KEY (`entry_id`) REFERENCES `entry` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
