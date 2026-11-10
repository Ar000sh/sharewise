-- The zero-balance flag follows asynchronous ledger events that may arrive out of order (D12, R11).
-- When the event that last set the flag occurred; an older event must not override a newer one. Null until the first event.
ALTER TABLE memberships ADD COLUMN balance_status_at TIMESTAMPTZ;
