package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;

/** Port out: persistence of expenses. */
public interface ExpenseRepositoryPort {

	void save(Expense expense);

	/** Newest first: by date, then creation time. */
	List<Expense> findByGroup(GroupId groupId);

}
