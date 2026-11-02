-- Accounts on other fediverse servers that a writer follows. follow_id is the id of the Follow we
-- sent, which the remote server's Accept or Reject names. state is PENDING until the remote server
-- answers; a Reject keeps the row as REJECTED so the writer can see it was declined. URLs are
-- stored as ASCII URIs.
CREATE TABLE IF NOT EXISTS `activitypub_following` (
  `id`           INT UNSIGNED                                        NOT NULL AUTO_INCREMENT,
  `user_id`      INT UNSIGNED                                        NOT NULL,
  `actor_id`     VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `inbox`        VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `shared_inbox` VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin     DEFAULT NULL,
  `follow_id`    VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state`        ENUM ('PENDING', 'ACCEPTED', 'REJECTED')            NOT NULL DEFAULT 'PENDING',
  `display_name` VARCHAR(255)                                            DEFAULT NULL,
  `handle`       VARCHAR(512)                                            DEFAULT NULL,
  `profile_url`  VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin     DEFAULT NULL,
  `created`      DATETIME                                            NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `activitypub_following_actor` (`user_id`, `actor_id`),
  KEY `activitypub_following_remote` (`actor_id`),
  KEY `activitypub_following_follow` (`follow_id`),
  CONSTRAINT `fk_activitypub_following_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Public posts by followed accounts, shown on their followers' friends pages. object_id is the
-- post's ActivityPub id; content is HTML already cleaned down to text, paragraphs, line breaks and
-- links; attachments is a JSON array of links ({"url", "name"}), never shown as images. Rows are
-- removed once older than activitypub.remote-posts.keep-days, or once nobody here follows the
-- author.
CREATE TABLE IF NOT EXISTS `activitypub_remote_post` (
  `id`          INT UNSIGNED                                        NOT NULL AUTO_INCREMENT,
  `object_id`   VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_id`    VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `url`         VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin     DEFAULT NULL,
  `name`        VARCHAR(512)                                            DEFAULT NULL,
  `summary`     TEXT                                                    DEFAULT NULL,
  `content`     MEDIUMTEXT                                          NOT NULL,
  `attachments` TEXT                                                    DEFAULT NULL,
  `published`   DATETIME                                            NOT NULL,
  `created`     DATETIME                                            NOT NULL,
  `updated`     DATETIME                                                DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `activitypub_remote_post_object` (`object_id`),
  KEY `activitypub_remote_post_actor` (`actor_id`, `published`),
  KEY `activitypub_remote_post_published` (`published`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
