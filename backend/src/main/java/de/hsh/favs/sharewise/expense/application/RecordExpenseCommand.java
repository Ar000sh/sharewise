package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.expense.domain.ExpenseTitle;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.LocalDate;
import java.util.List;

/** Everything needed to record one expense. {@code caller} is the person recording it. */
public record RecordExpenseCommand(GroupId groupId, UserId caller, ExpenseTitle title, Money totalAmount,
		LocalDate date, UserId paidBy, List<UserId> participants, SplitStrategy splitStrategy) {

}
