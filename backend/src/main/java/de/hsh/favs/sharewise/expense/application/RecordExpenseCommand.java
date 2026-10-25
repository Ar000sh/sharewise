package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.time.LocalDate;
import java.util.List;

public record RecordExpenseCommand(GroupId groupId, MemberId caller, String title, long amountCents, LocalDate date,
		MemberId payerId, List<MemberId> participantIds, SplitType split) {
}
