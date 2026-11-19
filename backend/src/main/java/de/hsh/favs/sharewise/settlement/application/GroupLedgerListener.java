package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The events that change the ledger, each handed to the ledger service: an expense of the
 * expense context, and a confirmed settlement payment and a waived debt of this context
 * (R14, R16, R17, design D7). Every method is asynchronous, runs after the commit in its
 * own transaction and has its own entry in the event publication registry.
 */
@Component
class GroupLedgerListener {

	private final GroupLedgerService ledgers;

	GroupLedgerListener(GroupLedgerService ledgers) {
		this.ledgers = ledgers;
	}

	@ApplicationModuleListener
	void on(ExpenseRecorded event) {
		ledgers.bookExpense(event);
	}

	@ApplicationModuleListener
	void on(SettlementPaymentConfirmed event) {
		ledgers.bookSettlementPayment(event);
	}

	@ApplicationModuleListener
	void on(DebtWaived event) {
		ledgers.bookDebtWaiver(event);
	}

}
