-- H1-B: closes the nullability drift between the live production schema and the JPA entity
-- metadata (Post.user/Comment.user/Like.user/Notification.read/Notification.createdAt/
-- Friends.createdAt are all @Column(nullable = false), but the live columns allowed NULL).
--
-- Safe to run directly, no backfill step required: a read-only production preflight (aggregate
-- COUNT/SUM queries only, no row content read) confirmed zero existing NULLs in every column
-- below before this migration was written:
--   posts.user_id            0 of 28 rows
--   comments.user_id         0 of 37 rows
--   post_likes.user_id       0 of 42 rows
--   notifications.is_read    0 of 102 rows
--   notifications.created_at 0 of 102 rows
--   friends.created_at       0 of 19 rows
--
-- Each MODIFY COLUMN restates the column's exact current type/DEFAULT (captured via
-- SHOW CREATE TABLE against the live schema) alongside the new NOT NULL, since MySQL's MODIFY
-- COLUMN replaces the entire column definition - omitting an existing DEFAULT would silently
-- drop it.

ALTER TABLE posts MODIFY COLUMN user_id INT NOT NULL;

ALTER TABLE comments MODIFY COLUMN user_id INT NOT NULL;

ALTER TABLE post_likes MODIFY COLUMN user_id INT NOT NULL;

ALTER TABLE notifications MODIFY COLUMN is_read TINYINT(1) NOT NULL DEFAULT '0';

ALTER TABLE notifications MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE friends MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
