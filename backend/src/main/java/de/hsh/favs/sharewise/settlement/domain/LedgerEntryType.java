package de.hsh.favs.sharewise.settlement.domain;

/**
 * Why a ledger entry exists (domain model: LedgerEntryType). Ex4 books only the two
 * expense types; the reversal, payment and waiver types arrive with corrections, removals
 * and settlement payments in Ex5.
 */
public enum LedgerEntryType {

	/** The payer is credited what they paid. */
	EXPENSE_PAID,

	/** A share holder is debited their share. */
	EXPENSE_OWED

}
