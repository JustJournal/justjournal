-- Why the content filter gave its verdict: the category the model answered (safe, suggestive,
-- nudity, sexual), 'animated' when only the first frame of a moving picture was checked, or 'error'
-- when no server could be asked. Pictures held with 'error' were never judged and are tried again
-- by a scheduled job; the others wait for a person. Null for pictures that predate the filter.
alter table user_images
    add column `moderation_category` VARCHAR(16) NULL;

alter table user_pic
    add column `moderation_category` VARCHAR(16) NULL;
