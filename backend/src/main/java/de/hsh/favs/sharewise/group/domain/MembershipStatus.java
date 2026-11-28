package de.hsh.favs.sharewise.group.domain;

/**
 * Whether a person is currently in the group. Leaving and removal turn ACTIVE into
 * FORMER_MEMBER; accepting a new invitation turns it back (design 2026-11-28 M3).
 */
public enum MembershipStatus {

	ACTIVE, FORMER_MEMBER

}
