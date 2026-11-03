-- An invitation now belongs to one existing account instead of being a link anyone can use (design 2026-11-03, N2).
-- The token and the optional email go away; the invitee is the account's id (a plain UUID, no foreign key across
-- modules).
--
-- No conversion of old rows: the development database is recreated for this change (decided), so the table is empty
-- and the new column can be NOT NULL at once.
ALTER TABLE invitations DROP CONSTRAINT uq_invitations_token;
ALTER TABLE invitations DROP COLUMN token;
ALTER TABLE invitations DROP COLUMN email;
ALTER TABLE invitations ADD COLUMN invitee_id UUID NOT NULL;

-- "My open invitations" looks invitations up by invitee. There is deliberately no unique index on
-- (group_id, invitee_id): EXPIRED is derived from expires_at, never stored, so no index could tell an open invitation
-- from an expired one. The group row lock keeps one open invitation per person and group.
CREATE INDEX ix_invitations_invitee ON invitations (invitee_id);
