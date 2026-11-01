-- Pictures and avatars held before V1.24 have no category, so the scheduled job that judges again
-- those the filter could not be asked about never finds them. Why they were held wasn't recorded,
-- so treat them as never judged: the model is asked again, and whatever it still holds waits for a
-- person as before.
update user_images
set moderation_category = 'error'
where moderation_status = 'REVIEW'
  and moderation_category is null;

update user_pic
set moderation_category = 'error'
where moderation_status = 'REVIEW'
  and moderation_category is null;
