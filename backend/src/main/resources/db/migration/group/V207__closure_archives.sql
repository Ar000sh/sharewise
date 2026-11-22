-- Admin closure creates immutable snapshots; reopening preserves them and increments the lifecycle revision.
ALTER TABLE groups ADD COLUMN lifecycle_revision BIGINT NOT NULL DEFAULT 0;
-- Earlier statuses had no producing operation. Refuse unexpected legacy rows rather than silently reopening them.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM groups WHERE status NOT IN ('OPEN','CLOSED')) THEN
  RAISE EXCEPTION 'Legacy group statuses require explicit review before the lifecycle migration';
 END IF;
END $$;
ALTER TABLE groups ADD CONSTRAINT group_lifecycle_status CHECK (status IN ('OPEN','CLOSED'));
CREATE TABLE group_archives (
 id UUID PRIMARY KEY, group_id UUID NOT NULL REFERENCES groups(id), archive_version INTEGER NOT NULL,
 closed_by UUID NOT NULL, closed_at TIMESTAMPTZ NOT NULL, snapshot TEXT NOT NULL,
 UNIQUE(group_id, archive_version)
);
CREATE TABLE group_lifecycle_entries (
 id UUID PRIMARY KEY, group_id UUID NOT NULL REFERENCES groups(id), action VARCHAR(255) NOT NULL,
 actor UUID NOT NULL, reason VARCHAR(500) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 revision BIGINT NOT NULL, UNIQUE(group_id, revision)
);
