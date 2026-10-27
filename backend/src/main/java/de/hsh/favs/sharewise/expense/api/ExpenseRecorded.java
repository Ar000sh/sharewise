package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.List;

/**
 * An expense was recorded. Carries everything the settlement context needs, so it never
 * calls back.
 */
public record ExpenseRecorded(ExpenseId expenseId, GroupId groupId, UserId paidBy, Money totalAmount,
		List<ExpenseShare> shares, Instant occurredAt) {

	public ExpenseRecorded {
		shares = List.copyOf(shares);
	}

}
