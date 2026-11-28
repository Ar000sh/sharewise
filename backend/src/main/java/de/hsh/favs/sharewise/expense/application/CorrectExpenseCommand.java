package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.api.ExpenseId;
import de.hsh.favs.sharewise.expense.domain.ExpenseTitle;
import de.hsh.favs.sharewise.expense.domain.SplitEntry;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything needed to correct one expense: the new version, typed like a recording, and
 * why. {@code caller} is the person correcting it; {@code reason} may be null.
 */
public record CorrectExpenseCommand(GroupId groupId, ExpenseId expenseId, UserId caller, ExpenseTitle title,
		Money totalAmount, LocalDate date, UserId paidBy, List<SplitEntry> participants, SplitStrategy splitStrategy,
		String reason) {

}
