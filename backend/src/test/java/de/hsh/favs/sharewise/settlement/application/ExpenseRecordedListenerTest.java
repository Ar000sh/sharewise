package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The event-driven path: an announced expense reaches the ledger after the publisher's
 * transaction commits.
 */
@ModuleIntegrationTest
class ExpenseRecordedListenerTest {

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	Balances balances;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	private void publishInOwnTransaction(ExpenseRecorded event) {
		new TransactionTemplate(transactions).executeWithoutResult(status -> publisher.publishEvent(event));
	}

	@Test
	void anExpenseRecordedEventIsBookedAndAnEventDeliveredTwiceCountsOnce() throws Exception {
		// given a group of two
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		GroupId group = new GroupId(new GroupRequests(mvc, json).groupWith(alice, "Trip", bob));
		ExpenseRecorded event = new ExpenseRecordedFixture().inGroup(group)
			.paidBy(alice.userId())
			.withTotalAmount("80.00")
			.splitBetween(alice.userId(), bob.userId())
			.build();

		// when the same event is announced twice
		publishInOwnTransaction(event);
		publishInOwnTransaction(event);

		// then the ledger shows it once
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances.of(group)).containsExactlyInAnyOrder(
					new MemberBalance(alice.userId(), Money.eur("40.00")),
					new MemberBalance(bob.userId(), Money.eur("-40.00"))));
		Thread.sleep(300); // a second booking, if it happened, would have been applied by
							// now
		assertThat(balances.of(group)).extracting(MemberBalance::netBalance)
			.containsExactlyInAnyOrder(Money.eur("40.00"), Money.eur("-40.00"));
	}

}
