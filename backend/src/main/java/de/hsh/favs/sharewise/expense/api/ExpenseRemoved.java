package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An expense was removed (J6, R8): it no longer counts for balances but stays visible.
 * Carries the version that counted until now, so the ledger takes it back without calling
 * back. {@code removalId} is the ledger's booking reference (design 2026-11-28 E5).
 */
public record ExpenseRemoved(ExpenseId expenseId, GroupId groupId, UUID removalId, UserId removedBy, UserId paidBy,
		Money totalAmount, List<ExpenseShare> shares, Instant occurredAt) {

	public ExpenseRemoved {
		shares = List.copyOf(shares);
	}

}
