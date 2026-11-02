-- Replies from other fediverse servers to an entry, shown with its comments. They have no local
-- user, so they live here rather than in comments, whose uid is a local user. object_id is the
-- reply's ActivityPub id, unique so a repeated delivery stores one row; actor_id is its author.
-- author_name, author_handle (@user@host) and author_url come from the author's actor document.
-- content is HTML already cleaned down to text, paragraphs, line breaks and links. The rows go
-- with their entry. URLs are stored as ASCII URIs.
CREATE TABLE IF NOT EXISTS `activitypub_reply` (
  `id`            INT UNSIGNED                                        NOT NULL AUTO_INCREMENT,
  `entry_id`      INT UNSIGNED                                        NOT NULL,
  `object_id`     VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_id`      VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `author_name`   VARCHAR(255)                                        NOT NULL,
  `author_handle` VARCHAR(255)                                                 DEFAULT NULL,
  `author_url`    VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content`       MEDIUMTEXT                                          NOT NULL,
  `published`     DATETIME                                            NOT NULL,
  `created`       DATETIME                                            NOT NULL,
  `updated`       DATETIME                                                     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `activitypub_reply_object` (`object_id`),
  KEY `activitypub_reply_entry` (`entry_id`),
  KEY `activitypub_reply_actor` (`actor_id`),
  CONSTRAINT `fk_activitypub_reply_entry` FOREIGN KEY (`entry_id`) REFERENCES `entry` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
