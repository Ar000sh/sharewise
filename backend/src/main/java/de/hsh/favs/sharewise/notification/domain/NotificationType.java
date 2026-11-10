package de.hsh.favs.sharewise.notification.domain;

/**
 * What an inbox entry is about. Each type is one event of another context. More types
 * arrive with later features (payments, waived debts, removal); a received invitation is
 * not an entry, it is asked from the group context.
 */
public enum NotificationType {

	/** Somebody joined through the recipient's invitation. */
	MEMBER_JOINED,

	/** Somebody turned the recipient's invitation down. */
	INVITATION_DECLINED,

	/** An admin made the recipient an admin. */
	MEMBER_PROMOTED

}
