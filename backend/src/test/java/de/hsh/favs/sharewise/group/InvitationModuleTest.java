package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.TestcontainersConfiguration;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@RecordApplicationEvents
@Import({ TestcontainersConfiguration.class, InvitationModuleTest.ClockConfig.class })
class InvitationModuleTest {

	static final String ALICE = "aaaaaaaa-0000-0000-0000-000000000001";
	static final String BOB = "aaaaaaaa-0000-0000-0000-000000000002";
	static final String CAROL = "aaaaaaaa-0000-0000-0000-000000000003";

	/** A clock the tests can move forward. */
	static class MutableClock extends Clock {

		private volatile Instant now = Instant.parse("2027-06-01T10:00:00Z");

		void reset() {
			now = Instant.parse("2027-06-01T10:00:00Z");
		}

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

	}

	@Autowired
	MockMvc mvc;

	@Autowired
	MutableClock clock;

	@Autowired
	ApplicationEvents events;

	@Autowired
	JsonMapper json;

	@BeforeEach
	void resetClock() {
		clock.reset();
	}

	String createGroup(String userId) throws Exception {
		String body = mvc.perform(post("/groups").header("X-User-Id", userId).contentType(APPLICATION_JSON)
				.content("{\"name\":\"Mallorca 2027\"}")).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return json.readTree(body).get("id").asString();
	}

	String invite(String groupId, String userId) throws Exception {
		String body = mvc.perform(post("/groups/" + groupId + "/invitations").header("X-User-Id", userId))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("token").asString();
	}

	@Test
	void memberCreatesInviteWithSevenDayExpiry() throws Exception {
		String group = createGroup(ALICE);
		String body = mvc.perform(post("/groups/" + group + "/invitations").header("X-User-Id", ALICE))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.expiresAt").value("2027-06-08T10:00:00Z")).andReturn().getResponse()
				.getContentAsString();

		String token = json.readTree(body).get("token").asString();
		assertThat(token).matches("[A-Za-z0-9_-]+");
		assertThat(Base64.getUrlDecoder().decode(token).length).isGreaterThanOrEqualTo(16);
		assertThat(invite(group, ALICE)).isNotEqualTo(token);
	}

	@Test
	void nonMemberCannotInvite403() throws Exception {
		String group = createGroup(ALICE);
		mvc.perform(post("/groups/" + group + "/invitations").header("X-User-Id", BOB))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void inviteForUnknownGroupIs404() throws Exception {
		mvc.perform(post("/groups/" + UUID.randomUUID() + "/invitations").header("X-User-Id", ALICE))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void acceptMakesCallerMemberAndPublishesMemberJoined() throws Exception {
		String group = createGroup(ALICE);
		String token = invite(group, ALICE);

		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", BOB)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(group)).andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.memberCount").value(2));

		assertThat(events.stream(MemberJoined.class)).anySatisfy(
				e -> assertThat(e).isEqualTo(new MemberJoined(new GroupId(UUID.fromString(group)),
						new MemberId(UUID.fromString(BOB)), Role.MEMBER)));
		mvc.perform(get("/groups/" + group).header("X-User-Id", BOB)).andExpect(status().isOk())
				.andExpect(jsonPath("$.members[1].displayName").value("Bob"))
				.andExpect(jsonPath("$.members[1].role").value("MEMBER"));
	}

	@Test
	void acceptTwiceIs409AlreadyMember() throws Exception {
		String token = invite(createGroup(ALICE), ALICE);
		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", BOB)).andExpect(status().isOk());
		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", BOB))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
	}

	@Test
	void creatorAcceptingOwnInviteIs409() throws Exception {
		String token = invite(createGroup(ALICE), ALICE);
		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", ALICE))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
	}

	@Test
	void tokenCanBeUsedByDifferentUsers() throws Exception {
		String group = createGroup(ALICE);
		String token = invite(group, ALICE);
		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", BOB)).andExpect(status().isOk());
		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", CAROL))
				.andExpect(status().isOk()).andExpect(jsonPath("$.memberCount").value(3));
	}

	@Test
	void expiredTokenIs410() throws Exception {
		String token = invite(createGroup(ALICE), ALICE);
		clock.advance(Duration.ofDays(7).plusSeconds(1));
		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", BOB))
				.andExpect(status().isGone()).andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
	}

	@Test
	void tokenStillValidJustBeforeExpiry() throws Exception {
		String token = invite(createGroup(ALICE), ALICE);
		clock.advance(Duration.ofDays(7).minusSeconds(1));
		mvc.perform(post("/invitations/" + token + "/accept").header("X-User-Id", BOB)).andExpect(status().isOk());
	}

	@Test
	void unknownTokenIs404() throws Exception {
		mvc.perform(post("/invitations/does-not-exist-token-123456/accept").header("X-User-Id", BOB))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
	}

	@Test
	void invitationEndpointsRequireIdentity() throws Exception {
		mvc.perform(post("/invitations/anything-at-all-123456/accept")).andExpect(status().isUnauthorized());
		mvc.perform(post("/groups/" + UUID.randomUUID() + "/invitations")).andExpect(status().isUnauthorized());
	}

}
