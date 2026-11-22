package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.group.api.GroupFinancialHistory;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.expense.api.ExpenseHistory;
import de.hsh.favs.sharewise.settlement.persistence.*;
import de.hsh.favs.sharewise.settlement.domain.PaymentStatus;
import de.hsh.favs.sharewise.shared.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dependency inversion: Group owns the closure read contract, Settlement supplies booked
 * financial facts.
 */
@Service
class GroupFinancialHistoryImpl implements GroupFinancialHistory {

	private final ExpenseHistory expenses;

	private final SettlementPaymentRepository payments;

	private final GroupLedgerRepository ledgers;

	private final GroupMembership groups;

	GroupFinancialHistoryImpl(ExpenseHistory expenses, SettlementPaymentRepository payments,
			GroupLedgerRepository ledgers, GroupMembership groups) {
		this.expenses = expenses;
		this.payments = payments;
		this.ledgers = ledgers;
		this.groups = groups;
	}

	@Override
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
	public Snapshot capture(GroupId group) {
		// Lock order everywhere is group, then ledger. Booking listeners only take the
		// ledger lock.
		ledgers.createIfAbsent(group.value());
		ledgers.findForUpdate(group.value()).orElseThrow();
		var expenseRows = expenses.of(group);
		var paymentRows = payments.findByGroupIdOrderByCreatedAtDesc(group.value());
		if (paymentRows.stream().anyMatch(p -> p.status() == PaymentStatus.PENDING))
			throw new DomainException(ErrorCode.PAYMENTS_PENDING, "Answer all pending payments before closing.");
		for (var expense : expenseRows) {
			requireBooked(group, expense.id());
			if (expense.reversalId() != null)
				requireBooked(group, expense.reversalId());
		}
		for (var payment : paymentRows)
			if (payment.status() == PaymentStatus.CONFIRMED || payment.status() == PaymentStatus.WAIVED)
				requireBooked(group, payment.paymentId().value());
		Map<UUID, String> balances = new TreeMap<>();
		groups.members(group).forEach(m -> balances.put(m.value(), "0.00"));
		ledgers.balances(group).forEach(b -> balances.put(b.userId().value(), b.netBalance().amount().toPlainString()));
		return new Snapshot(expenseRows, paymentRows.stream()
			.map(p -> new Payment(p.paymentId().value(), p.fromDebtor().value(), p.toCreditor().value(),
					p.amount().amount().toPlainString(), p.status().name(), p.note(), p.createdAt(), p.resolvedAt()))
			.toList(), balances.entrySet().stream().map(b -> new Balance(b.getKey(), b.getValue())).toList());
	}

	private void requireBooked(GroupId group, UUID reference) {
		if (!ledgers.isBooked(group.value(), reference))
			throw new DomainException(ErrorCode.FINANCIAL_PROCESSING_PENDING,
					"Financial updates are still processing. Try again shortly.");
	}

}
