-- Whether the writer lets people on other fediverse servers follow their journal. Nothing is sent
-- unless this is Y and activitypub.federation.enabled is on for the site.
ALTER TABLE `user_pref`
  ADD COLUMN `federate` ENUM ('Y', 'N') NOT NULL DEFAULT 'N';

-- Accounts on other servers that follow a journal. actor_id is the remote actor's id; inbox and
-- shared_inbox are where activities for them are delivered. URLs are stored as ASCII URIs.
CREATE TABLE IF NOT EXISTS `activitypub_follower` (
  `id`           INT UNSIGNED                            NOT NULL AUTO_INCREMENT,
  `user_id`      INT UNSIGNED                            NOT NULL,
  `actor_id`     VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `inbox`        VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `shared_inbox` VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin     DEFAULT NULL,
  `created`      DATETIME                                NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `activitypub_follower_actor` (`user_id`, `actor_id`),
  CONSTRAINT `fk_activitypub_follower_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Activities waiting to be posted to, or already posted to, another server's inbox. user_id is
-- null when the instance actor sends. A background job posts PENDING rows whose next_attempt_at
-- has passed, retrying temporary failures with growing delays; next_attempt_at is also the lease a
-- running delivery holds. finished_at is when the row became DELIVERED or FAILED, so old rows can
-- be removed.
CREATE TABLE IF NOT EXISTS `activitypub_delivery` (
  `id`              INT UNSIGNED                              NOT NULL AUTO_INCREMENT,
  `user_id`         INT UNSIGNED                                       DEFAULT NULL,
  `actor_id`        VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_inbox`    VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `activity`        MEDIUMTEXT                                NOT NULL,
  `attempts`        INT UNSIGNED                              NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME                                  NOT NULL,
  `last_error`      VARCHAR(255)                                       DEFAULT NULL,
  `status`          ENUM ('PENDING', 'DELIVERED', 'FAILED')   NOT NULL DEFAULT 'PENDING',
  `created`         DATETIME                                  NOT NULL,
  `finished_at`     DATETIME                                           DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `activitypub_delivery_due` (`status`, `next_attempt_at`),
  KEY `activitypub_delivery_finished` (`status`, `finished_at`),
  KEY `activitypub_delivery_user` (`user_id`),
  CONSTRAINT `fk_activitypub_delivery_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
