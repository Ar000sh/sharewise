-- The settlement context follows the domain diagram: entries book a decimal amount with a currency for a user.
ALTER TABLE settlement_ledger RENAME TO settlement_ledgers;
ALTER TABLE settlement_ledger_entry RENAME TO settlement_ledger_entries;
ALTER INDEX settlement_ledger_entry_by_group RENAME TO settlement_ledger_entries_by_group;

ALTER TABLE settlement_ledger_entries RENAME COLUMN member_id TO user_id;
ALTER TABLE settlement_ledger_entries ADD COLUMN delta_amount NUMERIC(19, 2);
UPDATE settlement_ledger_entries SET delta_amount = delta_cents / 100.0;
ALTER TABLE settlement_ledger_entries ALTER COLUMN delta_amount SET NOT NULL;
ALTER TABLE settlement_ledger_entries DROP COLUMN delta_cents;
ALTER TABLE settlement_ledger_entries ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';
ALTER TABLE settlement_ledger_entries ALTER COLUMN currency DROP DEFAULT;
