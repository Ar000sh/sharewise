-- Sign-in moves to Keycloak (design 2026-10-30, D3/D4): an account's id is now the Keycloak subject and ShareWise
-- keeps no password, no failed-login counter and no session. Existing accounts are test data without a Keycloak
-- twin, so they are dropped (D6), together with every row that holds their ids.
--
-- There are no foreign keys between modules, so the dependent rows are deleted explicitly. Every group, invitation,
-- expense and ledger row names at least one of the dropped accounts (memberships.user_id, invitations.created_by,
-- expenses.paid_by/recorded_by, expense_shares.participant_id, settlement_ledger_entries.user_id); a group or
-- ledger without its members would be an orphan, so all of it goes. The event publication registry goes too:
-- unfinished publications would be redelivered on restart for groups that no longer exist.
--
-- The checks for the tables of other modules are needed because Flyway orders by version: on a fresh database this
-- V105 runs before V201 to V402 have created those tables (out-of-order is only used on existing databases).
DO $$
BEGIN
    IF to_regclass('settlement_ledger_entries') IS NOT NULL THEN
        DELETE FROM settlement_ledger_entries;
        DELETE FROM settlement_ledgers;
    END IF;
    IF to_regclass('expense_shares') IS NOT NULL THEN
        DELETE FROM expense_shares;
        DELETE FROM expenses;
    END IF;
    IF to_regclass('memberships') IS NOT NULL THEN
        DELETE FROM invitations;
        DELETE FROM memberships;
        DELETE FROM groups;
    END IF;
END $$;

DELETE FROM event_publication;

DROP TABLE auth_session;
DELETE FROM accounts;

ALTER TABLE accounts DROP COLUMN password_hash;
ALTER TABLE accounts DROP COLUMN failed_login_attempts;
ALTER TABLE accounts DROP COLUMN last_login_at;
