package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.shared.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reverses the old expense and books its replacement atomically under a unique reference.
 */
public record ExpenseCorrected(UUID reversalId, GroupId groupId, UserId paidBy, Money totalAmount,
		List<ExpenseShare> shares, UserId correctedBy, ExpenseRecorded replacement, Instant occurredAt) {

	public ExpenseCorrected {
		shares = List.copyOf(shares);
	}
}
