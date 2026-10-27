package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns the expense context's event into a call on the ledger service. Asynchronous,
 * after the commit.
 */
@Component
class ExpenseRecordedListener {

	private final GroupLedgerService ledgers;

	ExpenseRecordedListener(GroupLedgerService ledgers) {
		this.ledgers = ledgers;
	}

	@ApplicationModuleListener
	void on(ExpenseRecorded event) {
		ledgers.bookExpense(event);
	}

}
