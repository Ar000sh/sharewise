package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** API contract of the group endpoints through the whole stack down to Postgres. */
@ModuleIntegrationTest
class GroupControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ApplicationEvents events;

	@Autowired
	JsonMapper json;

	GroupRequests groups;

	AccountFixture alice;

	AccountFixture bob;

	AccountFixture carol;

	@BeforeEach
	void setUp() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		carol = AccountFixture.signUp(mvc, json, "Carol");
		groups = new GroupRequests(mvc, json);
	}

	@Test
	void postCreatesTheGroupWithTheCallerAsAdminAndPublishesBothEvents() throws Exception {
		// when
		String body = groups.body(groups.create(alice, "Mallorca 2027")
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("Mallorca 2027"))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.memberCount").value(1)));

		// then
		GroupId id = new GroupId(UUID.fromString(json.readTree(body).get("id").asString()));
		assertThat(events.stream(GroupCreated.class).filter(e -> e.groupId().equals(id))).singleElement()
			.satisfies(e -> assertThat(e.creator()).isEqualTo(alice.userId()));
		assertThat(events.stream(MemberJoined.class).filter(e -> e.groupId().equals(id))).singleElement()
			.satisfies(e -> {
				assertThat(e.userId()).isEqualTo(alice.userId());
				assertThat(e.role()).isEqualTo(GroupRole.ADMIN);
			});
		groups.detail(id.value(), alice)
			.andExpect(jsonPath("$.members[0].userId").value(alice.idString()))
			.andExpect(jsonPath("$.members[0].displayName").value("Alice"))
			.andExpect(jsonPath("$.members[0].role").value("ADMIN"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "{not json" })
	void postWithBlankNameOrBrokenJsonIs400(String name) throws Exception {
		// "{not json" is sent as the raw body, the others as the name
		var request = post("/groups").header("Authorization", alice.bearer()).contentType(APPLICATION_JSON);
		request = name.startsWith("{") ? request.content(name) : request.content("{\"name\":\"" + name + "\"}");

		mvc.perform(request)
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void postWithMissingNameOrNameOver100CharactersIs400() throws Exception {
		mvc.perform(post("/groups").header("Authorization", alice.bearer()).contentType(APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		groups.create(alice, "x".repeat(101)).andExpect(status().isBadRequest());
		groups.create(alice, "x".repeat(100)).andExpect(status().isCreated());
	}

	@Test
	void getListsOnlyTheGroupsTheCallerIsMemberOf() throws Exception {
		// given
		String joined = "Joined " + UUID.randomUUID();
		String foreign = "Foreign " + UUID.randomUUID();
		groups.groupWith(alice, joined, bob);
		groups.createGroup(carol, foreign);

		// when / then
		groups.list(bob)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name=='" + joined + "')].memberCount").value(2))
			.andExpect(jsonPath("$[?(@.name=='" + foreign + "')]", hasSize(0)));
	}

	@Test
	void getDetailListsMembersWithNamesAndRoles() throws Exception {
		// given
		UUID id = groups.groupWith(alice, "Trip", bob);

		// when / then
		groups.detail(id, bob)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberCount").value(2))
			.andExpect(jsonPath("$.members[?(@.userId=='" + alice.idString() + "')].displayName").value("Alice"))
			.andExpect(jsonPath("$.members[?(@.userId=='" + alice.idString() + "')].role").value("ADMIN"))
			.andExpect(jsonPath("$.members[?(@.userId=='" + bob.idString() + "')].displayName").value("Bob"))
			.andExpect(jsonPath("$.members[?(@.userId=='" + bob.idString() + "')].role").value("MEMBER"));
	}

	@Test
	void getDetailAsNonMemberIs403() throws Exception {
		UUID id = groups.createGroup(alice, "Private");

		groups.detail(id, bob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void getDetailOfAnUnknownGroupIs404() throws Exception {
		groups.detail(UUID.randomUUID(), alice)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void getDetailWithAMalformedIdIs400() throws Exception {
		mvc.perform(get("/groups/not-a-uuid").header("Authorization", alice.bearer()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void everyGroupEndpointNeedsAKnownUser() throws Exception {
		mvc.perform(get("/groups"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(post("/groups").contentType(APPLICATION_JSON).content("{\"name\":\"x\"}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/groups/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

}
