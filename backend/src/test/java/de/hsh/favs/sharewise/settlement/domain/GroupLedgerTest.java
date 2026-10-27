package de.hsh.favs.sharewise.settlement.domain;

import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.ALICE;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.BOB;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded.Share;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupLedgerTest {

	private static final Instant AT = Instant.parse("2026-10-25T10:00:00Z");

	private final GroupLedger ledger = new GroupLedger(new GroupId(UUID.randomUUID()));

	@Test
	void payerIsCreditedTotalAndParticipantsDebitedShares() {
		// given
		var expense = new ExpenseRecordedFixture().build();

		// when
		ledger.appendExpenseEntry(expense, AT);

		// then
		assertThat(ledger.projectBalances()).containsOnly(Map.entry(ALICE, 9000L), Map.entry(BOB, -4500L),
				Map.entry(CAROL, -4500L));
	}

	@Test
	void anExpenseBooksOneEntryForThePayerAndOneForEveryShare() {
		// given
		var expense = new ExpenseRecordedFixture().build();

		// when
		ledger.appendExpenseEntry(expense, AT);

		// then
		assertThat(ledger.entries()).extracting(LedgerEntry::referenceId).containsOnly(expense.expenseId());
		assertThat(ledger.entries()).extracting(LedgerEntry::occurredAt).containsOnly(AT);
		assertThat(ledger.entries()).extracting(LedgerEntry::type, LedgerEntry::member, LedgerEntry::deltaCents)
			.containsExactlyInAnyOrder(tuple(LedgerEntryType.EXPENSE_PAID, ALICE, 9000L),
					tuple(LedgerEntryType.EXPENSE_OWED, BOB, -4500L),
					tuple(LedgerEntryType.EXPENSE_OWED, CAROL, -4500L));
	}

	@Test
	void payerWhoIsAlsoParticipantNetsCorrectly() {
		// given
		var expense = new ExpenseRecordedFixture()
			.withShares(new Share(ALICE, 3000), new Share(BOB, 3000), new Share(CAROL, 3000))
			.build();

		// when
		ledger.appendExpenseEntry(expense, AT);

		// then
		assertThat(ledger.projectBalances()).containsOnly(Map.entry(ALICE, 6000L), Map.entry(BOB, -3000L),
				Map.entry(CAROL, -3000L));
	}

	@Test
	void balancesSumToZeroAfterManyExpenses() {
		// given
		var members = List.of(ALICE, BOB, CAROL);
		var random = new Random(42);

		// when
		for (int i = 0; i < 200; i++) {
			var shares = new ArrayList<Share>();
			long total = 0;
			for (MemberId member : members) {
				if (random.nextBoolean()) {
					long cents = 1 + random.nextInt(10_000);
					shares.add(new Share(member, cents));
					total += cents;
				}
			}
			if (!shares.isEmpty()) {
				ledger.appendExpenseEntry(new ExpenseRecordedFixture().withPayer(members.get(random.nextInt(3)))
					.withTotal(total)
					.withShares(shares.toArray(Share[]::new))
					.build(), AT);
			}
		}

		// then
		assertThat(ledger.projectBalances().values().stream().mapToLong(Long::longValue).sum()).isZero();
	}

}
