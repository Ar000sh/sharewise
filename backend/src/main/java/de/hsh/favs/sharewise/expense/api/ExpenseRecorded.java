package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.List;

/**
 * An expense was recorded. Carries everything the settlement and notification contexts
 * need, so they never call back: who paid, who recorded it (the notification context
 * tells everybody involved except the recorder) and what each participant owes.
 */
public record ExpenseRecorded(ExpenseId expenseId, GroupId groupId, UserId paidBy, UserId recordedBy, Money totalAmount,
		List<ExpenseShare> shares, Instant occurredAt) {

	public ExpenseRecorded {
		shares = List.copyOf(shares);
	}

}
