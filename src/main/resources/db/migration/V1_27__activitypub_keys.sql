-- RSA key pairs that sign ActivityPub requests: one per user, made the first time it is needed,
-- and one for the instance actor (/actor), which signs requests made on behalf of the site.
-- A user's row has user_id set and key_name null; the instance row has user_id null and
-- key_name 'instance'. Both columns are unique (MySQL lets a unique column hold many nulls), so
-- there is at most one row per user and one per name. Keys are PEM: the public key as X.509
-- SubjectPublicKeyInfo, the private key as unencrypted PKCS#8, like the TOTP secrets in
-- user_two_factor.
CREATE TABLE IF NOT EXISTS `activitypub_key` (
  `id`              INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         INT UNSIGNED          DEFAULT NULL,
  `key_name`        VARCHAR(32)           DEFAULT NULL,
  `public_key_pem`  TEXT         NOT NULL,
  `private_key_pem` TEXT         NOT NULL,
  `created`         DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `activitypub_key_user` (`user_id`),
  UNIQUE KEY `activitypub_key_name` (`key_name`),
  CONSTRAINT `fk_activitypub_key_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
