package de.hsh.favs.sharewise.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseCorrected;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseRemoved;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The rules of the GroupLedger aggregate: what an expense books, and what a balance is.
 * No Spring, no database.
 */
class GroupLedgerTest {

	UserId alice = AccountFixture.alice().userId();

	UserId bob = AccountFixture.bob().userId();

	UserId carol = AccountFixture.carol().userId();

	UserId david = AccountFixture.david().userId();

	GroupLedger ledger = new GroupLedger(ExpenseRecordedFixture.GROUP);

	private Money balanceOf(UserId user) {
		return ledger.projectBalances()
			.stream()
			.filter(balance -> balance.userId().equals(user))
			.map(MemberBalance::netBalance)
			.findFirst()
			.orElseThrow();
	}

	@Test
	void anExpenseCreditsThePayerTheTotalAndDebitsEveryParticipantTheirShare() {
		// when alice pays 900.00 for all four
		ledger.appendExpenseEntry(new ExpenseRecordedFixture().build(), MutableClock.START);

		// then
		assertThat(ledger.entries()).hasSize(5);
		assertThat(ledger.entries()).filteredOn(e -> e.type() == LedgerEntryType.EXPENSE_PAID)
			.singleElement()
			.satisfies(e -> {
				assertThat(e.userId()).isEqualTo(alice);
				assertThat(e.deltaAmount()).isEqualTo(Money.eur("900.00"));
				assertThat(e.occurredAt()).isEqualTo(MutableClock.START);
			});
		assertThat(ledger.entries()).filteredOn(e -> e.type() == LedgerEntryType.EXPENSE_OWED)
			.extracting(LedgerEntry::deltaAmount)
			.containsOnly(Money.eur("-225.00"));
	}

	@Test
	void theMallorcaExampleGivesTheDocumentedBalances() {
		// given the hotel (900.00 by alice) and the dinner (180.00 by bob), both split
		// four ways
		ledger.appendExpenseEntry(new ExpenseRecordedFixture().build(), MutableClock.START);
		ledger.appendExpenseEntry(new ExpenseRecordedFixture().paidBy(bob).withTotalAmount("180.00").build(),
				MutableClock.START);

		// then
		assertThat(balanceOf(alice)).isEqualTo(Money.eur("630.00"));
		assertThat(balanceOf(bob)).isEqualTo(Money.eur("-90.00"));
		assertThat(balanceOf(carol)).isEqualTo(Money.eur("-270.00"));
		assertThat(balanceOf(david)).isEqualTo(Money.eur("-270.00"));
	}

	@Test
	void thePayerWhoTakesNoPartIsOwedTheWholeTotal() {
		// when alice pays 90.00 for bob and carol only
		ledger.appendExpenseEntry(
				new ExpenseRecordedFixture().withTotalAmount("90.00").splitBetween(bob, carol).build(),
				MutableClock.START);

		// then
		assertThat(balanceOf(alice)).isEqualTo(Money.eur("90.00"));
		assertThat(balanceOf(bob)).isEqualTo(Money.eur("-45.00"));
	}

	@Test
	void aConfirmedPaymentRaisesTheDebtorAndLowersTheCreditorAndKeepsTheSumAtZero() {
		// given bob owes alice 225.00 after the hotel
		ledger.appendExpenseEntry(new ExpenseRecordedFixture().build(), MutableClock.START);
		var paymentId = new SettlementPaymentId(UUID.randomUUID());

		// when bob's 100.00 is confirmed
		ledger.appendSettlementPayment(new SettlementPaymentConfirmed(paymentId, ExpenseRecordedFixture.GROUP, bob,
				alice, Money.eur("100.00"), MutableClock.START), MutableClock.START);

		// then
		assertThat(ledger.entries()).filteredOn(e -> e.referenceId().equals(paymentId.value()))
			.extracting(LedgerEntry::userId, LedgerEntry::deltaAmount, LedgerEntry::type)
			.containsExactlyInAnyOrder(tuple(bob, Money.eur("100.00"), LedgerEntryType.PAYMENT_SENT),
					tuple(alice, Money.eur("-100.00"), LedgerEntryType.PAYMENT_RECEIVED));
		assertThat(balanceOf(bob)).isEqualTo(Money.eur("-125.00"));
		assertThat(balanceOf(alice)).isEqualTo(Money.eur("575.00"));
		assertThat(sumOfBalances()).isEqualTo(Money.eur("0.00"));
	}

	@Test
	void aWaiverBooksDebtWaivedOnBothSides() {
		// given
		ledger.appendExpenseEntry(new ExpenseRecordedFixture().build(), MutableClock.START);
		var paymentId = new SettlementPaymentId(UUID.randomUUID());

		// when alice forgives 25.00 of bob's debt
		ledger.appendDebtWaiver(new DebtWaived(paymentId, ExpenseRecordedFixture.GROUP, bob, alice, Money.eur("25.00"),
				MutableClock.START), MutableClock.START);

		// then
		assertThat(ledger.entries()).filteredOn(e -> e.referenceId().equals(paymentId.value()))
			.extracting(LedgerEntry::userId, LedgerEntry::deltaAmount, LedgerEntry::type)
			.containsExactlyInAnyOrder(tuple(bob, Money.eur("25.00"), LedgerEntryType.DEBT_WAIVED),
					tuple(alice, Money.eur("-25.00"), LedgerEntryType.DEBT_WAIVED));
		assertThat(sumOfBalances()).isEqualTo(Money.eur("0.00"));
	}

	private Money sumOfBalances() {
		return ledger.projectBalances()
			.stream()
			.map(MemberBalance::netBalance)
			.reduce(Money.zero(java.util.Currency.getInstance("EUR")), Money::plus);
	}

