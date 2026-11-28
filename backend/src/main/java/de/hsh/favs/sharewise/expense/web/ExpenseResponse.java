package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.expense.domain.ExpenseRevision;
import de.hsh.favs.sharewise.expense.domain.ExpenseStatus;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An expense as the API shows it, with its earlier versions (R7). Amounts are decimal
 * strings with two decimals, for example {@code "900.00"}.
 */
record ExpenseResponse(UUID id, String title, String totalAmount, LocalDate date, UUID paidBy, UUID recordedBy,
		SplitStrategy splitStrategy, ExpenseStatus status, List<ShareResponse> shares,
		List<RevisionResponse> revisions) {

	record ShareResponse(UUID participantId, String owedShare) {

	}

	/** The version a correction replaced. */
	record VersionResponse(String title, String totalAmount, LocalDate date, UUID paidBy, SplitStrategy splitStrategy,
			List<ShareResponse> shares) {

	}

	/** One correction: who, when, why, and the version it replaced. */
	record RevisionResponse(UUID correctedBy, Instant correctedAt, String reason, VersionResponse previous) {

	}

	static ExpenseResponse of(Expense expense) {
		return new ExpenseResponse(expense.expenseId().value(), expense.title().value(),
				expense.totalAmount().amount().toPlainString(), expense.date(), expense.paidBy().value(),
				expense.recordedBy().value(), expense.splitStrategy(), expense.status(), shares(expense.shares()),
				expense.revisions().stream().map(ExpenseResponse::revision).toList());
	}

	private static RevisionResponse revision(ExpenseRevision revision) {
		var previous = new VersionResponse(revision.previousTitle().value(),
				revision.previousTotalAmount().amount().toPlainString(), revision.previousDate(),
				revision.previousPaidBy().value(), revision.previousSplitStrategy(), shares(revision.previousShares()));
		return new RevisionResponse(revision.correctedBy().value(), revision.correctedAt(),
				revision.reason().orElse(null), previous);
	}

	private static List<ShareResponse> shares(List<ExpenseShare> shares) {
		return shares.stream()
			.map(share -> new ShareResponse(share.participantId().value(), share.owedShare().amount().toPlainString()))
			.toList();
	}

}
