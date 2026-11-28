package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.domain.Membership;
import de.hsh.favs.sharewise.group.persistence.GroupRepository;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import de.hsh.favs.sharewise.settlement.application.RecordSettlementPaymentCommand;
import de.hsh.favs.sharewise.settlement.application.SettlementPaymentService;
import de.hsh.favs.sharewise.settlement.domain.SettlementPayment;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code Membership.hasZeroBalance} follows the ledger across the module boundary (D12),
 * and a closed group archives itself on {@code AllBalancesSettled} (L5): the ledger
 * publishes both events in {@code shared.events}, the group reacts asynchronously after
 * the commit, so every assertion waits for it. Real accounts and a real group; the flag
 * is read through the repository because no public API exposes it yet.
 */
@ModuleIntegrationTest
class GroupBalanceStatusTest {

	@Autowired
	GroupLedgerService ledgerService;

	@Autowired
	SettlementPaymentService payments;

	@Autowired
	GroupRepository groups;

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	AccountFixture alice;

	AccountFixture bob;

	AccountFixture carol;

	GroupId trip;

	@BeforeEach
	void setUp() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		carol = AccountFixture.signUp(mvc, json, "Carol");
		trip = new GroupId(new GroupRequests(mvc, json).groupWith(alice, "Trip " + UUID.randomUUID(), bob, carol));
	}

	@Test
	void anExpenseMakesTheFlagFalse() {
		// when Alice pays 20.00 for herself and Bob
		bookExpenseByAliceForAliceAndBob();

		// then Alice and Bob are non-zero; Carol, who is not in the expense, stays zero
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(hasZeroBalance(alice)).isFalse();
			assertThat(hasZeroBalance(bob)).isFalse();
		});
		assertThat(hasZeroBalance(carol)).isTrue();
	}

	@Test
	void confirmingTheLastPaymentSetsHasZeroBalanceOnTheGroupMembers() {
		// given Bob owes Alice 10.00 and the group knows it
		bookExpenseByAliceForAliceAndBob();
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(hasZeroBalance(alice)).isFalse();
			assertThat(hasZeroBalance(bob)).isFalse();
		});

		// when Bob pays the 10.00 and Alice confirms
		SettlementPayment payment = payments
			.record(new RecordSettlementPaymentCommand(trip, bob.userId(), alice.userId(), Money.eur("10.00"), null));
		payments.confirm(alice.userId(), payment.paymentId());

		// then both are back at zero
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(hasZeroBalance(alice)).isTrue();
			assertThat(hasZeroBalance(bob)).isTrue();
		});
		assertThat(hasZeroBalance(carol)).isTrue();
	}

	@Test
	void theLastConfirmedPaymentOfAClosedGroupArchivesItAutomatically() throws Exception {
		// given Bob owes Alice 10.00, and Alice closed the group for settlement (L4:
		// balances need not be 0)
		bookExpenseByAliceForAliceAndBob();
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(hasZeroBalance(bob)).isFalse());
		new GroupRequests(mvc, json).close(trip.value(), alice);

		// when Bob pays and Alice confirms, so every balance is 0 (AllBalancesSettled)
		SettlementPayment payment = payments
			.record(new RecordSettlementPaymentCommand(trip, bob.userId(), alice.userId(), Money.eur("10.00"), null));
		payments.confirm(alice.userId(), payment.paymentId());

		// then the group archives itself (L5)
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(status()).isEqualTo(GroupStatus.ARCHIVED));
	}

	@Test
	void settlingEveryBalanceOfAnOpenGroupLeavesItOpen() {
		// given Bob owes Alice 10.00 in an open group
		bookExpenseByAliceForAliceAndBob();
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(hasZeroBalance(bob)).isFalse());

		// when Bob pays and Alice confirms
		SettlementPayment payment = payments
			.record(new RecordSettlementPaymentCommand(trip, bob.userId(), alice.userId(), Money.eur("10.00"), null));
		payments.confirm(alice.userId(), payment.paymentId());

		// then the balances are 0, but the group stays open (design
		// 2026-11-28-group-lifecycle G2)
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(hasZeroBalance(bob)).isTrue());
		assertThat(status()).isEqualTo(GroupStatus.OPEN);
	}

	private GroupStatus status() {
		return new TransactionTemplate(transactions)
			.execute(status -> groups.findById(trip.value()).orElseThrow().status());
	}

	private void bookExpenseByAliceForAliceAndBob() {
		ledgerService.bookExpense(new ExpenseRecordedFixture().inGroup(trip)
			.paidBy(alice.userId())
			.withTotalAmount("20.00")
			.splitBetween(alice.userId(), bob.userId())
			.build());
	}

	private boolean hasZeroBalance(AccountFixture user) {
		return new TransactionTemplate(transactions).execute(status -> groups.findById(trip.value())
			.orElseThrow()
			.memberships()
			.stream()
			.filter(m -> m.userId().equals(user.userId()))
			.findFirst()
			.map(Membership::hasZeroBalance)
			.orElseThrow());
	}

}
