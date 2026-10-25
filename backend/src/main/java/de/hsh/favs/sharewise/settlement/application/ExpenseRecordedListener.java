package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Driving adapter: turns the event into a call on the application service. */
@Component
class ExpenseRecordedListener {

	private final ApplyExpenseToBalances applyExpense;

	ExpenseRecordedListener(ApplyExpenseToBalances applyExpense) {
		this.applyExpense = applyExpense;
	}

	@ApplicationModuleListener
	void on(ExpenseRecorded event) {
		applyExpense.apply(event);
	}

}
