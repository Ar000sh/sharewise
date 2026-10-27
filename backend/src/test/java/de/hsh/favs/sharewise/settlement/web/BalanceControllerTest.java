package de.hsh.favs.sharewise.settlement.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * API contract of {@code GET /groups/{groupId}/balances} with real accounts and groups.
 */
@ModuleIntegrationTest
class BalanceControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	GroupLedgerService ledgers;

	@Test
	void listsEveryMemberWithNameAndDecimalBalanceSortedByNameAndSummingToZero() throws Exception {
		// given bob, alice and carol in a group; bob pays 90.00 for himself and carol, so
		// alice has no expense
		AccountFixture bob = AccountFixture.signUp(mvc, json, "Bob");
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture carol = AccountFixture.signUp(mvc, json, "Carol");
		UUID group = new GroupRequests(mvc, json).groupWith(bob, "Trip", alice, carol);
		ledgers.bookExpense(new ExpenseRecordedFixture().inGroup(new GroupId(group))
			.paidBy(bob.userId())
			.withTotalAmount("90.00")
			.splitBetween(bob.userId(), carol.userId())
			.build());

		// when / then
		mvc.perform(get("/groups/{id}/balances", group).header("Authorization", alice.bearer()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("application/json"))
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].userId").value(alice.idString()))
			.andExpect(jsonPath("$[0].displayName").value("Alice"))
			.andExpect(jsonPath("$[0].netBalance").value("0.00"))
			.andExpect(jsonPath("$[1].displayName").value("Bob"))
			.andExpect(jsonPath("$[1].netBalance").value("45.00"))
			.andExpect(jsonPath("$[2].displayName").value("Carol"))
			.andExpect(jsonPath("$[2].netBalance").value("-45.00"));
	}

	@Test
	void aCallerWhoIsNotInTheGroupGets403() throws Exception {
		// given
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");
		AccountFixture outsider = AccountFixture.signUp(mvc, json, "Outsider");
		UUID group = new GroupRequests(mvc, json).createGroup(alice, "Private");

		// when / then
		mvc.perform(get("/groups/{id}/balances", group).header("Authorization", outsider.bearer()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void anUnknownGroupIs404AndAMalformedIdIs400AndNoCredentialsIs401() throws Exception {
		AccountFixture alice = AccountFixture.signUp(mvc, json, "Alice");

		mvc.perform(get("/groups/{id}/balances", UUID.randomUUID()).header("Authorization", alice.bearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
		mvc.perform(get("/groups/not-a-uuid/balances").header("Authorization", alice.bearer()))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/groups/{id}/balances", UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

}
