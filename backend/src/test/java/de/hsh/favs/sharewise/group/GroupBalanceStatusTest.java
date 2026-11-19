package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
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
 * {@code Membership.hasZeroBalance} follows the ledger across the module boundary (D12):
 * the ledger publishes {@code MemberBalanceStatusChanged} in {@code shared.events}, the
 * group reacts asynchronously after the commit, so every assertion waits for it. Real
 * accounts and a real group; the flag is read through the repository because no public
 * API exposes it yet.
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
