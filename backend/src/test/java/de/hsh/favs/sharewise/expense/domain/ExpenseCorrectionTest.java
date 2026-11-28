package de.hsh.favs.sharewise.expense.domain;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.CAROL;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.DAVID;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseCorrected;
import de.hsh.favs.sharewise.expense.api.ExpenseRemoved;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.group.api.GroupMembershipView;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The rules of correcting and removing an expense (J5, J6, R5 to R8, R10). The default
 * expense is the fixture's hotel: 900.00 paid and recorded by Alice, split equally
 * between Alice, Bob, Carol and David; Alice is the admin. No Spring, no database.
 */
class ExpenseCorrectionTest {

	static final Instant LATER = MutableClock.START.plus(Duration.ofDays(1));

	static final LocalDate DAY = LocalDate.parse("2027-06-01");

	static final UserId EVE = AccountFixture.eve().userId();

	Expense hotel = new ExpenseFixture().build();

	/** An open group of the four, with Alice as admin, plus whoever is given. */
	static GroupMembershipView openGroupWith(UserId... more) {
		var members = new HashSet<>(Set.of(ALICE, BOB, CAROL, DAVID));
		members.addAll(List.of(more));
		return new GroupMembershipView(GROUP, true, members, Set.of(ALICE));
	}

	static List<ExpenseShare> equally(String total, UserId... participants) {
		return EqualSplit.split(Money.eur(total), List.of(participants));
	}

	private ExpenseCorrected correct(Expense expense, GroupMembershipView view, UserId actor, String total,
			List<ExpenseShare> shares) {
		return expense.correct(view, new ExpenseTitle("Hotel"), ALICE, Money.eur(total), DAY, shares,
				SplitStrategy.EQUAL, "Receipt was wrong", actor, LATER);
	}

