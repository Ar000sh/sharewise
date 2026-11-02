package de.hsh.favs.sharewise.settlement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.api.Balances;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * API contract of {@code POST /groups/{groupId}/debt-waivers}. Bob owes Alice 90.00.
 */
@ModuleIntegrationTest
class DebtWaiverControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	GroupLedgerService ledgers;

	@Autowired
	Balances balances;

	@Autowired
	ApplicationEvents events;

	SettlementRequests settlement;

	AccountFixture alice;

	AccountFixture bob;

	UUID group;

	@BeforeEach
	void bobOwesAlice90() throws Exception {
		settlement = new SettlementRequests(mvc, json);
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		group = new GroupRequests(mvc, json).groupWith(alice, "Trip", bob);
		SettlementRequests.bookDebt(ledgers, group, alice, bob, "90.00");
	}

	@Test
	void waiveByTheCreditorIs201WaivedAndBalancesFollow() throws Exception {
		// when
		settlement.waive(group, alice, bob, "90.00", "Bob gets it as a gift")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.groupId").value(group.toString()))
			.andExpect(jsonPath("$.fromDebtor").value(bob.idString()))
			.andExpect(jsonPath("$.fromDebtorName").value(bob.displayName()))
			.andExpect(jsonPath("$.toCreditor").value(alice.idString()))
			.andExpect(jsonPath("$.toCreditorName").value(alice.displayName()))
			.andExpect(jsonPath("$.amount").value("90.00"))
			.andExpect(jsonPath("$.status").value("WAIVED"))
			.andExpect(jsonPath("$.note").value("Bob gets it as a gift"))
			.andExpect(jsonPath("$.resolvedAt").isNotEmpty());

		// then
		assertThat(events.stream(DebtWaived.class).count()).isEqualTo(1);
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances.of(new GroupId(group))).allMatch(b -> b.netBalance().isZero()));
	}

	@Test
	void waiveByAMemberWhoIsNotOwedIs422() throws Exception {
		// bob is the debtor; alice owes bob nothing
		settlement.waive(group, bob, alice, "10.00", "reason")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_OPEN_DEBT"));
		settlement.waive(group, alice, bob, "90.01", "reason")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_OPEN_DEBT"));
		assertThat(events.stream(DebtWaived.class).count()).isZero();
	}

	@Test
	void aZeroAmountIs422AndABlankOrMissingReasonIs400() throws Exception {
		settlement.waive(group, alice, bob, "0.00", "reason")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
		settlement.waive(group, alice, bob, "10.00", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		settlement.waive(group, alice, bob, "10.00", " ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void amountWithThreeDecimalsOrExponentOrStringIs400() throws Exception {
		for (String amount : List.of("\"10.001\"", "\"1e1\"", "\"abc\"", "10", "1e1", "null")) {
			mvc.perform(post("/groups/{id}/debt-waivers", group).header("Authorization", alice.bearer())
				.contentType(APPLICATION_JSON)
				.content("{\"fromDebtor\":\"" + bob.idString() + "\",\"amount\":" + amount + ",\"note\":\"r\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		}
	}

	@Test
	void nonMemberIs403UnknownGroupIs404NoTokenIs401() throws Exception {
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");

		settlement.waive(group, outsider, bob, "1.00", "r")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
		settlement.waive(UUID.randomUUID(), alice, bob, "1.00", "r")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
		mvc.perform(post("/groups/{id}/debt-waivers", group).contentType(APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		settlement.waive(group, alice, outsider, "1.00", "r")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_IN_GROUP"));
	}

}
