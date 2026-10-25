package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded.Share;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.List;
import java.util.UUID;

/** Defaults: a fresh group, Alice pays 90.00 and Bob and Carol owe 45.00 each. */
class ExpenseRecordedFixture {

	static final MemberId ALICE = new MemberId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));
	static final MemberId BOB = new MemberId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"));
	static final MemberId CAROL = new MemberId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003"));

	private UUID expenseId = UUID.randomUUID();
	private GroupId group = new GroupId(UUID.randomUUID());
	private MemberId payer = ALICE;
	private long total = 9000;
	private List<Share> shares = List.of(new Share(BOB, 4500), new Share(CAROL, 4500));

	ExpenseRecordedFixture withExpenseId(UUID expenseId) {
		this.expenseId = expenseId;
		return this;
	}

	ExpenseRecordedFixture withGroup(GroupId group) {
		this.group = group;
		return this;
	}

	ExpenseRecordedFixture withPayer(MemberId payer) {
		this.payer = payer;
		return this;
	}

	ExpenseRecordedFixture withTotal(long total) {
		this.total = total;
		return this;
	}

	ExpenseRecordedFixture withShares(Share... shares) {
		this.shares = List.of(shares);
		return this;
	}

	ExpenseRecorded build() {
		return new ExpenseRecorded(expenseId, group, payer, total, shares);
	}

}
