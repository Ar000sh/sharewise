package de.hsh.favs.sharewise.expense.application;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.CAROL;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.DAVID;

import de.hsh.favs.sharewise.expense.domain.ExpenseFixture;
import de.hsh.favs.sharewise.expense.domain.ExpenseTitle;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.LocalDate;
import java.util.List;

/**
 * Test data for a {@link RecordExpenseCommand}. By default: Alice records the 900.00 EUR
 * hotel she paid, split equally between Alice, Bob, Carol and David.
 */
public class RecordExpenseCommandFixture {

	private GroupId groupId = ExpenseFixture.GROUP;

	private UserId caller = ALICE;

	private Money totalAmount = Money.eur("900.00");

	private UserId paidBy = ALICE;

	private List<UserId> participants = List.of(ALICE, BOB, CAROL, DAVID);

	private SplitStrategy splitStrategy = SplitStrategy.EQUAL;

	public RecordExpenseCommandFixture withCaller(UserId caller) {
		this.caller = caller;
		return this;
	}

	public RecordExpenseCommandFixture withTotalAmount(String totalAmount) {
		this.totalAmount = Money.eur(totalAmount);
		return this;
	}

	public RecordExpenseCommandFixture withPaidBy(UserId paidBy) {
		this.paidBy = paidBy;
		return this;
	}

	public RecordExpenseCommandFixture withParticipants(UserId... participants) {
		this.participants = List.of(participants);
		return this;
	}

	public RecordExpenseCommandFixture withSplitStrategy(SplitStrategy splitStrategy) {
		this.splitStrategy = splitStrategy;
		return this;
	}

	public RecordExpenseCommand build() {
		return new RecordExpenseCommand(groupId, caller, new ExpenseTitle("Hotel"), totalAmount,
				LocalDate.parse("2027-06-01"), paidBy, participants, splitStrategy);
	}

}
