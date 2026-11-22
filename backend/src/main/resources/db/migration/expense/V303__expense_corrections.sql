-- Corrections preserve the original expense and link the replacement; reversal ids track ledger completion.
ALTER TABLE expenses ADD COLUMN reversal_id UUID;
ALTER TABLE expenses ADD COLUMN corrected_from UUID;
