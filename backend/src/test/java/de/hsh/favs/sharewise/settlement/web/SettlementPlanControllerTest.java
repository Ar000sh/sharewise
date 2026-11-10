package de.hsh.favs.sharewise.settlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.web.ExpenseRequests;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** API contract of {@code GET /groups/{groupId}/settlement-plan}. */
@ModuleIntegrationTest
class SettlementPlanControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	GroupLedgerService ledgers;

	@Test
	void planForMallorcaListsThreeEdgesToAlice() throws Exception {
		// given the Mallorca trip: Alice pays the hotel 900.00, Bob the dinner 180.00,
		// both split between the four
		var settlement = new SettlementRequests(mvc, json);
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		AccountFixture carol = AccountFixture.signUp(mvc, json, "Carol");
		AccountFixture david = AccountFixture.signUp(mvc, json, "David");
		UUID group = new GroupRequests(mvc, json).groupWith(alice, "Mallorca", bob, carol, david);
		new ExpenseRequests(mvc, json).inGroup(group)
			.withTitle("Hotel")
			.withTotalAmount("900.00")
			.splitBetween(alice, bob, carol, david)
			.record(alice)
			.andExpect(status().isCreated());
		new ExpenseRequests(mvc, json).inGroup(group)
			.withTitle("Dinner")
			.withTotalAmount("180.00")
			.paidBy(bob)
			.splitBetween(alice, bob, carol, david)
			.record(bob)
			.andExpect(status().isCreated());

		// when / then the final plan has arrived: three edges, all to Alice, nothing
		// pending; the hotel alone already gives three edges of 225.00, so the amounts
		// are part of what is awaited
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			JsonNode plan = json.readTree(settlement.body(settlement.plan(group, bob).andExpect(status().isOk())));
			assertThat(plan.size()).isEqualTo(3);
			for (JsonNode edge : plan) {
				assertThat(edge.get("toCreditor").asString()).isEqualTo(alice.idString());
				assertThat(edge.get("toCreditorName").asString()).isEqualTo(alice.displayName());
				assertThat(edge.get("pendingAmount").asString()).isEqualTo("0.00");
				assertThat(edge.size()).isEqualTo(7);
				String expected = edge.get("fromDebtor").asString().equals(bob.idString()) ? "90.00" : "270.00";
				assertThat(edge.get("amount").asString()).isEqualTo(expected);
				assertThat(edge.get("payableAmount").asString()).isEqualTo(expected);
			}
			assertThat(plan.findValuesAsString("fromDebtor")).containsExactlyInAnyOrder(bob.idString(),
					carol.idString(), david.idString());
			assertThat(plan.findValuesAsString("fromDebtor"))
				.isSortedAccordingTo((x, y) -> UUID.fromString(x).compareTo(UUID.fromString(y)));
		});
	}

	@Test
	void pendingAmountShowsWhatIsReservedOnThePair() throws Exception {
		// given bob owes alice 90.00 and has reported 40.00
		var settlement = new SettlementRequests(mvc, json);
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		UUID group = new GroupRequests(mvc, json).groupWith(alice, "Trip", bob);
		SettlementRequests.bookDebt(ledgers, group, alice, bob, "90.00");
		settlement.recordedPayment(group, bob, alice, "40.00");

		// when / then
		settlement.plan(group, alice)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].fromDebtor").value(bob.idString()))
			.andExpect(jsonPath("$[0].fromDebtorName").value(bob.displayName()))
			.andExpect(jsonPath("$[0].toCreditor").value(alice.idString()))
			.andExpect(jsonPath("$[0].amount").value("90.00"))
			.andExpect(jsonPath("$[0].pendingAmount").value("40.00"))
			.andExpect(jsonPath("$[0].payableAmount").value("50.00"));
	}

	@Test
	void aGroupWhereEveryoneIsAtZeroHasAnEmptyPlanAndRefusesRecord() throws Exception {
		var settlement = new SettlementRequests(mvc, json);
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		UUID group = new GroupRequests(mvc, json).groupWith(alice, "Quiet", bob);

		settlement.plan(group, alice).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		settlement.record(group, bob, alice, "1.00", null)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_OPEN_DEBT"));
	}

	@Test
	void nonMemberIs403UnknownGroupIs404AndNoTokenIs401() throws Exception {
		var settlement = new SettlementRequests(mvc, json);
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");
		UUID group = new GroupRequests(mvc, json).createGroup(alice, "Private");

		settlement.plan(group, outsider)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
		settlement.plan(UUID.randomUUID(), alice)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
		mvc.perform(get("/groups/{id}/settlement-plan", group)).andExpect(status().isUnauthorized());
	}

}