	private static void assertRefusedWith(Runnable action, ErrorCode code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(code));
	}

	@Test
	void correctingReplacesTheVersionKeepsTheOldOneAsRevisionAndTheEventCarriesBoth() {
		// given
		var oldShares = hotel.shares();
		var newShares = equally("1000.00", ALICE, BOB, CAROL, DAVID);

		// when
		ExpenseCorrected corrected = correct(hotel, openGroupWith(), ALICE, "1000.00", newShares);

		// then the new version counts, the old one is a revision (R7)
		assertThat(hotel.status()).isEqualTo(ExpenseStatus.CORRECTED);
		assertThat(hotel.totalAmount()).isEqualTo(Money.eur("1000.00"));
		assertThat(hotel.shares()).extracting(ExpenseShare::owedShare).containsOnly(Money.eur("250.00"));
		assertThat(hotel.revisions()).singleElement().satisfies(revision -> {
			assertThat(revision.previousTotalAmount()).isEqualTo(Money.eur("900.00"));
			assertThat(revision.previousShares()).isEqualTo(oldShares);
			assertThat(revision.correctedBy()).isEqualTo(ALICE);
			assertThat(revision.correctedAt()).isEqualTo(LATER);
			assertThat(revision.reason()).contains("Receipt was wrong");
		});
		assertThat(corrected).isEqualTo(new ExpenseCorrected(hotel.expenseId(), GROUP, hotel.revisions().get(0).id(),
				ALICE, ALICE, Money.eur("900.00"), oldShares, ALICE, Money.eur("1000.00"), newShares, LATER));
	}

	@Test
	void aMemberWhoJoinedLaterCanBeAddedByACorrection() {
		// when Eve joined after the hotel was recorded, and the hotel is corrected to
		// include her (R10)
		correct(hotel, openGroupWith(EVE), ALICE, "900.00", equally("900.00", ALICE, BOB, CAROL, DAVID, EVE));

		// then
		assertThat(hotel.shares()).extracting(ExpenseShare::participantId).contains(EVE);
	}

	@Test
	void theRecorderThePayerAndAnAdminMayCorrectAnybodyElseMayNot() {
		// given Bob paid, Carol recorded, Alice is the admin, David is just in the split
		Expense taxi = new ExpenseFixture().withPaidBy(BOB).withRecordedBy(CAROL).build();
		var shares = equally("900.00", ALICE, BOB, CAROL, DAVID);

		// when / then (R6)
		assertRefusedWith(() -> correct(taxi, openGroupWith(), DAVID, "900.00", shares),
				ErrorCode.NOT_ALLOWED_TO_CHANGE_EXPENSE);
		assertThat(taxi.revisions()).isEmpty();
		correct(taxi, openGroupWith(), BOB, "900.00", shares);
		correct(taxi, openGroupWith(), CAROL, "900.00", shares);
		correct(taxi, openGroupWith(), ALICE, "900.00", shares);
		assertThat(taxi.revisions()).hasSize(3);
	}

	@Test
	void nothingCanBeCorrectedOrRemovedWhileTheGroupIsNotOpen() {
		// given
		var closed = new GroupMembershipView(GROUP, false, Set.of(ALICE, BOB, CAROL, DAVID), Set.of(ALICE));

		// when / then (R5, L3)
		assertRefusedWith(() -> correct(hotel, closed, ALICE, "1000.00", equally("1000.00", ALICE, BOB)),
				ErrorCode.GROUP_NOT_OPEN);
		assertRefusedWith(() -> hotel.remove(closed, ALICE, LATER), ErrorCode.GROUP_NOT_OPEN);
		assertThat(hotel.status()).isEqualTo(ExpenseStatus.ACTIVE);
	}

	@Test
	void anExpenseThatInvolvesSomebodyWhoLeftCannotBeCorrectedOrRemoved() {
		// given David left the group
		var withoutDavid = new GroupMembershipView(GROUP, true, Set.of(ALICE, BOB, CAROL), Set.of(ALICE));

		// when / then (design 2026-11-28 E3)
		assertRefusedWith(() -> correct(hotel, withoutDavid, ALICE, "600.00", equally("600.00", ALICE, BOB, CAROL)),
				ErrorCode.FORMER_MEMBER_INVOLVED);
		assertRefusedWith(() -> hotel.remove(withoutDavid, ALICE, LATER), ErrorCode.FORMER_MEMBER_INVOLVED);
		assertThat(hotel.status()).isEqualTo(ExpenseStatus.ACTIVE);
		assertThat(hotel.revisions()).isEmpty();
	}

	@Test
	void theNewVersionFollowsTheRulesOfRecording() {
		// when / then
		assertRefusedWith(() -> correct(hotel, openGroupWith(), ALICE, "900.00", equally("900.00", BOB, CAROL)),
				ErrorCode.RECORDER_NOT_PARTICIPANT);
		assertRefusedWith(() -> correct(hotel, openGroupWith(), ALICE, "900.00", equally("800.00", ALICE, BOB)),
				ErrorCode.SHARES_NOT_TOTAL);
		assertRefusedWith(() -> correct(hotel, openGroupWith(), ALICE, "900.00", equally("900.00", ALICE, EVE)),
				ErrorCode.PARTICIPANT_NOT_IN_GROUP);
		assertRefusedWith(
				() -> hotel.correct(openGroupWith(), new ExpenseTitle("Hotel"), EVE, Money.eur("900.00"), DAY,
						equally("900.00", ALICE), SplitStrategy.EQUAL, null, ALICE, LATER),
				ErrorCode.PAYER_NOT_IN_GROUP);
		assertThat(hotel.status()).isEqualTo(ExpenseStatus.ACTIVE);
		assertThat(hotel.totalAmount()).isEqualTo(Money.eur("900.00"));
	}

	@Test
	void twoCorrectionsKeepBothEarlierVersionsInOrder() {
		// when
		correct(hotel, openGroupWith(), ALICE, "1000.00", equally("1000.00", ALICE, BOB, CAROL, DAVID));
		correct(hotel, openGroupWith(), ALICE, "1100.00", equally("1100.00", ALICE, BOB, CAROL, DAVID));

		// then
		assertThat(hotel.revisions()).extracting(ExpenseRevision::previousTotalAmount)
			.containsExactly(Money.eur("900.00"), Money.eur("1000.00"));
	}

	@Test
	void removingMarksItRemovedAndTheEventCarriesTheVersionThatCounted() {
		// when
		ExpenseRemoved removed = hotel.remove(openGroupWith(), ALICE, LATER);

		// then (R8)
		assertThat(hotel.status()).isEqualTo(ExpenseStatus.REMOVED);
		assertThat(removed).isEqualTo(new ExpenseRemoved(hotel.expenseId(), GROUP, removed.removalId(), ALICE, ALICE,
				Money.eur("900.00"), hotel.shares(), LATER));
	}

	@Test
	void aRemovedExpenseCannotBeChangedAgain() {
		// given
		hotel.remove(openGroupWith(), ALICE, LATER);

		// when / then
		assertRefusedWith(() -> hotel.remove(openGroupWith(), ALICE, LATER), ErrorCode.EXPENSE_REMOVED);
		assertRefusedWith(
				() -> correct(hotel, openGroupWith(), ALICE, "900.00", equally("900.00", ALICE, BOB, CAROL, DAVID)),
				ErrorCode.EXPENSE_REMOVED);
		assertThat(hotel.revisions()).isEmpty();
	}

}
