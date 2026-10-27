package de.hsh.favs.sharewise.group.domain;

/**
 * Whether a person is currently in the group. Leaving and removal (later exercises) turn
 * ACTIVE into FORMER_MEMBER.
 */
public enum MembershipStatus {

	ACTIVE, FORMER_MEMBER

}
