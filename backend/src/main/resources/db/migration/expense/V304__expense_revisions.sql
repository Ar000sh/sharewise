-- A correction keeps the version it replaced (R7, design 2026-11-28-expense-correction). One row per correction,
-- its shares in a table of their own, like expense_shares. V303 is used on the branch feature/expense-membership-copy.
CREATE TABLE expense_revisions (
    id                      UUID         PRIMARY KEY,
    expense_id              UUID         NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    -- 1 for the first correction: keeps the order when two corrections share a timestamp
    revision_no             INT          NOT NULL,
    previous_title          VARCHAR(200) NOT NULL,
    previous_total_amount   NUMERIC(19, 2) NOT NULL,
    currency                VARCHAR(3)   NOT NULL,
    previous_date           DATE         NOT NULL,
    previous_paid_by        UUID         NOT NULL,
    previous_split_strategy VARCHAR(20)  NOT NULL,
    corrected_by            UUID         NOT NULL,
    corrected_at            TIMESTAMPTZ  NOT NULL,
    reason                  VARCHAR(200),
    CONSTRAINT expense_revisions_once UNIQUE (expense_id, revision_no)
);

CREATE TABLE expense_revision_shares (
    revision_id    UUID           NOT NULL REFERENCES expense_revisions (id) ON DELETE CASCADE,
    position       INT            NOT NULL,
    participant_id UUID           NOT NULL,
    owed_share     NUMERIC(19, 2) NOT NULL CHECK (owed_share >= 0),
    PRIMARY KEY (revision_id, position)
);
