-- rss_cache was created latin1, so saving a feed with any character outside it (the BBC world
-- feed with Croatian names, for one) failed with "Incorrect string value". The stored content
-- only ever held latin1 characters, so converting is lossless. MySQL widens content to LONGTEXT
-- and uri to TEXT to keep their character lengths; nothing depends on the narrower types.
ALTER TABLE rss_cache
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
