-- The expense context follows the domain diagram: money is a decimal amount with a currency, the payer is "paid_by",
-- the person recording is kept, and an expense has a split strategy and a status.
ALTER TABLE expense RENAME TO expenses;
ALTER TABLE expense_share RENAME TO expense_shares;
ALTER INDEX expense_group_idx RENAME TO expenses_group_idx;

ALTER TABLE expenses RENAME COLUMN payer_id TO paid_by;
ALTER TABLE expenses ADD COLUMN recorded_by UUID;
UPDATE expenses SET recorded_by = paid_by;
ALTER TABLE expenses ALTER COLUMN recorded_by SET NOT NULL;

ALTER TABLE expenses ADD COLUMN total_amount NUMERIC(19, 2);
UPDATE expenses SET total_amount = amount_cents / 100.0;
ALTER TABLE expenses ALTER COLUMN total_amount SET NOT NULL;
ALTER TABLE expenses ADD CONSTRAINT expenses_total_amount_positive CHECK (total_amount > 0);
ALTER TABLE expenses DROP COLUMN amount_cents;

ALTER TABLE expenses ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';
ALTER TABLE expenses ADD COLUMN split_strategy VARCHAR(20) NOT NULL DEFAULT 'EQUAL';
ALTER TABLE expenses ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE expenses ALTER COLUMN currency DROP DEFAULT;
ALTER TABLE expenses ALTER COLUMN split_strategy DROP DEFAULT;
ALTER TABLE expenses ALTER COLUMN status DROP DEFAULT;

ALTER TABLE expense_shares RENAME COLUMN member_id TO participant_id;
ALTER TABLE expense_shares ADD COLUMN owed_share NUMERIC(19, 2);
UPDATE expense_shares SET owed_share = share_cents / 100.0;
ALTER TABLE expense_shares ALTER COLUMN owed_share SET NOT NULL;
ALTER TABLE expense_shares ADD CONSTRAINT expense_shares_owed_share_not_negative CHECK (owed_share >= 0);
ALTER TABLE expense_shares DROP COLUMN share_cents;
