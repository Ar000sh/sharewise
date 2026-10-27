package de.hsh.favs.sharewise.expense.domain;

import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.ALICE;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.BOB;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.CAROL;
import static de.hsh.favs.sharewise.expense.domain.ExpenseFixture.DAVID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseShare;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The rules of the Expense aggregate. No Spring, no database. */
class ExpenseTest {

	private static void assertRefusedWith(Runnable action, ErrorCode code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(code));
	}

	@Test
	void recordingCreatesAnActiveExpenseAndReturnsTheEventWithEverythingSettlementNeeds() {
		// when
		Expense.Recorded recorded = new ExpenseFixture().record();

		// then
		Expense expense = recorded.expense();
		assertThat(expense.title()).isEqualTo(new ExpenseTitle("Hotel"));
		assertThat(expense.totalAmount()).isEqualTo(Money.eur("900.00"));
		assertThat(expense.paidBy()).isEqualTo(ALICE);
		assertThat(expense.recordedBy()).isEqualTo(ALICE);
		assertThat(expense.status()).isEqualTo(ExpenseStatus.ACTIVE);
		assertThat(expense.splitStrategy()).isEqualTo(SplitStrategy.EQUAL);
		assertThat(expense.shares()).hasSize(4).extracting(ExpenseShare::owedShare).containsOnly(Money.eur("225.00"));
		assertThat(recorded.event()).isEqualTo(new ExpenseRecorded(expense.expenseId(), ExpenseFixture.GROUP, ALICE,
				Money.eur("900.00"), expense.shares(), MutableClock.START));
	}

	@Test
	void thePayerNeedNotTakePartInTheSplit() {
		// when alice pays for bob and carol only
		Expense expense = new ExpenseFixture().withParticipants(BOB, CAROL).withTotalAmount("90.00").build();

		// then
		assertThat(expense.shares()).extracting(ExpenseShare::participantId).containsExactly(BOB, CAROL);
	}

	@Test
	void aMemberMayRecordWhatSomeoneElsePaid() {
		// when bob records the hotel that alice paid
		Expense expense = new ExpenseFixture().withRecordedBy(BOB).build();

		// then
		assertThat(expense.paidBy()).isEqualTo(ALICE);
		assertThat(expense.recordedBy()).isEqualTo(BOB);
	}

	@Test
	void nothingCanBeRecordedInAGroupThatIsNotOpen() {
		assertRefusedWith(() -> new ExpenseFixture().withClosedGroup().build(), ErrorCode.GROUP_NOT_OPEN);
	}

	@Test
	void thePayerMustBeAMemberOfTheGroup() {
		assertRefusedWith(() -> new ExpenseFixture().withGroupMembers(ALICE, BOB)
			.withPaidBy(CAROL)
			.withParticipants(ALICE, BOB)
			.build(), ErrorCode.PAYER_NOT_IN_GROUP);
	}

	@Test
	void everyParticipantMustBeAMemberOfTheGroup() {
		assertRefusedWith(
				() -> new ExpenseFixture().withGroupMembers(ALICE, BOB).withParticipants(ALICE, DAVID).build(),
				ErrorCode.PARTICIPANT_NOT_IN_GROUP);
	}

	@Test
	void theRecorderMustBeAMemberOfTheGroup() {
		assertRefusedWith(() -> new ExpenseFixture().withGroupMembers(ALICE, BOB)
			.withRecordedBy(DAVID)
			.withParticipants(ALICE, BOB)
			.build(), ErrorCode.NOT_A_MEMBER);
	}

	@Test
	void anExpenseNeedsAtLeastOneParticipantAndNoOneTwice() {
		UserId bob = BOB;
		List<ExpenseShare> twice = List.of(new ExpenseShare(bob, Money.eur("5.00")),
				new ExpenseShare(bob, Money.eur("5.00")));

		assertRefusedWith(() -> Expense.record(new ExpenseFixture().groupView(), new ExpenseTitle("x"), ALICE, ALICE,
				Money.eur("10.00"), LocalDate.parse("2027-06-01"), List.of(), SplitStrategy.EQUAL, MutableClock.START),
				ErrorCode.NO_PARTICIPANTS);
		assertRefusedWith(() -> Expense.record(new ExpenseFixture().groupView(), new ExpenseTitle("x"), ALICE, ALICE,
				Money.eur("10.00"), LocalDate.parse("2027-06-01"), twice, SplitStrategy.EQUAL, MutableClock.START),
				ErrorCode.NO_PARTICIPANTS);
	}

	@Test
	void theTotalMustBePositive() {
		List<ExpenseShare> share = List.of(new ExpenseShare(BOB, Money.eur("0.00")));

		assertRefusedWith(() -> Expense.record(new ExpenseFixture().groupView(), new ExpenseTitle("x"), ALICE, ALICE,
				Money.eur("0.00"), LocalDate.parse("2027-06-01"), share, SplitStrategy.EQUAL, MutableClock.START),
				ErrorCode.AMOUNT_NOT_POSITIVE);
	}

	@Test
	void sharesThatDoNotAddUpToTheTotalAreAProgrammingError() {
		List<ExpenseShare> wrong = List.of(new ExpenseShare(BOB, Money.eur("4.99")));

		assertThatThrownBy(() -> Expense.record(new ExpenseFixture().groupView(), new ExpenseTitle("x"), ALICE, ALICE,
				Money.eur("5.00"), LocalDate.parse("2027-06-01"), wrong, SplitStrategy.EQUAL, MutableClock.START))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aTitleIsTrimmedNotBlankAndAtMost200Characters() {
		assertThat(new ExpenseTitle("  Dinner ").value()).isEqualTo("Dinner");
		assertThat(new ExpenseTitle("x".repeat(200)).value()).hasSize(200);
		assertRefusedWith(() -> new ExpenseTitle("   "), ErrorCode.BAD_REQUEST);
		assertRefusedWith(() -> new ExpenseTitle("x".repeat(201)), ErrorCode.BAD_REQUEST);
	}

}
