package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.ExpenseIntegrationTest;
import de.hsh.favs.sharewise.expense.StubCurrentUser;
import de.hsh.favs.sharewise.expense.StubGroupMembership;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ExpenseIntegrationTest
class ExpenseControllerTest {

	@Autowired MockMvc mvc;
	@Autowired StubGroupMembership groups;
	@Autowired StubCurrentUser currentUser;

	UUID group;
	String alice = StubCurrentUser.ALICE.id().value().toString();
	String bob = UUID.randomUUID().toString();
	String carol = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() {
		groups.reset();
		currentUser.reset();
		group = UUID.randomUUID();
		groups.group(new GroupId(group), GroupStatus.OPEN, member(alice), member(bob), member(carol));
	}

	static MemberId member(String id) {
		return new MemberId(UUID.fromString(id));
	}

	String body(String title, long cents, String date, String payer, String split, String... participants) {
		return """
				{"title":"%s","amountCents":%d,"date":"%s","payerId":"%s","participantIds":["%s"],"split":"%s"}"""
				.formatted(title, cents, date, payer, String.join("\",\"", participants), split);
	}

	ResultActions postExpense(Object groupId, String body) throws Exception {
		return mvc.perform(post("/groups/{id}/expenses", groupId).contentType(APPLICATION_JSON).content(body));
	}

	@Test
	void postReturns201AndMatchesContractShape() throws Exception {
		postExpense(group, body("Hotel", 1_000, "2027-06-01", alice, "EQUAL", alice, bob, carol))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Hotel"))
				.andExpect(jsonPath("$.amountCents").value(1_000))
				.andExpect(jsonPath("$.date").value("2027-06-01"))
				.andExpect(jsonPath("$.payerId").value(alice))
				.andExpect(jsonPath("$.shares.length()").value(3))
				.andExpect(jsonPath("$.shares[0].memberId").value(alice))
				.andExpect(jsonPath("$.shares[0].shareCents").value(334))
				.andExpect(jsonPath("$.shares[1].memberId").value(bob))
				.andExpect(jsonPath("$.shares[1].shareCents").value(333))
				.andExpect(jsonPath("$.shares[2].shareCents").value(333));
	}

	@Test
	void splitPercentageIs422SplitNotSupported() throws Exception {
		postExpense(group, body("Hotel", 1_000, "2027-06-01", alice, "PERCENTAGE", alice))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("SPLIT_NOT_SUPPORTED"));
	}

	@Test
	void unknownSplitValueIs400() throws Exception {
		postExpense(group, body("Hotel", 1_000, "2027-06-01", alice, "WEIRD", alice))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void blankTitleIs400() throws Exception {
		postExpense(group, body("", 1_000, "2027-06-01", alice, "EQUAL", alice))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void zeroAmountIs422() throws Exception {
		postExpense(group, body("Hotel", 0, "2027-06-01", alice, "EQUAL", alice))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
	}

	@Test
	void listIsNewestFirstWithShares() throws Exception {
		postExpense(group, body("Old", 100, "2027-06-01", alice, "EQUAL", alice, bob)).andExpect(status().isCreated());
		postExpense(group, body("Newest", 200, "2027-06-03", bob, "EQUAL", alice, bob)).andExpect(status().isCreated());
		postExpense(group, body("SameDayLater", 300, "2027-06-01", alice, "EQUAL", alice)).andExpect(status().isCreated());

		mvc.perform(get("/groups/{id}/expenses", group))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].title").value("Newest"))
				.andExpect(jsonPath("$[0].shares.length()").value(2))
				.andExpect(jsonPath("$[0].shares[0].shareCents").value(100))
				.andExpect(jsonPath("$[1].title").value("SameDayLater"))
				.andExpect(jsonPath("$[2].title").value("Old"));
	}

	@Test
	void invalidUuidIs400() throws Exception {
		postExpense("not-a-uuid", body("Hotel", 100, "2027-06-01", alice, "EQUAL", alice))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		postExpense(group, body("Hotel", 100, "2027-06-01", "nope", "EQUAL", alice))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		mvc.perform(get("/groups/{id}/expenses", "not-a-uuid"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void callerNotMemberIs403() throws Exception {
		groups.group(new GroupId(group), GroupStatus.OPEN, member(bob), member(carol));

		postExpense(group, body("Hotel", 100, "2027-06-01", bob, "EQUAL", bob))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
		mvc.perform(get("/groups/{id}/expenses", group))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void unknownGroupIs404() throws Exception {
		mvc.perform(get("/groups/{id}/expenses", UUID.randomUUID()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

}
