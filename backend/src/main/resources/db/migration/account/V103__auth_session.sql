-- A session is the SHA-256 of the bearer token, so a leaked table cannot be replayed.
CREATE TABLE auth_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);
