package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded.Share;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;
import java.util.UUID;

/** Defaults: a fresh group, Alice pays 90.00 and Bob and Carol owe 45.00 each. */
public class ExpenseRecordedFixture {

	public static final UserId ALICE = new UserId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));

	public static final UserId BOB = new UserId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"));

	public static final UserId CAROL = new UserId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003"));

	private UUID expenseId = UUID.randomUUID();

	private GroupId group = new GroupId(UUID.randomUUID());

	private UserId payer = ALICE;

	private long total = 9000;

	private List<Share> shares = List.of(new Share(BOB, 4500), new Share(CAROL, 4500));

	public ExpenseRecordedFixture withExpenseId(UUID expenseId) {
		this.expenseId = expenseId;
		return this;
	}

	public ExpenseRecordedFixture withGroup(GroupId group) {
		this.group = group;
		return this;
	}

	public ExpenseRecordedFixture withPayer(UserId payer) {
		this.payer = payer;
		return this;
	}

	public ExpenseRecordedFixture withTotal(long total) {
		this.total = total;
		return this;
	}

	public ExpenseRecordedFixture withShares(Share... shares) {
		this.shares = List.of(shares);
		return this;
	}

	public ExpenseRecorded build() {
		return new ExpenseRecorded(expenseId, group, payer, total, shares);
	}

}
