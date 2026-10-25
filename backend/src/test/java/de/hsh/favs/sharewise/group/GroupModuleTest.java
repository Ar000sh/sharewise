package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class GroupModuleTest {

	static final String ALICE = "aaaaaaaa-0000-0000-0000-000000000001";
	static final String BOB = "aaaaaaaa-0000-0000-0000-000000000002";

	@Autowired
	MockMvc mvc;

	@Autowired
	GroupMembership membership;

	@Autowired
	ApplicationEvents events;

	@Autowired
	JsonMapper json;

	String createGroup(String userId, String name) throws Exception {
		String body = mvc.perform(post("/groups").header("X-User-Id", userId).contentType(APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}")).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return json.readTree(body).get("id").asString();
	}

	@Test
	void createGroupMakesCallerAdminMemberAndPublishesEvents() throws Exception {
		String body = mvc.perform(post("/groups").header("X-User-Id", ALICE).contentType(APPLICATION_JSON)
				.content("{\"name\":\"Mallorca 2027\"}")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Mallorca 2027")).andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.memberCount").value(1)).andReturn().getResponse().getContentAsString();
		GroupId id = new GroupId(UUID.fromString(json.readTree(body).get("id").asString()));

		assertThat(events.stream(GroupCreated.class)).anySatisfy(
				e -> assertThat(e).isEqualTo(new GroupCreated(id, new UserId(UUID.fromString(ALICE)))));
		assertThat(events.stream(MemberJoined.class)).anySatisfy(e -> assertThat(e)
				.isEqualTo(new MemberJoined(id, new MemberId(UUID.fromString(ALICE)), Role.ADMIN)));

		mvc.perform(get("/groups/" + id.value()).header("X-User-Id", ALICE)).andExpect(status().isOk())
				.andExpect(jsonPath("$.members", hasSize(1))).andExpect(jsonPath("$.members[0].memberId").value(ALICE))
				.andExpect(jsonPath("$.members[0].displayName").value("Alice"))
				.andExpect(jsonPath("$.members[0].role").value("ADMIN"));
	}

	@Test
	void blankNameIs400BadRequest() throws Exception {
		for (String name : new String[] { "", "   ", "x".repeat(101) }) {
			mvc.perform(post("/groups").header("X-User-Id", ALICE).contentType(APPLICATION_JSON)
					.content("{\"name\":\"" + name + "\"}")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		}
		mvc.perform(post("/groups").header("X-User-Id", ALICE).contentType(APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void nameOfExactly100CharactersIsAccepted() throws Exception {
		mvc.perform(post("/groups").header("X-User-Id", ALICE).contentType(APPLICATION_JSON)
				.content("{\"name\":\"" + "x".repeat(100) + "\"}")).andExpect(status().isCreated());
	}

	@Test
	void listReturnsOnlyMyGroups() throws Exception {
		String mine = "Only Bob " + UUID.randomUUID();
		createGroup(BOB, mine);
		String body = mvc.perform(get("/groups").header("X-User-Id", ALICE)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		assertThat(body).doesNotContain(mine);
		mvc.perform(get("/groups").header("X-User-Id", BOB)).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name=='" + mine + "')].memberCount").value(1));
	}

	@Test
	void detailForNonMemberIs403NotAMember() throws Exception {
		String id = createGroup(ALICE, "Private");
		mvc.perform(get("/groups/" + id).header("X-User-Id", BOB)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void detailForUnknownGroupIs404() throws Exception {
		mvc.perform(get("/groups/" + UUID.randomUUID()).header("X-User-Id", ALICE)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void malformedGroupIdIs400() throws Exception {
		mvc.perform(get("/groups/not-a-uuid").header("X-User-Id", ALICE)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void groupEndpointsRequireIdentity() throws Exception {
		mvc.perform(get("/groups")).andExpect(status().isUnauthorized());
		mvc.perform(post("/groups").contentType(APPLICATION_JSON).content("{\"name\":\"x\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void membershipExistsIsMemberMembersStatus() throws Exception {
		GroupId id = new GroupId(UUID.fromString(createGroup(ALICE, "Membership")));
		MemberId alice = new MemberId(UUID.fromString(ALICE));
		MemberId bob = new MemberId(UUID.fromString(BOB));

		assertThat(membership.exists(id)).isTrue();
		assertThat(membership.exists(new GroupId(UUID.randomUUID()))).isFalse();
		assertThat(membership.isMember(id, alice)).isTrue();
		assertThat(membership.isMember(id, bob)).isFalse();
		assertThat(membership.members(id)).containsExactly(alice);
		assertThat(membership.status(id)).isEqualTo(GroupStatus.OPEN);
	}

}
