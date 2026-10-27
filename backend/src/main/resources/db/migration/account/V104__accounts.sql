-- The account context follows the domain diagram: users become accounts with a status and login bookkeeping.
-- Remove the seeded dev users of the removed dev profile, they have no email or password and cannot sign in.
DELETE FROM auth_session WHERE user_id IN (
    'aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000002',
    'aaaaaaaa-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000004');
DELETE FROM users WHERE email IS NULL;

ALTER TABLE users RENAME TO accounts;
ALTER TABLE accounts RENAME CONSTRAINT users_pkey TO accounts_pkey;
ALTER INDEX users_email_unique RENAME TO accounts_email_unique;

ALTER TABLE accounts ALTER COLUMN email SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE accounts ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE accounts ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN registered_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE accounts ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE accounts ALTER COLUMN status DROP DEFAULT;
ALTER TABLE accounts ALTER COLUMN failed_login_attempts DROP DEFAULT;
ALTER TABLE accounts ALTER COLUMN registered_at DROP DEFAULT;
