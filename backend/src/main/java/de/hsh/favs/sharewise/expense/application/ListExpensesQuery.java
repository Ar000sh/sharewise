package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.Expense;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.List;

/** Port in: the expenses of a group, newest first (by date, then creation time). */
public interface ListExpensesQuery {

	List<Expense> list(GroupId groupId, MemberId caller);

}
