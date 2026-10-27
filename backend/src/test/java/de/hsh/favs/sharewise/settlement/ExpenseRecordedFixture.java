package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseId;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.domain.EqualSplit;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Test data for the {@link ExpenseRecorded} event the settlement context consumes. By
 * default: a new 900.00 EUR hotel paid by Alice and split equally between Alice, Bob,
 * Carol and David. Shares are calculated with the real equal split so the event is always
 * consistent.
 */
public class ExpenseRecordedFixture {

	public static final GroupId GROUP = new GroupId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));

	private ExpenseId expenseId = new ExpenseId(UUID.randomUUID());

	private GroupId groupId = GROUP;

	private UserId paidBy = AccountFixture.alice().userId();

	private Money totalAmount = Money.eur("900.00");

	private List<UserId> participants = List.of(AccountFixture.alice().userId(), AccountFixture.bob().userId(),
			AccountFixture.carol().userId(), AccountFixture.david().userId());

	private Instant occurredAt = MutableClock.START;

	public ExpenseRecordedFixture withExpenseId(ExpenseId expenseId) {
		this.expenseId = expenseId;
		return this;
	}

	public ExpenseRecordedFixture inGroup(GroupId groupId) {
		this.groupId = groupId;
		return this;
	}

	public ExpenseRecordedFixture paidBy(UserId paidBy) {
		this.paidBy = paidBy;
		return this;
	}

	public ExpenseRecordedFixture withTotalAmount(String totalAmount) {
		this.totalAmount = Money.eur(totalAmount);
		return this;
	}

	public ExpenseRecordedFixture splitBetween(UserId... participants) {
		this.participants = List.of(participants);
		return this;
	}

	public ExpenseRecorded build() {
		return new ExpenseRecorded(expenseId, groupId, paidBy, totalAmount, EqualSplit.split(totalAmount, participants),
				occurredAt);
	}

}
