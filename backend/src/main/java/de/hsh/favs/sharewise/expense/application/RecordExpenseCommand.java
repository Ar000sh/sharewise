package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.LocalDate;
import java.util.List;

public record RecordExpenseCommand(GroupId groupId, UserId caller, String title, long amountCents, LocalDate date,
		UserId payerId, List<UserId> participantIds, SplitType split) {
}
