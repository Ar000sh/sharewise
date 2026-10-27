package de.hsh.favs.sharewise.expense.application;

import de.hsh.favs.sharewise.shared.UserId;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;

import de.hsh.favs.sharewise.shared.GroupId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Test data for a {@link RecordExpenseCommand}. By default: Alice records a 10.00 EUR
 * dinner she paid, split with Bob.
 */
public class RecordExpenseCommandFixture {

	private GroupId groupId = new GroupId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));

	private UserId caller = ALICE;

	private String title = "Dinner";

	private long amountCents = 1_000;

	private LocalDate date = LocalDate.of(2027, 6, 2);

	private UserId payer = ALICE;

	private List<UserId> participants = List.of(ALICE, BOB);

	private SplitType split = SplitType.EQUAL;

	public RecordExpenseCommandFixture withGroupId(GroupId groupId) {
		this.groupId = groupId;
		return this;
	}

	public RecordExpenseCommandFixture withCaller(UserId caller) {
		this.caller = caller;
		return this;
	}

	public RecordExpenseCommandFixture withAmountCents(long amountCents) {
		this.amountCents = amountCents;
		return this;
	}

	public RecordExpenseCommandFixture withPayer(UserId payer) {
		this.payer = payer;
		return this;
	}

	public RecordExpenseCommandFixture withParticipants(UserId... participants) {
		this.participants = List.of(participants);
		return this;
	}

	public RecordExpenseCommandFixture withSplit(SplitType split) {
		this.split = split;
		return this;
	}

	public RecordExpenseCommand build() {
		return new RecordExpenseCommand(groupId, caller, title, amountCents, date, payer, participants, split);
	}

}
