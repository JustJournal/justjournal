-- Optional second factor for web logins. A row in user_two_factor means it is turned on.
CREATE TABLE IF NOT EXISTS `user_two_factor` (
  `id`          INT UNSIGNED          NOT NULL AUTO_INCREMENT,
  `user_id`     INT UNSIGNED          NOT NULL,
  `method`      ENUM ('EMAIL', 'TOTP') NOT NULL,
  `totp_secret` VARCHAR(64)                    DEFAULT NULL,
  `created`     DATETIME              NOT NULL,
  `last_used`   DATETIME                       DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_two_factor_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Single use backup codes, stored as SHA-256 hex digests.
CREATE TABLE IF NOT EXISTS `user_two_factor_recovery` (
  `id`        INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`   INT UNSIGNED NOT NULL,
  `code_hash` CHAR(64)     NOT NULL,
  `used_at`   DATETIME              DEFAULT NULL,
  `created`   DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `user_two_factor_recovery_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Browsers the user chose to remember. Only a SHA-256 hex digest of the cookie value is kept.
CREATE TABLE IF NOT EXISTS `user_two_factor_trusted` (
  `id`         INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    INT UNSIGNED NOT NULL,
  `token_hash` CHAR(64)     NOT NULL,
  `expires`    DATETIME     NOT NULL,
  `created`    DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_two_factor_trusted_token` (`token_hash`),
  KEY `user_two_factor_trusted_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
