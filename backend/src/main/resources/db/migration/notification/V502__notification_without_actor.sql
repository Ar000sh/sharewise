-- A group archived automatically, because its last balance reached 0, has no person who did it
-- (design 2026-11-28-group-lifecycle G5). Its inbox entry GROUP_ARCHIVED therefore has no actor.
ALTER TABLE notifications ALTER COLUMN actor_id DROP NOT NULL;
