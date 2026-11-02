package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the ledger aggregate: book what the other contexts announce. */
@Service
public class GroupLedgerService {

	private final GroupLedgerRepository ledgers;

	private final Clock clock;

	GroupLedgerService(GroupLedgerRepository ledgers, Clock clock) {
		this.ledgers = ledgers;
		this.clock = clock;
	}

	/**
	 * Books an expense in its group's ledger, creating the ledger on the first event.
	 * Applying the same event twice books it once: the event may be delivered more than
	 * once.
	 */
	@Transactional
	public void bookExpense(ExpenseRecorded expense) {
		var group = expense.groupId().value();
		ledgers.createIfAbsent(group);
		GroupLedger ledger = ledgers.findForUpdate(group).orElseThrow();
		if (!ledgers.isBooked(group, expense.expenseId().value())) {
			ledger.appendExpenseEntry(expense, clock.instant());
			ledgers.save(ledger);
		}
	}

	/**
	 * Books a confirmed settlement payment in its group's ledger (R14, R16, design D8),
	 * creating the ledger if needed. Idempotent: the entries reference the payment id, so
	 * a second delivery finds them and books nothing.
	 */
	@Transactional
	public void bookSettlementPayment(SettlementPaymentConfirmed payment) {
		var group = payment.groupId().value();
		ledgers.createIfAbsent(group);
		GroupLedger ledger = ledgers.findForUpdate(group).orElseThrow();
		if (!ledgers.isBooked(group, payment.paymentId().value())) {
			ledger.appendSettlementPayment(payment, clock.instant());
			ledgers.save(ledger);
		}
	}

	/**
	 * Books a waived debt in its group's ledger (R14, R17, design D8); idempotent like
	 * {@link #bookSettlementPayment}.
	 */
	@Transactional
	public void bookDebtWaiver(DebtWaived waiver) {
		var group = waiver.groupId().value();
		ledgers.createIfAbsent(group);
		GroupLedger ledger = ledgers.findForUpdate(group).orElseThrow();
		if (!ledgers.isBooked(group, waiver.paymentId().value())) {
			ledger.appendDebtWaiver(waiver, clock.instant());
			ledgers.save(ledger);
		}
	}

}
