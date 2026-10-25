package de.hsh.favs.sharewise.settlement.domain;

import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.ALICE;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.BOB;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.CAROL;
import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded.Share;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupLedgerTest {

	private final GroupLedger ledger = new GroupLedger(new GroupId(UUID.randomUUID()));

	@Test
	void payerIsCreditedTotalAndParticipantsDebitedShares() {
		// given
		var expense = new ExpenseRecordedFixture().build();

		// when
		ledger.apply(expense);

		// then
		assertThat(ledger.balances()).containsOnly(Map.entry(ALICE, 9000L), Map.entry(BOB, -4500L),
				Map.entry(CAROL, -4500L));
	}

	@Test
	void payerWhoIsAlsoParticipantNetsCorrectly() {
		// given
		var expense = new ExpenseRecordedFixture()
				.withShares(new Share(ALICE, 3000), new Share(BOB, 3000), new Share(CAROL, 3000)).build();

		// when
		ledger.apply(expense);

		// then
		assertThat(ledger.balances()).containsOnly(Map.entry(ALICE, 6000L), Map.entry(BOB, -3000L),
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
				ledger.apply(new ExpenseRecordedFixture().withPayer(members.get(random.nextInt(3))).withTotal(total)
						.withShares(shares.toArray(Share[]::new)).build());
			}
		}

		// then
		assertThat(ledger.balances().values().stream().mapToLong(Long::longValue).sum()).isZero();
	}

	@Test
	void applyingTheSameExpenseTwiceCountsItOnce() {
		// given
		var expense = new ExpenseRecordedFixture().build();

		// when
		var first = ledger.apply(expense);
		var second = ledger.apply(expense);

		// then
		assertThat(first).isTrue();
		assertThat(second).isFalse();
		assertThat(ledger.balances()).containsOnly(Map.entry(ALICE, 9000L), Map.entry(BOB, -4500L),
				Map.entry(CAROL, -4500L));
	}

}
