package de.hsh.favs.sharewise.settlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API contract of {@code GET /me/settlement-payments}: the inbox of PENDING payments the
 * caller (the creditor) still has to answer.
 */
@ModuleIntegrationTest
class MySettlementPaymentsControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	GroupLedgerService ledgers;

	@Autowired
	MutableClock clock;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void recordReturns201PendingAndTheCreditorSeesItInTheInbox() throws Exception {
		// given bob owes alice 50.00 and reports a payment
		var settlement = new SettlementRequests(mvc, json);
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		UUID group = new GroupRequests(mvc, json).groupWith(alice, "Trip", bob);
		SettlementRequests.bookDebt(ledgers, group, alice, bob, "50.00");
		settlement.record(group, bob, alice, "50.00", "Bank transfer")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"));

		// when / then the creditor sees it, the debtor does not
		settlement.awaitingMyAnswer(alice)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].groupId").value(group.toString()))
			.andExpect(jsonPath("$[0].fromDebtorName").value(bob.displayName()))
			.andExpect(jsonPath("$[0].toCreditorName").value(alice.displayName()))
			.andExpect(jsonPath("$[0].amount").value("50.00"))
			.andExpect(jsonPath("$[0].status").value("PENDING"));
		settlement.awaitingMyAnswer(bob).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void answeredPaymentsLeaveTheInboxAndTheNewestComesFirst() throws Exception {
		// given two groups with a debt each
		var settlement = new SettlementRequests(mvc, json);
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		UUID first = new GroupRequests(mvc, json).groupWith(alice, "One", bob);
		UUID second = new GroupRequests(mvc, json).groupWith(alice, "Two", bob);
		SettlementRequests.bookDebt(ledgers, first, alice, bob, "30.00");
		SettlementRequests.bookDebt(ledgers, second, alice, bob, "40.00");
		UUID older = settlement.recordedPayment(first, bob, alice, "30.00");
		clock.advance(Duration.ofMinutes(1));
		UUID newer = settlement.recordedPayment(second, bob, alice, "40.00");

		// when / then
		JsonNode inbox = json.readTree(settlement.body(settlement.awaitingMyAnswer(alice).andExpect(status().isOk())));
		assertThat(inbox.size()).isEqualTo(2);
		assertThat(inbox.get(0).get("id").asString()).isEqualTo(newer.toString());
		assertThat(inbox.get(1).get("id").asString()).isEqualTo(older.toString());
		settlement.confirm(newer, alice).andExpect(status().isOk());
		settlement.awaitingMyAnswer(alice)
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(older.toString()));
	}

	@Test
	void noTokenIs401() throws Exception {
		mvc.perform(get("/me/settlement-payments")).andExpect(status().isUnauthorized());
	}

}
