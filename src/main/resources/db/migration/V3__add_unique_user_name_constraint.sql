-- H1-B: closes the missing-unique-constraint drift between the live production schema and
-- User.userName (@Column(nullable = false, unique = true)) - the live `users` table previously
-- had a unique constraint only on `email` (unique_user_email), none on `user_name`.
--
-- Safe to run directly, no cleanup step required: a read-only production preflight (aggregate
-- COUNT/GROUP BY queries only, no row content read) confirmed, before this migration was
-- written: 0 duplicate user_name groups and 0 NULL user_name values across all 19 existing rows.
--
-- users.user_name is already `varchar(255) NOT NULL` on the live schema (matches the entity),
-- so only the unique index is added here - no MODIFY COLUMN needed.

ALTER TABLE users ADD CONSTRAINT uk_users_user_name UNIQUE (user_name);
