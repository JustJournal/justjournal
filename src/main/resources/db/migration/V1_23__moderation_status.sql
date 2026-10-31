-- The content filter's verdict on each picture and avatar. Everything uploaded before the filter
-- counts as approved. REVIEW is shown only to its owner until it is set to APPROVED by hand.
alter table user_images
    add column `moderation_status` ENUM ('APPROVED', 'REVIEW', 'REJECTED') NOT NULL DEFAULT 'APPROVED';

alter table user_pic
    add column `moderation_status` ENUM ('APPROVED', 'REVIEW', 'REJECTED') NOT NULL DEFAULT 'APPROVED';
