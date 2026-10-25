-- No foreign keys to other modules: group_id, payer_id and member_id are plain UUIDs.
CREATE TABLE expense (
    id           UUID         PRIMARY KEY,
    group_id     UUID         NOT NULL,
    title        VARCHAR(200) NOT NULL,
    amount_cents BIGINT       NOT NULL CHECK (amount_cents > 0),
    expense_date DATE         NOT NULL,
    payer_id     UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX expense_group_idx ON expense (group_id, expense_date DESC, created_at DESC);

CREATE TABLE expense_share (
    expense_id  UUID   NOT NULL REFERENCES expense (id) ON DELETE CASCADE,
    position    INT    NOT NULL,
    member_id   UUID   NOT NULL,
    share_cents BIGINT NOT NULL CHECK (share_cents >= 0),
    PRIMARY KEY (expense_id, position)
);
