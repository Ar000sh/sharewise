package de.hsh.favs.sharewise.expense.domain;

/**
 * Whether an expense still counts. A new expense is ACTIVE; correcting (CORRECTED) and
 * removing (REMOVED) arrive in a later exercise and keep the expense visible in its
 * history.
 */
public enum ExpenseStatus {

	ACTIVE, CORRECTED, REMOVED

}
