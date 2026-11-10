package de.hsh.favs.sharewise.settlement.domain;

/**
 * Why a ledger entry exists (domain model: LedgerEntryType). Expenses book the two
 * expense types (R14); confirmed settlement payments and waived debts book the payment
 * and waiver types (R16, R17, design D8). The reversal type arrives with corrections and
 * removals of expenses.
 */
public enum LedgerEntryType {

	/** The payer is credited what they paid. */
	EXPENSE_PAID,

	/** A share holder is debited their share. */
	EXPENSE_OWED,

	/** The debtor's confirmed payment: their balance rises by the amount (R16). */
	PAYMENT_SENT,

	/**
	 * The creditor received a confirmed payment: their balance falls by the amount (R16).
	 */
	PAYMENT_RECEIVED,

	/** A waived debt, on both sides: debtor's balance rises, creditor's falls (R17). */
	DEBT_WAIVED

}
