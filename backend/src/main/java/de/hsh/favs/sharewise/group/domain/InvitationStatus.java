package de.hsh.favs.sharewise.group.domain;

/**
 * Where an invitation is in its life. PENDING, ACCEPTED and DECLINED are stored; EXPIRED
 * is never stored, it is derived from the expiry time (see
 * {@link Invitation#statusAt(java.time.Instant)}), so no reader sees a stale PENDING.
 */
public enum InvitationStatus {

	PENDING, ACCEPTED, DECLINED, EXPIRED

}
