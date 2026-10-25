CREATE TABLE groups (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(30)  NOT NULL,
    currency   VARCHAR(3)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- member_id carries the user UUID; no foreign key into the account module tables.
CREATE TABLE members (
    id        UUID        PRIMARY KEY,
    group_id  UUID        NOT NULL REFERENCES groups (id),
    member_id UUID        NOT NULL,
    role      VARCHAR(10) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_members_group_member UNIQUE (group_id, member_id)
);

CREATE INDEX ix_members_member ON members (member_id);
