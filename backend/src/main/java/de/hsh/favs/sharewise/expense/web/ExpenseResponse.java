package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.ExpenseStatus;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An expense as the API shows it. Amounts are decimal strings with two decimals, for
 * example {@code "900.00"}.
 */
record ExpenseResponse(UUID id, String title, String totalAmount, LocalDate date, UUID paidBy, UUID recordedBy,
		SplitStrategy splitStrategy, ExpenseStatus status, List<ShareResponse> shares) {

	record ShareResponse(UUID participantId, String owedShare) {

	}

	static ExpenseResponse of(Expense expense) {
		return new ExpenseResponse(expense.expenseId().value(), expense.title().value(),
				expense.totalAmount().amount().toPlainString(), expense.date(), expense.paidBy().value(),
				expense.recordedBy().value(), expense.splitStrategy(), expense.status(),
				expense.shares()
					.stream()
					.map(share -> new ShareResponse(share.participantId().value(),
							share.owedShare().amount().toPlainString()))
					.toList());
	}

}
