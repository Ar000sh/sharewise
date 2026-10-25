CREATE TABLE invitations (
    id         UUID         PRIMARY KEY,
    group_id   UUID         NOT NULL REFERENCES groups (id),
    token      VARCHAR(64)  NOT NULL,
    created_by UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_invitations_token UNIQUE (token)
);
