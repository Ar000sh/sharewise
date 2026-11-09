-- An admin chooses the role the invitee gets when they accept (design 2026-11-09, A3).
--
-- DEFAULT 'MEMBER' also covers invitations that are open when this runs: they were sent before roles existed and
-- meant a plain member.
ALTER TABLE invitations ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'MEMBER';
