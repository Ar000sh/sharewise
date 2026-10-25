package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.domain.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record ExpenseResponse(UUID id, String title, long amountCents, LocalDate date, UUID payerId,
		List<ShareResponse> shares) {

	record ShareResponse(UUID memberId, long shareCents) {
	}

	static ExpenseResponse from(Expense expense) {
		return new ExpenseResponse(expense.id(), expense.title(), expense.amount().cents(), expense.date(),
				expense.payerId().value(),
				expense.shares().stream()
						.map(s -> new ShareResponse(s.memberId().value(), s.amount().cents())).toList());
	}

}
