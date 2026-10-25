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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Ex4 slice with every real bean and no stubs: create a group, invite, accept, record equal-split expenses,
 * read balances. Balances arrive through the {@code ExpenseRecorded} event, so they are awaited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class MallorcaSliceTest {

	private static final String ALICE = "aaaaaaaa-0000-0000-0000-000000000001";

	private static final String BOB = "aaaaaaaa-0000-0000-0000-000000000002";

	private static final String CAROL = "aaaaaaaa-0000-0000-0000-000000000003";

	private static final String DAVID = "aaaaaaaa-0000-0000-0000-000000000004";

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Test
	void mallorcaBalancesAfterHotelAndDinner() throws Exception {
		// given Alice's group with Bob, Carol and David as members
		UUID group = groupWith(ALICE, BOB, CAROL, DAVID);
		List<String> everyone = List.of(ALICE, BOB, CAROL, DAVID);

		// when Alice pays the hotel and Bob the dinner, both split between all four
		recordExpense(group, ALICE, "Hotel", 90000, ALICE, everyone).andExpect(status().isCreated());
		recordExpense(group, BOB, "Dinner", 18000, BOB, everyone).andExpect(status().isCreated());

		// then the balances settle to the documented Mallorca figures and add up to zero
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(balances(group, ALICE))
			.containsExactlyInAnyOrderEntriesOf(Map.of(ALICE, 63000L, BOB, -9000L, CAROL, -27000L, DAVID, -27000L)));
		assertThat(balances(group, ALICE).values().stream().mapToLong(Long::longValue).sum()).isZero();
	}

	@Test
	void responsesCarryExactlyTheFieldsOfTheContract() throws Exception {
		// given a group with one invited member and one expense
		UUID group = groupWith(ALICE, BOB);

		// when the caller reads the endpoints
		JsonNode created = read(post("/groups").header("X-User-Id", ALICE).contentType(APPLICATION_JSON)
			.content("{\"name\":\"Shape\"}"));
		JsonNode detail = read(get("/groups/" + group).header("X-User-Id", ALICE));
		JsonNode expense = read(recordExpense(group, ALICE, "Taxi", 1000, ALICE, List.of(ALICE, BOB)));
		JsonNode invitation = read(post("/groups/" + group + "/invitations").header("X-User-Id", ALICE));

		// then the field names are the ones in docs/api/openapi.yaml
		assertThat(fields(created)).containsExactly("id", "memberCount", "name", "status");
		assertThat(fields(detail)).containsExactly("id", "memberCount", "members", "name", "status");
		assertThat(fields(detail.get("members").get(0))).containsExactly("displayName", "memberId", "role");
		assertThat(fields(expense)).containsExactly("amountCents", "date", "id", "payerId", "shares", "title");
		assertThat(fields(expense.get("shares").get(0))).containsExactly("memberId", "shareCents");
		assertThat(fields(invitation)).containsExactly("expiresAt", "token");
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			JsonNode balances = read(get("/groups/" + group + "/balances").header("X-User-Id", ALICE));
			assertThat(fields(balances.get(0))).containsExactly("balanceCents", "displayName", "memberId");
		});
	}

	@Test
	void lateJoinerIsListedAtZeroAndDoesNotShareEarlierExpenses() throws Exception {
		// given an expense between Alice and Bob before Carol joins
		UUID group = groupWith(ALICE, BOB);
		recordExpense(group, ALICE, "Hotel", 10000, ALICE, List.of(ALICE, BOB)).andExpect(status().isCreated());

		// when Carol joins afterwards
		join(group, ALICE, CAROL);

		// then she is listed with balance 0 and the sum is still 0
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(balances(group, CAROL))
			.containsExactlyInAnyOrderEntriesOf(Map.of(ALICE, 5000L, BOB, -5000L, CAROL, 0L)));
	}

	@Test
	void remainderCentsGoToTheFirstParticipantsAndTheSumStaysZero() throws Exception {
		// given three members and an amount that does not divide by three
		UUID group = groupWith(ALICE, BOB, CAROL);

		// when Alice pays 100.00 split three ways
		recordExpense(group, ALICE, "Tapas", 10000, ALICE, List.of(ALICE, BOB, CAROL)).andExpect(status().isCreated());

		// then the first participant carries the extra cent
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(balances(group, ALICE))
			.containsExactlyInAnyOrderEntriesOf(Map.of(ALICE, 6666L, BOB, -3333L, CAROL, -3333L)));
	}

	@Test
	void outsidersAndStrangersAreRefusedAndNothingIsBooked() throws Exception {
		// given a group of Alice and Bob; Carol is no member
		UUID group = groupWith(ALICE, BOB);

		// then a non-member can neither read nor write
		mvc.perform(get("/groups/" + group + "/balances").header("X-User-Id", CAROL)).andExpect(status().isForbidden());
		mvc.perform(get("/groups/" + group + "/expenses").header("X-User-Id", CAROL)).andExpect(status().isForbidden());
		recordExpense(group, CAROL, "Sneaky", 100, CAROL, List.of(CAROL)).andExpect(status().isForbidden());
		// and a member cannot name an outsider as payer or participant (422)
		recordExpense(group, ALICE, "Wrong payer", 100, CAROL, List.of(ALICE)).andExpect(status().isUnprocessableEntity());
		recordExpense(group, ALICE, "Wrong share", 100, ALICE, List.of(ALICE, CAROL))
			.andExpect(status().isUnprocessableEntity());
		// and an unknown user is not authenticated
		mvc.perform(get("/groups/" + group).header("X-User-Id", UUID.randomUUID().toString()))
			.andExpect(status().isUnauthorized());

		// then no refused call left a booking behind: one valid expense afterwards is the only thing booked
		recordExpense(group, ALICE, "Valid", 200, ALICE, List.of(ALICE, BOB)).andExpect(status().isCreated());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(balances(group, ALICE))
			.containsExactlyInAnyOrderEntriesOf(Map.of(ALICE, 100L, BOB, -100L)));
		assertThat(read(get("/groups/" + group + "/expenses").header("X-User-Id", ALICE))).hasSize(1);
	}

	@Test
	void joiningTwiceIsAConflict() throws Exception {
		UUID group = groupWith(ALICE, BOB);
		String token = invite(group, ALICE);

		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", BOB)).andExpect(status().isConflict());
		mvc.perform(post("/invitations/" + "x".repeat(43) + "/accept").header("X-User-Id", CAROL))
			.andExpect(status().isNotFound());
	}

	// --- helpers ---------------------------------------------------------------------------------------------

	private UUID groupWith(String admin, String... joiners) throws Exception {
		JsonNode group = read(post("/groups").header("X-User-Id", admin).contentType(APPLICATION_JSON)
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
		mvc.perform(post("/invitations/" + invite(group, inviter) + "/accept").header("X-User-Id", joiner))
			.andExpect(status().isOk());
	}

	private String invite(UUID group, String caller) throws Exception {
		return read(post("/groups/" + group + "/invitations").header("X-User-Id", caller)).get("token").asString();
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
		return mvc.perform(post("/groups/" + group + "/expenses").header("X-User-Id", caller)
			.contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private Map<String, Long> balances(UUID group, String caller) throws Exception {
		Map<String, Long> result = new LinkedHashMap<>();
		for (JsonNode b : read(get("/groups/" + group + "/balances").header("X-User-Id", caller))) {
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
