package de.hsh.favs.sharewise.notification.domain;

/**
 * What an inbox entry is about. Each type is one event of another context. More types
 * arrive with later features (expense correction and removal); a received invitation and
 * a payment waiting for the recipient's answer are not entries, they are state asked from
 * the group and the settlement context (I1).
 */
public enum NotificationType {

	/** Somebody joined through the recipient's invitation. */
	MEMBER_JOINED,

	/** Somebody turned the recipient's invitation down. */
	INVITATION_DECLINED,

	/** The creditor confirmed that the recipient's payment arrived (R16, I2). */
	PAYMENT_CONFIRMED,

	/** The creditor said the recipient's payment did not arrive (R16, I2). */
	PAYMENT_REJECTED,

	/** The creditor forgave part of the recipient's debt (R17, I2). */
	DEBT_WAIVED,

	/**
	 * Somebody else recorded an expense in which the recipient owes a share or which the
	 * recipient paid (J4).
	 */
	EXPENSE_RECORDED,

	/** An admin made the recipient an admin. */
	MEMBER_PROMOTED,

	/** An admin removed the recipient from the group (J11, design 2026-11-28 M7). */
	MEMBER_REMOVED,

	/** An admin closed the recipient's group for settlement (J12). */
	GROUP_CLOSED,

	/** An admin reopened the recipient's group (J12b). */
	GROUP_REOPENED,

	/**
	 * The recipient's group was archived (J13), by an admin or automatically, then
	 * without an actor.
	 */
	GROUP_ARCHIVED

}
