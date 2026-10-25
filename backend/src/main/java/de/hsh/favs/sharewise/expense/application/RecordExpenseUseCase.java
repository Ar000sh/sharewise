package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.Expense;

/** Port in: record an expense in a group. */
public interface RecordExpenseUseCase {

	Expense record(RecordExpenseCommand command);

}
