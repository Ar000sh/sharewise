-- Settlement ledger, one per group (aggregate GroupLedger), fed by ExpenseRecorded.
-- No foreign keys to other modules: groups and members belong to them.
CREATE TABLE settlement_ledger
(
  group_id UUID PRIMARY KEY
);

-- Append-only bookings. Ex4 uses EXPENSE_PAID (the payer is credited) and EXPENSE_OWED (a share holder is
-- debited); Ex5 adds reversals, payments and waivers. A member's balance is the sum of their deltas.
CREATE TABLE settlement_ledger_entry
(
  id          UUID PRIMARY KEY,
  group_id    UUID                     NOT NULL REFERENCES settlement_ledger (group_id),
  member_id   UUID                     NOT NULL,
  delta_cents BIGINT                   NOT NULL,
  entry_type  VARCHAR(30)              NOT NULL,
  reference_id UUID                    NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  -- safety net behind the ledger's own "book an expense once" guard
  CONSTRAINT settlement_ledger_entry_once UNIQUE (reference_id, member_id, entry_type)
);

CREATE INDEX settlement_ledger_entry_by_group ON settlement_ledger_entry (group_id);
