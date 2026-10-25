package de.hsh.favs.sharewise.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded.Share;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, SettlementFakes.class })
class BalanceListenerTest {

	@Autowired
	BalanceStore store;

	@Autowired
	ApplyExpenseToBalances applyExpense;

	private GroupId group;
	private MemberId alice;
	private MemberId bob;
	private MemberId carol;

	// the Spring test instance may be shared between tests, so every test gets its own group
	@BeforeEach
	void freshGroup() {
		group = new GroupId(UUID.randomUUID());
		alice = new MemberId(UUID.randomUUID());
		bob = new MemberId(UUID.randomUUID());
		carol = new MemberId(UUID.randomUUID());
	}

	@Test
	void payerIsCreditedTotalAndParticipantsDebitedShares(Scenario scenario) {
		var event = new ExpenseRecorded(UUID.randomUUID(), group, alice, 9000,
				List.of(new Share(bob, 4500), new Share(carol, 4500)));

		scenario.publish(event).andWaitForStateChange(() -> store.balancesOf(group), balances -> !balances.isEmpty())
				.andVerify(balances -> assertThat(balances).containsOnly(Map.entry(alice, 9000L),
						Map.entry(bob, -4500L), Map.entry(carol, -4500L)));
	}

	@Test
	void payerWhoIsAlsoParticipantNetsCorrectly(Scenario scenario) {
		var event = new ExpenseRecorded(UUID.randomUUID(), group, alice, 9000,
				List.of(new Share(alice, 3000), new Share(bob, 3000), new Share(carol, 3000)));

		scenario.publish(event).andWaitForStateChange(() -> store.balancesOf(group), balances -> !balances.isEmpty())
				.andVerify(balances -> assertThat(balances).containsOnly(Map.entry(alice, 6000L),
						Map.entry(bob, -3000L), Map.entry(carol, -3000L)));
	}

	@Test
	void balancesSumToZeroAfterManyExpenses() {
		var members = List.of(alice, bob, carol);
		var random = new Random(42);
		for (int i = 0; i < 200; i++) {
			var payer = members.get(random.nextInt(members.size()));
			var shares = new ArrayList<Share>();
			long total = 0;
			for (var member : members) {
				if (random.nextBoolean()) {
					long cents = 1 + random.nextInt(10_000);
					shares.add(new Share(member, cents));
					total += cents;
				}
			}
			if (shares.isEmpty()) {
				continue;
			}
			applyExpense.apply(new ExpenseRecorded(UUID.randomUUID(), group, payer, total, shares));
		}

		assertThat(store.balancesOf(group).values().stream().mapToLong(Long::longValue).sum()).isZero();
	}

	@Test
	void reprocessingSameExpenseIdIsIdempotent() {
		var event = new ExpenseRecorded(UUID.randomUUID(), group, alice, 9000,
				List.of(new Share(bob, 4500), new Share(carol, 4500)));

		applyExpense.apply(event);
		applyExpense.apply(event);

		assertThat(store.balancesOf(group)).containsOnly(Map.entry(alice, 9000L), Map.entry(bob, -4500L),
				Map.entry(carol, -4500L));
	}

}
