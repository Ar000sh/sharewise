package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.group.api.GroupMembershipView;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test data for an {@link Expense}. By default: "Hotel", 900.00 EUR paid and recorded by
 * Alice, split equally between Alice, Bob, Carol and David in an open group of those four
 * with Alice as admin. Override what the test is about.
 */
public class ExpenseFixture {

	public static final UserId ALICE = AccountFixture.alice().userId();

	public static final UserId BOB = AccountFixture.bob().userId();

	public static final UserId CAROL = AccountFixture.carol().userId();

	public static final UserId DAVID = AccountFixture.david().userId();

	public static final GroupId GROUP = new GroupId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));

	private boolean open = true;

	private Set<UserId> members = Set.of(ALICE, BOB, CAROL, DAVID);

	private String title = "Hotel";

	private Money totalAmount = Money.eur("900.00");

	private UserId paidBy = ALICE;

	private UserId recordedBy = ALICE;

	private List<UserId> participants = List.of(ALICE, BOB, CAROL, DAVID);

	private Instant now = MutableClock.START;

	public ExpenseFixture withClosedGroup() {
		this.open = false;
		return this;
	}

	public ExpenseFixture withGroupMembers(UserId... members) {
		this.members = Set.of(members);
		return this;
	}

	public ExpenseFixture withTitle(String title) {
		this.title = title;
		return this;
	}

	public ExpenseFixture withTotalAmount(String totalAmount) {
		this.totalAmount = Money.eur(totalAmount);
		return this;
	}

	public ExpenseFixture withPaidBy(UserId paidBy) {
		this.paidBy = paidBy;
		return this;
	}

	public ExpenseFixture withRecordedBy(UserId recordedBy) {
		this.recordedBy = recordedBy;
		return this;
	}

	public ExpenseFixture withParticipants(UserId... participants) {
		this.participants = List.of(participants);
		return this;
	}

	public GroupMembershipView groupView() {
		return new GroupMembershipView(GROUP, open, members, Set.of(ALICE));
	}

	/**
	 * The shares as the application calculates them, so the fixture builds through the
	 * real rules.
	 */
	public List<ExpenseShare> shares() {
		return EqualSplit.split(totalAmount, participants);
	}

	public Expense.Recorded record() {
		return Expense.record(groupView(), new ExpenseTitle(title), paidBy, recordedBy, totalAmount,
				LocalDate.parse("2027-06-01"), shares(), SplitStrategy.EQUAL, now);
	}

	public Expense build() {
		return record().expense();
	}

}
