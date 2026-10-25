-- Settlement read model: running balance per (group, member), fed by ExpenseRecorded.
-- No foreign keys: groups and members belong to other modules.
CREATE TABLE settlement_balance
(
  group_id      UUID   NOT NULL,
  member_id     UUID   NOT NULL,
  balance_cents BIGINT NOT NULL,
  PRIMARY KEY (group_id, member_id)
);

-- Expenses already applied, so a redelivered event is not counted twice.
CREATE TABLE settlement_processed_expense
(
  expense_id UUID PRIMARY KEY
);
