-- The inbox of the notification context (design 2026-11-03, N1): one row per person who should hear about something
-- that happened. Ids only, no names or texts; those are looked up when the inbox is read. No foreign keys: the ids
-- belong to other modules.
CREATE TABLE notifications (
    id           UUID        PRIMARY KEY,
    recipient_id UUID        NOT NULL,
    type         VARCHAR(40) NOT NULL,
    group_id     UUID        NOT NULL,
    actor_id     UUID        NOT NULL,   -- who did it (joined, declined)
    reference    UUID        NOT NULL,   -- what it points at: the invitation, or the person who joined
    occurred_at  TIMESTAMPTZ NOT NULL,
    read_at      TIMESTAMPTZ,            -- null = unread

    -- A redelivered event must not create a second entry (idempotent consumer). occurred_at is part of the key
    -- because a MEMBER_JOINED has no invitation to point at: without the time, a person who leaves and joins again
    -- would be dropped as a duplicate. A redelivery carries the same occurred_at, so it still collides.
    CONSTRAINT uq_notifications_event UNIQUE (recipient_id, type, reference, occurred_at)
);

-- The bell: a person's entries, unread first-class, newest first.
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, read_at, occurred_at DESC);
