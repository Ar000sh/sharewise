-- Accounts get a username: the name people type to invite somebody (design 2026-11-03, N3/N4). Keycloak owns it and
-- ShareWise copies it on the first login, like email and display name (D10).
--
-- No backfill: the development database and the Keycloak realm are recreated for this change (decided), so no
-- account exists without a username. An existing database with accounts must be deleted first.
ALTER TABLE accounts ADD COLUMN username VARCHAR(30) NOT NULL;

-- Unique, because an invitation finds exactly one account by it; the value is stored lower case.
CREATE UNIQUE INDEX accounts_username_unique ON accounts (username);
