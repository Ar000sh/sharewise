package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An expense was corrected (J5, R7): its old version became a revision and the new one
 * counts from now on. Carries both versions, so the ledger books the difference and the
 * inbox tells everybody involved without calling back. {@code revisionId} identifies this
 * one correction; the ledger uses it as its booking reference (design 2026-11-28 E5).
 */
public record ExpenseCorrected(ExpenseId expenseId, GroupId groupId, UUID revisionId, UserId correctedBy,
		UserId previousPaidBy, Money previousTotalAmount, List<ExpenseShare> previousShares, UserId paidBy,
		Money totalAmount, List<ExpenseShare> shares, Instant occurredAt) {

	public ExpenseCorrected {
		previousShares = List.copyOf(previousShares);
		shares = List.copyOf(shares);
	}

}
