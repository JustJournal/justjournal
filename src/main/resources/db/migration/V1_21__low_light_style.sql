-- Low Light: dark theme for readers who find bright pages hard to look at.
-- The stylesheet is static/styles/25.css. IGNORE so a database that already has the row
-- doesn't fail the migration.
INSERT IGNORE INTO style (id, title, `desc`, modified)
VALUES (25, 'Low Light', 'Soft dark theme, easier on light sensitive eyes', CURRENT_TIMESTAMP);
