package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** API contract of the invitation endpoints, including expiry with a moved clock. */
@ModuleIntegrationTest
class InvitationControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MutableClock clock;

	@Autowired
	ApplicationEvents events;

	@Autowired
	JsonMapper json;

	GroupRequests groups;

	AccountFixture alice;

	AccountFixture bob;

	@BeforeEach
	void setUp() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		clock.reset();
		groups = new GroupRequests(mvc, json);
	}

	@Test
	void postCreatesAnInvitationValidForSevenDays() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		// when / then
		groups.createInvitation(group, alice)
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith("application/json"))
			.andExpect(jsonPath("$.token").isString())
			.andExpect(jsonPath("$.expiresAt").value("2027-06-08T10:00:00Z"));
	}

	@Test
	void postAsNonMemberIs403() throws Exception {
		UUID group = groups.createGroup(alice, "Closed circle");

		groups.createInvitation(group, bob)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void postForAnUnknownGroupIs404() throws Exception {
		groups.createInvitation(UUID.randomUUID(), alice)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void acceptMakesTheCallerAMemberAndPublishesMemberJoined() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		String token = groups.invite(group, alice);

		// when
		groups.accept(token, bob)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(group.toString()))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.memberCount").value(2));

		// then
		GroupId id = new GroupId(group);
		assertThat(events.stream(MemberJoined.class).filter(e -> e.groupId().equals(id)))
			.contains(new MemberJoined(id, bob.userId(), Role.MEMBER));
		groups.detail(group, bob).andExpect(status().isOk());
	}

	@Test
	void acceptingTwiceOrAsTheCreatorIs409() throws Exception {
		// given
		String token = groups.invite(groups.createGroup(alice, "Twice"), alice);
		groups.accept(token, bob).andExpect(status().isOk());

		// when / then
		groups.accept(token, bob)
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
		groups.accept(token, alice)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
	}

	@Test
	void acceptAfterSevenDaysIs410AndNobodyJoins() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Late");
		String token = groups.invite(group, alice);
		clock.advance(Duration.ofDays(7).plusSeconds(1));

		// when / then
		groups.accept(token, bob)
			.andExpect(status().isGone())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
		groups.detail(group, alice).andExpect(jsonPath("$.memberCount").value(1));
	}

	@Test
	void acceptWithAnUnknownTokenIs404() throws Exception {
		groups.accept("does-not-exist-token-123456", bob)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
	}

	@Test
	void invitationEndpointsNeedAKnownUser() throws Exception {
		mvc.perform(post("/invitations/anything-at-all-123456/accept")).andExpect(status().isUnauthorized());
		mvc.perform(post("/groups/" + UUID.randomUUID() + "/invitations")).andExpect(status().isUnauthorized());
	}

}
