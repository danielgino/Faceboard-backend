-- Demo Mode: the single membership signal for the public, read-only Demo dataset. is_demo=1
-- marks exactly the seeded demo_user and its seed friend/content rows (created idempotently by
-- DemoDataSeeder, only when app.demo.enabled=true); every existing/real user row defaults to 0
-- and is completely unaffected. TINYINT(1) matches the boolean convention already used for
-- notifications.is_read (see V2__add_missing_not_null_constraints.sql).

ALTER TABLE users ADD COLUMN is_demo TINYINT(1) NOT NULL DEFAULT 0;
