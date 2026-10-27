package de.hsh.favs.sharewise.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
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
