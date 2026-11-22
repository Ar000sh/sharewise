package de.hsh.favs.sharewise.expense.api;

import de.hsh.favs.sharewise.group.api.GroupFinancialHistory;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;

/** Immutable expense values, including replaced revisions, for closure snapshots. */
public interface ExpenseHistory {

	List<GroupFinancialHistory.Expense> of(GroupId group);

}
