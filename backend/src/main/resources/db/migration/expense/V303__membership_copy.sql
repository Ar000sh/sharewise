-- The expense context keeps its own copy of "who is in which group", fed by the group context's events
-- (GroupCreated, MemberJoined). as_of is the occurredAt of the last event applied, so an older event that arrives
-- late never overwrites a newer fact. No foreign keys into the group tables.
CREATE TABLE expense_known_groups (
    group_id UUID        PRIMARY KEY,
    is_open  BOOLEAN     NOT NULL,
    as_of    TIMESTAMPTZ NOT NULL
);

CREATE TABLE expense_known_members (
    group_id  UUID        NOT NULL,
    user_id   UUID        NOT NULL,
    is_admin  BOOLEAN     NOT NULL,
    is_active BOOLEAN     NOT NULL,
    as_of     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

-- One-off start of the copy for groups that existed before it did. Later changes arrive as events.
INSERT INTO expense_known_groups (group_id, is_open, as_of)
SELECT id, status = 'OPEN', created_at FROM groups;

INSERT INTO expense_known_members (group_id, user_id, is_admin, is_active, as_of)
SELECT group_id, user_id, role = 'ADMIN', status = 'ACTIVE', joined_at FROM memberships;
