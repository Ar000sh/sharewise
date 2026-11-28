package de.hsh.favs.sharewise.expense.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.expense.api.ExpenseCorrected;
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
	void aRecorderWhoIsNotAParticipantIs422AndNothingIsRecorded() throws Exception {
		expenses.paidBy(alice)
			.splitBetween(bob)
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("RECORDER_NOT_PARTICIPANT"));
		expenses.list(alice).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void noParticipantsIs400AndAnAmountOfZeroIs422() throws Exception {
		mvc.perform(post("/groups/" + group + "/expenses").header("Authorization", alice.bearer())
			.contentType(APPLICATION_JSON)
			.content("{\"title\":\"Hotel\",\"totalAmount\":\"10.00\",\"date\":\"2027-06-01\",\"paidBy\":\""
					+ alice.idString() + "\",\"participants\":[],\"splitStrategy\":\"EQUAL\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withTotalAmount("0.00")
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
	}

	@Test
	void anExactSplitIs201WithTheTypedShares() throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withSplitStrategy("EXACT")
			.withValues("6.00", "4.00")
			.record(alice)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.splitStrategy").value("EXACT"))
			.andExpect(jsonPath("$.shares[0].owedShare").value("6.00"))
			.andExpect(jsonPath("$.shares[1].owedShare").value("4.00"));
	}

	@Test
	void aPercentageSplitIs201AndTheLeftoverCentFollowsTheLargestFraction() throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob, carol)
			.withSplitStrategy("PERCENTAGE")
			.withValues("33.33", "33.33", "33.34")
			.record(alice)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.splitStrategy").value("PERCENTAGE"))
			.andExpect(jsonPath("$.shares[0].owedShare").value("3.33"))
			.andExpect(jsonPath("$.shares[1].owedShare").value("3.33"))
			.andExpect(jsonPath("$.shares[2].owedShare").value("3.34"));
	}

	@Test
	void exactAmountsThatDoNotAddUpAre422SharesNotTotal() throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withSplitStrategy("EXACT")
			.withValues("6.00", "3.00")
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("SHARES_NOT_TOTAL"));
		expenses.list(alice).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void percentagesThatDoNotAddUpAre422() throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withSplitStrategy("PERCENTAGE")
			.withValues("60", "39.99")
			.record(alice)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("PERCENTAGES_NOT_100"));
		expenses.list(alice).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void aValueWithEqualOrNoValueWithExactOrPercentageIs400() throws Exception {
		expenses.paidBy(alice)
			.splitBetween(alice, bob)
			.withValues("5.00", "5.00")
			.record(alice)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		for (String strategy : new String[] { "EXACT", "PERCENTAGE" }) {
			expenses.paidBy(alice)
				.splitBetween(alice, bob)
				.withSplitStrategy(strategy)
				.withValues()
				.record(alice)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "33.333", "abc", "1,5" })
	void aValueThatIsNotANumberWithAtMostTwoDecimalsIs400(String value) throws Exception {
		for (String strategy : new String[] { "EXACT", "PERCENTAGE" }) {
			expenses.paidBy(alice)
				.splitBetween(alice)
				.withSplitStrategy(strategy)
				.withValues(value)
				.record(alice)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		}
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
	void putCorrectsTheExpenseAnswersWithItsRevisionAndTheBalancesFollow() throws Exception {
		// given alice paid 90.00 for the three of them (alice +60.00)
		UUID dinner = expenses.withTitle("Dinner")
			.withTotalAmount("90.00")
			.paidBy(alice)
			.splitBetween(alice, bob, carol)
			.recorded(alice);

		// when it is corrected to 120.00
		expenses.withTotalAmount("120.00")
			.correct(dinner, alice, "Receipt said 120")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CORRECTED"))
			.andExpect(jsonPath("$.totalAmount").value("120.00"))
			.andExpect(jsonPath("$.revisions[0].previous.totalAmount").value("90.00"))
			.andExpect(jsonPath("$.revisions[0].previous.shares[0].owedShare").value("30.00"))
			.andExpect(jsonPath("$.revisions[0].correctedBy").value(alice.idString()))
			.andExpect(jsonPath("$.revisions[0].reason").value("Receipt said 120"));

		// then the event went out and alice's balance becomes +80.00 a moment later (R7,
		// R14)
		assertThat(events.stream(ExpenseCorrected.class).filter(e -> e.expenseId().value().equals(dinner)))
			.singleElement();
		awaitBalanceOf(alice, "80.00");
	}

	@Test
	void putByAMemberWhoIsNeitherRecorderNorPayerNorAdminIs403AndChangesNothing() throws Exception {
		// given bob paid and recorded it; alice is the admin
		UUID taxi = expenses.withTotalAmount("30.00").paidBy(bob).splitBetween(bob, carol).recorded(bob);

		// when / then (R6)
		expenses.withTotalAmount("60.00")
			.correct(taxi, carol, null)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_ALLOWED_TO_CHANGE_EXPENSE"));
		expenses.list(alice).andExpect(jsonPath("$[0].totalAmount").value("30.00"));
		assertThat(events.stream(ExpenseCorrected.class)).isEmpty();
	}

	@Test
	void changingAnExpenseOfAClosedGroupIs422GroupNotOpen() throws Exception {
		// given
		UUID taxi = expenses.withTotalAmount("30.00").paidBy(bob).splitBetween(bob, carol).recorded(bob);
		new GroupRequests(mvc, json).close(group, alice).andExpect(status().isOk());

		// when / then (R5, L3)
		expenses.correct(taxi, bob, null)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_OPEN"));
		expenses.remove(taxi, bob).andExpect(jsonPath("$.code").value("GROUP_NOT_OPEN"));
	}

	@Test
	void deleteRemovesTheExpenseItStaysInTheListAndTheBalancesGoBack() throws Exception {
		// given alice paid 90.00 for the three of them
		UUID dinner = expenses.withTotalAmount("90.00").paidBy(alice).splitBetween(alice, bob, carol).recorded(alice);
		awaitBalanceOf(alice, "60.00");

		// when alice, who paid and recorded it, removes it
		expenses.remove(dinner, alice).andExpect(status().isNoContent());

		// then (R8)
		expenses.list(alice).andExpect(jsonPath("$[0].status").value("REMOVED"));
		awaitBalanceOf(alice, "0.00");
		expenses.remove(dinner, alice)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXPENSE_REMOVED"));
	}

	@Test
	void anUnknownExpenseIs404ExpenseNotFound() throws Exception {
		expenses.remove(UUID.randomUUID(), alice)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EXPENSE_NOT_FOUND"));
	}

	@Test
	void anExpenseOfSomebodyWhoLeftCannotBeRemovedAnyMore() throws Exception {
		// given carol paid a 30.00 museum ticket for herself only (her balance stays 0)
		// and then left
		UUID museum = expenses.withTotalAmount("30.00").paidBy(carol).splitBetween(carol).recorded(carol);
		new GroupRequests(mvc, json).leave(group, carol).andExpect(status().isNoContent());

		// when / then (design 2026-11-28-expense-correction E3)
		expenses.remove(museum, alice)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FORMER_MEMBER_INVOLVED"));
	}

	private void awaitBalanceOf(AccountFixture person, String amount) {
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(
					() -> mvc.perform(get("/groups/{id}/balances", group).header("Authorization", alice.bearer()))
						.andExpect(jsonPath("$[?(@.userId=='" + person.idString() + "')].netBalance").value(amount)));
	}

	@Test
	void bothEndpointsNeedASignedInUser() throws Exception {
		mvc.perform(get("/groups/" + group + "/expenses")).andExpect(status().isUnauthorized());
		mvc.perform(post("/groups/" + group + "/expenses").contentType(APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
