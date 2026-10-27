-- The group context follows the domain diagram: members become memberships (with status and zero-balance flag),
-- and an invitation gets a status and an optional email address.
ALTER TABLE members RENAME TO memberships;
ALTER TABLE memberships RENAME COLUMN member_id TO user_id;
ALTER TABLE memberships RENAME CONSTRAINT members_pkey TO memberships_pkey;
ALTER TABLE memberships RENAME CONSTRAINT uq_members_group_member TO uq_memberships_group_user;
ALTER INDEX ix_members_member RENAME TO ix_memberships_user;
ALTER TABLE memberships ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE memberships ADD COLUMN has_zero_balance BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE memberships ALTER COLUMN status DROP DEFAULT;
ALTER TABLE memberships ALTER COLUMN has_zero_balance DROP DEFAULT;

ALTER TABLE invitations ADD COLUMN email VARCHAR(254);
ALTER TABLE invitations ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE invitations ALTER COLUMN status DROP DEFAULT;
