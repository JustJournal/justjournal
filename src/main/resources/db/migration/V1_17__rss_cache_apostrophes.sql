-- RssCacheRefresh used to store every apostrophe in feed content as \' (an old "escape for
-- MySQL" step that JPA never needed), and the backslashes showed up on the page.
-- CHAR(92) is the backslash and CHAR(39) the apostrophe, to avoid quoting ambiguity.
-- lastupdated is set to itself so its ON UPDATE CURRENT_TIMESTAMP doesn't fire and delay the
-- next refresh.
UPDATE rss_cache
SET content     = REPLACE(content, CONCAT(CHAR(92), CHAR(39)), CHAR(39)),
    lastupdated = lastupdated
WHERE content LIKE CONCAT('%', CHAR(92), CHAR(92), CHAR(39), '%');
