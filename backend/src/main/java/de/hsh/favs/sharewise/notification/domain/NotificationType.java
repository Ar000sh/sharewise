package de.hsh.favs.sharewise.notification.domain;

/**
 * What an inbox entry is about. Each type is one event of another context. More types
 * arrive with later features (removal); a received invitation and a payment waiting for
 * the recipient's answer are not entries, they are state asked from the group and the
 * settlement context (I1).
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
	EXPENSE_RECORDED

}
