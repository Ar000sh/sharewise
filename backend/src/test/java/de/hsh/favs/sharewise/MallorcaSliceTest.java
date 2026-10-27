package de.hsh.favs.sharewise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Ex4 slice with every real bean and no stubs: sign up, create a group, invite,
 * accept, record equal-split expenses, read balances. Balances arrive through the
 * {@code ExpenseRecorded} event, so they are awaited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MallorcaSliceTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	/** User ids of four freshly signed-up users, and the session token of each. */
	String alice, bob, carol, david;

	final Map<String, String> tokens = new LinkedHashMap<>();

	@BeforeEach
	void signUpFourFriends() throws Exception {
		alice = signUp("Alice");
		bob = signUp("Bob");
		carol = signUp("Carol");
		david = signUp("David");
	}

	@Test
	void mallorcaBalancesAfterHotelAndDinner() throws Exception {
		// given Alice's group with Bob, Carol and David as members
		UUID group = groupWith(alice, bob, carol, david);
		List<String> everyone = List.of(alice, bob, carol, david);

		// when Alice pays the hotel and Bob the dinner, both split between all four
		recordExpense(group, alice, "Hotel", 90000, alice, everyone).andExpect(status().isCreated());
		recordExpense(group, bob, "Dinner", 18000, bob, everyone).andExpect(status().isCreated());

		// then the balances settle to the documented Mallorca figures and add up to zero
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances(group, alice)).containsExactlyInAnyOrderEntriesOf(
					Map.of(alice, 63000L, bob, -9000L, carol, -27000L, david, -27000L)));
		assertThat(balances(group, alice).values().stream().mapToLong(Long::longValue).sum()).isZero();
	}

	@Test
	void responsesCarryExactlyTheFieldsOfTheContract() throws Exception {
		// given a group with one invited member and one expense
		UUID group = groupWith(alice, bob);

		// when the caller reads the endpoints
		JsonNode created = read(post("/groups").header("Authorization", auth(alice))
			.contentType(APPLICATION_JSON)
			.content("{\"name\":\"Shape\"}"));
		JsonNode detail = read(get("/groups/" + group).header("Authorization", auth(alice)));
		JsonNode expense = read(recordExpense(group, alice, "Taxi", 1000, alice, List.of(alice, bob)));
		JsonNode invitation = read(post("/groups/" + group + "/invitations").header("Authorization", auth(alice)));

		// then the field names are the ones in docs/api/openapi.yaml
		assertThat(fields(created)).containsExactly("id", "memberCount", "name", "status");
		assertThat(fields(detail)).containsExactly("id", "memberCount", "members", "name", "status");
		assertThat(fields(detail.get("members").get(0))).containsExactly("displayName", "role", "userId");
		assertThat(fields(expense)).containsExactly("amountCents", "date", "id", "payerId", "shares", "title");
		assertThat(fields(expense.get("shares").get(0))).containsExactly("memberId", "shareCents");
		assertThat(fields(invitation)).containsExactly("expiresAt", "token");
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			JsonNode balances = read(get("/groups/" + group + "/balances").header("Authorization", auth(alice)));
			assertThat(fields(balances.get(0))).containsExactly("balanceCents", "displayName", "memberId");
		});
	}

	@Test
	void lateJoinerIsListedAtZeroAndDoesNotShareEarlierExpenses() throws Exception {
		// given an expense between Alice and Bob before Carol joins
		UUID group = groupWith(alice, bob);
		recordExpense(group, alice, "Hotel", 10000, alice, List.of(alice, bob)).andExpect(status().isCreated());

		// when Carol joins afterwards
		join(group, alice, carol);

		// then she is listed with balance 0 and the sum is still 0
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances(group, carol))
				.containsExactlyInAnyOrderEntriesOf(Map.of(alice, 5000L, bob, -5000L, carol, 0L)));
	}

	@Test
	void remainderCentsGoToTheFirstParticipantsAndTheSumStaysZero() throws Exception {
		// given three members and an amount that does not divide by three
		UUID group = groupWith(alice, bob, carol);

		// when Alice pays 100.00 split three ways
		recordExpense(group, alice, "Tapas", 10000, alice, List.of(alice, bob, carol)).andExpect(status().isCreated());

		// then the first participant carries the extra cent
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances(group, alice))
				.containsExactlyInAnyOrderEntriesOf(Map.of(alice, 6666L, bob, -3333L, carol, -3333L)));
	}

	@Test
	void outsidersAndStrangersAreRefusedAndNothingIsBooked() throws Exception {
		// given a group of Alice and Bob; Carol is no member
		UUID group = groupWith(alice, bob);

		// then a non-member can neither read nor write
		mvc.perform(get("/groups/" + group + "/balances").header("Authorization", auth(carol)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/groups/" + group + "/expenses").header("Authorization", auth(carol)))
			.andExpect(status().isForbidden());
		recordExpense(group, carol, "Sneaky", 100, carol, List.of(carol)).andExpect(status().isForbidden());
		// and a member cannot name an outsider as payer or participant (422)
		recordExpense(group, alice, "Wrong payer", 100, carol, List.of(alice))
			.andExpect(status().isUnprocessableEntity());
		recordExpense(group, alice, "Wrong share", 100, alice, List.of(alice, carol))
			.andExpect(status().isUnprocessableEntity());
		// and an unknown user is not authenticated
		mvc.perform(get("/groups/" + group).header("Authorization", "Bearer unknown"))
			.andExpect(status().isUnauthorized());

		// then no refused call left a booking behind: one valid expense afterwards is the
		// only thing booked
		recordExpense(group, alice, "Valid", 200, alice, List.of(alice, bob)).andExpect(status().isCreated());
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(balances(group, alice))
				.containsExactlyInAnyOrderEntriesOf(Map.of(alice, 100L, bob, -100L)));
		assertThat(read(get("/groups/" + group + "/expenses").header("Authorization", auth(alice)))).hasSize(1);
	}

	@Test
	void joiningTwiceIsAConflict() throws Exception {
		UUID group = groupWith(alice, bob);
		String token = invite(group, alice);

		mvc.perform(post("/invitations/" + token + "/accept").header("Authorization", auth(bob)))
			.andExpect(status().isConflict());
		mvc.perform(post("/invitations/" + "x".repeat(43) + "/accept").header("Authorization", auth(carol)))
			.andExpect(status().isNotFound());
	}

	// --- helpers
	// ---------------------------------------------------------------------------------------------

	private String signUp(String name) throws Exception {
		AccountFixture user = AccountFixture.signUp(mvc, json, name);
		tokens.put(user.idString(), user.bearer());
		return user.idString();
	}

	private String auth(String userId) {
		return tokens.get(userId);
	}

	private UUID groupWith(String admin, String... joiners) throws Exception {
		JsonNode group = read(post("/groups").header("Authorization", auth(admin))
			.contentType(APPLICATION_JSON)
			.content("{\"name\":\"Mallorca 2027\"}"));
		UUID id = UUID.fromString(group.get("id").asString());
		for (String joiner : joiners) {
			if (!joiner.equals(admin)) {
				join(id, admin, joiner);
			}
		}
		return id;
	}

	private void join(UUID group, String inviter, String joiner) throws Exception {
		mvc.perform(post("/invitations/" + invite(group, inviter) + "/accept").header("Authorization", auth(joiner)))
			.andExpect(status().isOk());
	}

	private String invite(UUID group, String caller) throws Exception {
		return read(post("/groups/" + group + "/invitations").header("Authorization", auth(caller))).get("token")
			.asString();
	}

	private ResultActions recordExpense(UUID group, String caller, String title, long cents, String payer,
			List<String> participants) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("amountCents", cents);
		body.put("date", "2027-06-01");
		body.put("payerId", payer);
		body.put("participantIds", participants);
		body.put("split", "EQUAL");
		return mvc.perform(post("/groups/" + group + "/expenses").header("Authorization", auth(caller))
			.contentType(APPLICATION_JSON)
			.content(json.writeValueAsString(body)));
	}

	private Map<String, Long> balances(UUID group, String caller) throws Exception {
		Map<String, Long> result = new LinkedHashMap<>();
		for (JsonNode b : read(get("/groups/" + group + "/balances").header("Authorization", auth(caller)))) {
			result.put(b.get("memberId").asString(), b.get("balanceCents").asLong());
		}
		return result;
	}

	private JsonNode read(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
		return json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString());
	}

	private JsonNode read(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private static TreeSet<String> fields(JsonNode node) {
		TreeSet<String> names = new TreeSet<>();
		node.propertyNames().forEach(names::add);
		return names;
	}

}