	@Test
	void theSimplifiedPlanOfTheMallorcaExampleSettlesEveryoneWithThreePayments() {
		// given
		ledger.appendExpenseEntry(new ExpenseRecordedFixture().build(), MutableClock.START);
		ledger.appendExpenseEntry(new ExpenseRecordedFixture().paidBy(bob).withTotalAmount("180.00").build(),
				MutableClock.START);

		// when
		SettlementPlan plan = ledger.projectSimplifiedPlan();

		// then
		assertThat(plan.simplifiedDebts()).hasSize(3);
		assertThat(plan.plannedAmount(carol, alice)).isEqualTo(Money.eur("270.00"));
		assertThat(plan.plannedAmount(david, alice)).isEqualTo(Money.eur("270.00"));
		assertThat(plan.plannedAmount(bob, alice)).isEqualTo(Money.eur("90.00"));
	}

	@Test
	void aCorrectionBooksOnlyTheDifferenceWithItsOwnReference() {
		// given the dinner of the Mallorca example: 180.00 by bob, four ways (45.00 each)
		ExpenseRecorded dinner = new ExpenseRecordedFixture().paidBy(bob).withTotalAmount("180.00").build();
		ledger.appendExpenseEntry(dinner, MutableClock.START);
		UUID revision = UUID.randomUUID();

		// when it is corrected to 200.00 (50.00 each; numbers checked by script, design
		// 2026-11-28-expense-correction)
		ledger.appendCorrectionReversal(corrected(dinner, revision, "200.00"), MutableClock.START);

		// then bob's paid +20.00 and four owed -5.00, all with the correction's id
		assertThat(ledger.entries()).filteredOn(e -> e.referenceId().equals(revision))
			.extracting(LedgerEntry::userId, LedgerEntry::type, LedgerEntry::deltaAmount)
			.containsExactlyInAnyOrder(tuple(bob, LedgerEntryType.EXPENSE_PAID, Money.eur("20.00")),
					tuple(alice, LedgerEntryType.EXPENSE_OWED, Money.eur("-5.00")),
					tuple(bob, LedgerEntryType.EXPENSE_OWED, Money.eur("-5.00")),
					tuple(carol, LedgerEntryType.EXPENSE_OWED, Money.eur("-5.00")),
					tuple(david, LedgerEntryType.EXPENSE_OWED, Money.eur("-5.00")));
		assertThat(balanceOf(bob)).isEqualTo(Money.eur("150.00"));
		assertThat(balanceOf(alice)).isEqualTo(Money.eur("-50.00"));
	}

	@Test
	void aCorrectionThatChangesNobodysAmountBooksNothing() {
		// given
		ExpenseRecorded dinner = new ExpenseRecordedFixture().paidBy(bob).withTotalAmount("180.00").build();
		ledger.appendExpenseEntry(dinner, MutableClock.START);

		// when only the title or the date changed
		ledger.appendCorrectionReversal(corrected(dinner, UUID.randomUUID(), "180.00"), MutableClock.START);

		// then
		assertThat(ledger.entries()).hasSize(5);
	}

	@Test
	void aRemovalTakesTheExpenseBackSoEveryBalanceIsZeroAgain() {
		// given
		ExpenseRecorded dinner = new ExpenseRecordedFixture().paidBy(bob).withTotalAmount("180.00").build();
		ledger.appendExpenseEntry(dinner, MutableClock.START);

		// when (R8)
		ledger.appendRemovalReversal(new ExpenseRemoved(dinner.expenseId(), dinner.groupId(), UUID.randomUUID(), bob,
				dinner.paidBy(), dinner.totalAmount(), dinner.shares(), MutableClock.START), MutableClock.START);

		// then
		assertThat(ledger.projectBalances()).extracting(MemberBalance::netBalance).containsOnly(Money.eur("0.00"));
	}

	/**
	 * The same expense, corrected to {@code total} split equally between the same four.
	 */
	private ExpenseCorrected corrected(ExpenseRecorded expense, UUID revision, String total) {
		ExpenseRecorded after = new ExpenseRecordedFixture().paidBy(expense.paidBy()).withTotalAmount(total).build();
		return new ExpenseCorrected(expense.expenseId(), expense.groupId(), revision, bob, expense.paidBy(),
				expense.totalAmount(), expense.shares(), after.paidBy(), after.totalAmount(), after.shares(),
				MutableClock.START);
	}

	@Test
	void theBalancesOfAGroupAlwaysAddUpToZeroWhateverTheExpenses() {
		// given 200 random expenses between a random number of people, with amounts that
		// divide badly
		Random random = new Random(42);
		List<UserId> people = IntStream.range(0, 7).mapToObj(i -> new UserId(UUID.randomUUID())).toList();
		for (int i = 0; i < 200; i++) {
			int count = 1 + random.nextInt(people.size());
			UserId[] participants = people.subList(0, count).toArray(new UserId[0]);
			String amount = (1 + random.nextInt(50_000)) / 100 + "." + String.format("%02d", random.nextInt(100));
			ledger.appendExpenseEntry(new ExpenseRecordedFixture().paidBy(people.get(random.nextInt(people.size())))
				.withTotalAmount(amount.equals("0.00") ? "0.01" : amount)
				.splitBetween(participants)
				.build(), MutableClock.START);
		}

		// then
		Money sum = ledger.projectBalances()
			.stream()
			.map(MemberBalance::netBalance)
			.reduce(Money.zero(java.util.Currency.getInstance("EUR")), Money::plus);
		assertThat(sum).isEqualTo(Money.eur("0.00"));
	}

}
