-- Sign-in data. Nullable: the dev seed users have no password and cannot sign in.
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);
CREATE UNIQUE INDEX users_email_unique ON users (email);
