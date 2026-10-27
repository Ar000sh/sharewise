package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Test data for an {@link Expense}. By default: "Dinner", 10.00 EUR on 2027-06-02, paid
 * by Alice and split between Alice and Bob. Built through {@link Expense#record}, so the
 * result is always valid.
 */
public class ExpenseFixture {

	public static final UserId ALICE = new UserId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));

	public static final UserId BOB = new UserId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"));

	public static final UserId CAROL = new UserId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003"));

	private GroupId groupId = new GroupId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));

	private String title = "Dinner";

	private long amountCents = 1_000;

	private LocalDate date = LocalDate.of(2027, 6, 2);

	private UserId payer = ALICE;

	private List<UserId> participants = List.of(ALICE, BOB);

	public ExpenseFixture withGroupId(GroupId groupId) {
		this.groupId = groupId;
		return this;
	}

	public ExpenseFixture withTitle(String title) {
		this.title = title;
		return this;
	}

	public ExpenseFixture withAmountCents(long amountCents) {
		this.amountCents = amountCents;
		return this;
	}

	public ExpenseFixture withDate(LocalDate date) {
		this.date = date;
		return this;
	}

	public ExpenseFixture withPayer(UserId payer) {
		this.payer = payer;
		return this;
	}

	public ExpenseFixture withParticipants(UserId... participants) {
		this.participants = List.of(participants);
		return this;
	}

	public Expense build() {
		return Expense.record(groupId, title, Money.eur(amountCents), date, payer, participants);
	}

}
