package de.hsh.favs.sharewise.group.domain;

/**
 * Where an invitation is in its life. A link invitation (no email) stays PENDING and can
 * be used by several people until it expires; an invitation to an email address becomes
 * ACCEPTED when used. DECLINED and EXPIRED are reached by the inbox and expiry handling
 * of later exercises; today an expired invitation is recognised by its expiry time.
 */
public enum InvitationStatus {

	PENDING, ACCEPTED, DECLINED, EXPIRED

}
