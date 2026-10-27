package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;
import java.util.UUID;

/** Carries everything Settlement needs, so Settlement never calls Expense. */
public record ExpenseRecorded(UUID expenseId, GroupId groupId, UserId payerId, long totalCents, List<Share> shares) {

	public record Share(UserId memberId, long shareCents) {
	}

}
