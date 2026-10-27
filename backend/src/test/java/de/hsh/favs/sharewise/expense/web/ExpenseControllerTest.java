package de.hsh.favs.sharewise.expense.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** API contract of the expense endpoints with real accounts and real groups. */
@ModuleIntegrationTest
class ExpenseControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	ApplicationEvents events;

	@Autowired
	MutableClock clock;

	AccountFixture alice;

	AccountFixture bob;

	AccountFixture carol;

	AccountFixture outsider;

	UUID group;

	ExpenseRequests expenses;

	@BeforeEach
	void setUp() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		carol = AccountFixture.signUp(mvc, json, "Carol");
		outsider = AccountFixture.signUp(mvc, json, "Outsider");
		group = new GroupRequests(mvc, json).groupWith(alice, "Mallorca 2027", bob, carol);
		expenses = new ExpenseRequests(mvc, json).inGroup(group);
	}

	@Test
	void postRecordsTheExpenseAnswers201WithTheContractShapeAndPublishesTheEvent() throws Exception {
		// when
		expenses.withTitle("Hotel")
			.withTotalAmount("100.00")
			.paidBy(alice)
			.splitBetween(alice, bob, carol)
			.record(bob)
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
			.andExpect(jsonPath("$.id").isString())
			.andExpect(jsonPath("$.title").value("Hotel"))
			.andExpect(jsonPath("$.totalAmount").value("100.00"))
			.andExpect(jsonPath("$.date").value("2027-06-01"))
			.andExpect(jsonPath("$.paidBy").value(alice.idString()))
			.andExpect(jsonPath("$.recordedBy").value(bob.idString()))
			.andExpect(jsonPath("$.splitStrategy").value("EQUAL"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.shares[0].participantId").value(alice.idString()))
			.andExpect(jsonPath("$.shares[0].owedShare").value("33.34"))
			.andExpect(jsonPath("$.shares[1].owedShare").value("33.33"))
			.andExpect(jsonPath("$.shares[2].owedShare").value("33.33"));

		// then
		assertThat(events.stream(ExpenseRecorded.class).filter(e -> e.groupId().value().equals(group))).singleElement()
			.satisfies(e -> assertThat(e.paidBy()).isEqualTo(alice.userId()));
	}

	@Test
	void getListsTheExpensesNewestFirstByDateThenByCreation() throws Exception {
		// given three expenses; the clock moves so creation times differ
		expenses.withTitle("Older")
			.onDate("2027-06-01")
			.paidBy(alice)
			.splitBetween(alice, bob)
			.record(alice)
			.andExpect(status().isCreated());
		clock.advance(Duration.ofSeconds(1));
		expenses.withTitle("Newer")
			.onDate("2027-06-03")
			.paidBy(bob)
			.splitBetween(alice, bob)
			.record(alice)
			.andExpect(status().isCreated());
		clock.advance(Duration.ofSeconds(1));
		expenses.withTitle("Same day, later")
			.onDate("2027-06-01")
			.paidBy(bob)
			.splitBetween(alice, bob)
			.record(alice)
			.andExpect(status().isCreated());

		// when / then
		expenses.list(carol)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("Newer"))
			.andExpect(jsonPath("$[1].title").value("Same day, later"))
			.andExpect(jsonPath("$[2].title").value("Older"))
			.andExpect(jsonPath("$[0].shares.length()").value(2));
	}

	@Test
	void aCallerWhoIsNotInTheGroupGets403OnBothEndpoints() throws Exception {
		expenses.paidBy(outsider)
			.splitBetween(outsider)
			.record(outsider)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
		expenses.list(outsider).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void anUnknownGroupIs404() throws Exception {
		new ExpenseRequests(mvc, json).paidBy(alice)
			.splitBetween(alice)
			.record(alice)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void aPayerOrParticipantOutsideTheGroupIs422WithTheMatchingCode() throws Exception {
		expenses.paidBy(outsider)
			.splitBetween(alice)
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("PAYER_NOT_IN_GROUP"));
		expenses.paidBy(alice)
			.splitBetween(alice, outsider)
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_IN_GROUP"));
	}

	@Test
	void noParticipantsAndAnAmountOfZeroAre422() throws Exception {
		expenses.paidBy(alice)
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("NO_PARTICIPANTS"));
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withTotalAmount("0.00")
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "EXACT", "PERCENTAGE" })
	void aSplitStrategyOtherThanEqualIs422(String strategy) throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withSplitStrategy(strategy)
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("SPLIT_NOT_SUPPORTED"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1,5", "1.005", "abc", "" })
	void anAmountThatIsNotAPlainDecimalWithTwoDecimalsIs400(String amount) throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withTotalAmount(amount)
			.record(alice)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void aBlankOrTooLongTitleAMalformedIdOrAnUnknownStrategyIs400() throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withTitle("  ")
			.record(alice)
			.andExpect(status().isBadRequest());
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withTitle("x".repeat(201))
			.record(alice)
			.andExpect(status().isBadRequest());
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withSplitStrategy("HALF")
			.record(alice)
			.andExpect(status().isBadRequest());
		mvc.perform(get("/groups/not-a-uuid/expenses").header("Authorization", alice.bearer()))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/groups/" + group + "/expenses").header("Authorization", alice.bearer())
			.contentType(APPLICATION_JSON)
			.content("{not json")).andExpect(status().isBadRequest());
	}

	@Test
	void bothEndpointsNeedASignedInUser() throws Exception {
		mvc.perform(get("/groups/" + group + "/expenses")).andExpect(status().isUnauthorized());
		mvc.perform(post("/groups/" + group + "/expenses").contentType(APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
