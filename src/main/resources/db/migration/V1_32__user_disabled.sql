-- An administrator can disable an account: it can't log in, its open sessions are logged out and
-- its journal, feeds, pictures and fediverse actor answer 404 until it is enabled again. Nothing
-- is deleted, so enabling the account restores everything.
ALTER TABLE `user`
  ADD COLUMN `disabled` BOOLEAN NOT NULL DEFAULT FALSE;
