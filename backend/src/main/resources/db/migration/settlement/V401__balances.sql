-- Settlement read model, one ledger per group (aggregate GroupLedger), fed by ExpenseRecorded.
-- No foreign keys to other modules: groups and members belong to them.
CREATE TABLE settlement_ledger
(
  group_id UUID PRIMARY KEY
);

-- Running balance per member; only members that appear in expenses have a row.
CREATE TABLE settlement_balance
(
  group_id      UUID   NOT NULL REFERENCES settlement_ledger (group_id),
  member_id     UUID   NOT NULL,
  balance_cents BIGINT NOT NULL,
  PRIMARY KEY (group_id, member_id)
);

-- Expenses already applied, so a redelivered event is not counted twice.
CREATE TABLE settlement_processed_expense
(
  group_id   UUID NOT NULL REFERENCES settlement_ledger (group_id),
  expense_id UUID NOT NULL,
  PRIMARY KEY (group_id, expense_id)
);
