package de.hsh.favs.sharewise.settlement.web;

import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.ALICE;
import static de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture.BOB;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded.Share;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.settlement.ModuleIntegrationTest;
import de.hsh.favs.sharewise.settlement.application.GroupLedgerService;
import de.hsh.favs.sharewise.settlement.ExpenseRecordedFixture;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The HTTP contract of {@code GET /groups/{groupId}/balances}. */
@ModuleIntegrationTest
class BalanceControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	GroupMembership membership;

	@Autowired
	UserProfiles users;

	@Autowired
	CurrentUser currentUser;

	@Autowired
	GroupLedgerService ledgerService;

	private final UserId alice = new UserId(ALICE.value());

	private final UserId bob = new UserId(BOB.value());

	private GroupId group;

	@BeforeEach
	void aliceAsksForHerGroup() {
		group = new GroupId(UUID.randomUUID());
		when(currentUser.get()).thenReturn(new UserProfile(alice, "Alice", true));
		when(membership.members(group)).thenReturn(List.of(ALICE, BOB));
		when(users.find(alice)).thenReturn(Optional.of(new UserProfile(alice, "Alice", true)));
		when(users.find(bob)).thenReturn(Optional.of(new UserProfile(bob, "Bob", true)));
	}

	@Test
	void returnsEveryMemberAsContractShapedJson() throws Exception {
		// given
		ledgerService.apply(
				new ExpenseRecordedFixture().withGroup(group).withTotal(9000).withShares(new Share(BOB, 9000)).build());

		// when / then
		mvc.perform(get("/groups/{id}/balances", group.value().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].memberId").value(ALICE.value().toString()))
			.andExpect(jsonPath("$[0].displayName").value("Alice"))
			.andExpect(jsonPath("$[0].balanceCents").value(9000))
			.andExpect(jsonPath("$[1].memberId").value(BOB.value().toString()))
			.andExpect(jsonPath("$[1].balanceCents").value(-9000));
	}

	@Test
	void nonMemberIs403() throws Exception {
		// given
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(membership).requireMember(any(), any());

		// when / then
		mvc.perform(get("/groups/{id}/balances", group.value().toString()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void unknownGroupIs404() throws Exception {
		// given
		doThrow(new DomainException(ErrorCode.GROUP_NOT_FOUND, "no")).when(membership).requireMember(any(), any());

		// when / then
		mvc.perform(get("/groups/{id}/balances", group.value().toString()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void malformedGroupIdIs400() throws Exception {
		mvc.perform(get("/groups/{id}/balances", "not-a-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

}
