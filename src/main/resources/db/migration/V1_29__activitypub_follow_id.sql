-- The id of the Follow a follower sent, so an Undo that names the Follow by its id alone can be
-- matched. Null for followers stored before this column existed and for Follows without a usable
-- id. Undo looks it up together with user_id and actor_id, which the existing unique key covers.
ALTER TABLE `activitypub_follower`
  ADD COLUMN `follow_id` VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
