package de.hsh.favs.sharewise.expense.domain;

/**
 * Whether an expense still counts. A new expense is ACTIVE; a corrected one (CORRECTED)
 * counts with its current version and keeps the old ones as revisions (R7); a removed one
 * (REMOVED) no longer counts but stays visible (R8).
 */
public enum ExpenseStatus {

	ACTIVE, CORRECTED, REMOVED

}
