package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Closure's read port, implemented by settlement. Caller holds the group write lock. */
public interface GroupFinancialHistory {

	/**
	 * Returns fully booked history or refuses closure while processing/payments remain.
	 */
	Snapshot capture(GroupId group);

	record Snapshot(List<Expense> expenses, List<Payment> payments, List<Balance> balances) {
	}

	record Expense(UUID id, String title, String totalAmount, LocalDate date, UUID paidBy, UUID recordedBy,
			String status, List<Share> shares, Instant createdAt, UUID reversalId, UUID correctedFrom) {
	}

	record Share(UUID participantId, String owedShare) {
	}

	record Payment(UUID id, UUID fromDebtor, UUID toCreditor, String amount, String status, String note,
			Instant createdAt, Instant resolvedAt) {
	}

	record Balance(UUID userId, String netBalance) {
	}

}
