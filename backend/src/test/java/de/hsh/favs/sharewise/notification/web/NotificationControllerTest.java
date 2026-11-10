package de.hsh.favs.sharewise.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.web.GroupRequests;
import de.hsh.favs.sharewise.notification.application.NotificationService;
import de.hsh.favs.sharewise.notification.domain.Notification;
import de.hsh.favs.sharewise.notification.domain.NotificationType;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API contract of the inbox, driven by the real flow: an invitation is answered, the
 * group publishes the event, the notification module writes the entry asynchronously.
 */
@ModuleIntegrationTest
class NotificationControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	MutableClock clock;

	@Autowired
	NotificationService notifications;

	GroupRequests groups;

	AccountFixture alice;

	AccountFixture bob;

	AccountFixture carol;

	@BeforeEach
	void setUp() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		carol = AccountFixture.signUp(mvc, json, "Carol");
		clock.reset();
		groups = new GroupRequests(mvc, json);
	}

	@Test
	void anAcceptedInvitationShowsAJoinedEntryWithActorGroupAndUnreadCount() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		// when
		groups.accept(groups.invite(group, alice, bob), bob).andExpect(status().isOk());

		// then
		JsonNode inbox = awaitEntries(alice, 1);
		assertThat(inbox.get("unreadCount").asInt()).isEqualTo(1);
		JsonNode entry = inbox.get("items").get(0);
		assertThat(entry.get("type").asString()).isEqualTo("MEMBER_JOINED");
		assertThat(entry.get("readAt").isNull()).isTrue();
		assertThat(entry.get("actor").get("userId").asString()).isEqualTo(bob.idString());
		assertThat(entry.get("actor").get("username").asString()).isEqualTo(bob.username());
		assertThat(entry.get("actor").get("displayName").asString()).isEqualTo(bob.displayName());
		assertThat(entry.get("group").get("id").asString()).isEqualTo(group.toString());
		assertThat(entry.get("group").get("name").asString()).isEqualTo("Mallorca 2027");
		assertThat(entry.get("reference").asString()).isEqualTo(bob.idString());
	}

	@Test
	void aDeclinedInvitationShowsADeclinedEntryPointingAtTheInvitation() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		UUID invitation = groups.invite(group, alice, bob);

		// when
		groups.decline(invitation, bob).andExpect(status().isNoContent());

		// then
		JsonNode entry = awaitEntries(alice, 1).get("items").get(0);
		assertThat(entry.get("type").asString()).isEqualTo("INVITATION_DECLINED");
		assertThat(entry.get("actor").get("userId").asString()).isEqualTo(bob.idString());
		assertThat(entry.get("reference").asString()).isEqualTo(invitation.toString());
	}

	@Test
	void aPromotionShowsAPromotedEntryToThePromotedPersonOnly() throws Exception {
		// given Bob joined Alice's group
		UUID group = groups.groupWith(alice, "Mallorca 2027", bob);

		// when
		groups.promote(group, alice, bob).andExpect(status().isOk());

		// then Bob's only entry is the promotion; Alice's only entry is still Bob's join
		JsonNode entry = awaitEntries(bob, 1).get("items").get(0);
		assertThat(entry.get("type").asString()).isEqualTo("MEMBER_PROMOTED");
		assertThat(entry.get("actor").get("userId").asString()).isEqualTo(alice.idString());
		assertThat(entry.get("group").get("id").asString()).isEqualTo(group.toString());
		assertThat(entry.get("reference").asString()).isEqualTo(bob.idString());
		JsonNode alicesEntries = awaitEntries(alice, 1).get("items");
		assertThat(alicesEntries).hasSize(1);
		assertThat(alicesEntries.get(0).get("type").asString()).isEqualTo("MEMBER_JOINED");
	}

	@Test
	void theCreatorsOwnGroupCreationAndAnInviteWithoutAnAnswerShowNothing() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Quiet");
		groups.invite(group, alice, bob);

		// when / then Bob has a pending invitation but no inbox entry, Alice has none
		// either
		inbox(bob, "").andExpect(jsonPath("$.unreadCount").value(0)).andExpect(jsonPath("$.items.length()").value(0));
		inbox(alice, "").andExpect(jsonPath("$.unreadCount").value(0)).andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void theUnreadFilterListsOnlyUnreadEntriesAndReadMarksOneAndTheCountDrops() throws Exception {
		// given two entries for Alice
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		groups.accept(groups.invite(group, alice, bob), bob).andExpect(status().isOk());
		groups.accept(groups.invite(group, alice, carol), carol).andExpect(status().isOk());
		JsonNode both = awaitEntries(alice, 2);
		String first = both.get("items").get(0).get("id").asString();
		String second = both.get("items").get(1).get("id").asString();

		// when she reads one
		mvc.perform(post("/notifications/" + first + "/read").header("Authorization", alice.bearer()))
			.andExpect(status().isNoContent());

		// then
		inbox(alice, "?unread=true").andExpect(jsonPath("$.unreadCount").value(1))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(second));
		inbox(alice, "").andExpect(jsonPath("$.items.length()").value(2));
	}

	@Test
	void readAllMarksOnlyTheCallersEntries() throws Exception {
		// given an entry for Alice (Bob joined) and one for Bob (Carol joined through
		// Bob, who can invite because Alice invited him as an admin)
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		String invited = groups
			.body(groups.createInvitation(group, alice, bob.username(), "ADMIN").andExpect(status().isCreated()));
		groups.accept(UUID.fromString(json.readTree(invited).get("invitationId").asString()), bob)
			.andExpect(status().isOk());
		groups.accept(groups.invite(group, bob, carol), carol).andExpect(status().isOk());
		awaitEntries(alice, 1);
		awaitEntries(bob, 1);

		// when
		mvc.perform(post("/notifications/read-all").header("Authorization", alice.bearer()))
			.andExpect(status().isNoContent());

		// then
		inbox(alice, "").andExpect(jsonPath("$.unreadCount").value(0));
		inbox(bob, "").andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	void anotherPersonsEntryIs404NotificationNotFoundOnRead() throws Exception {
		// given an entry of Alice
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		groups.accept(groups.invite(group, alice, bob), bob).andExpect(status().isOk());
		String id = awaitEntries(alice, 1).get("items").get(0).get("id").asString();

		// when / then Bob cannot touch it, and it stays unread
		mvc.perform(post("/notifications/" + id + "/read").header("Authorization", bob.bearer()))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
		mvc.perform(post("/notifications/" + UUID.randomUUID() + "/read").header("Authorization", bob.bearer()))
			.andExpect(status().isNotFound());
		inbox(alice, "").andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	void anEntryWhoseActorAndGroupCannotBeResolvedHasNullActorAndGroup() throws Exception {
		// given an entry that points at an account and a group that do not exist
		notifications.record(Notification.notify(alice.userId(), NotificationType.MEMBER_JOINED,
				new GroupId(UUID.randomUUID()), new UserId(UUID.randomUUID()), UUID.randomUUID(), clock.instant()));

		// when / then
		inbox(alice, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].actor").doesNotExist())
			.andExpect(jsonPath("$.items[0].group").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "51", "-1", "abc" })
	void aLimitOutsideOneToFiftyIs400(String limit) throws Exception {
		inbox(alice, "?limit=" + limit).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void theLimitCapsTheListButNotTheUnreadCount() throws Exception {
		// given two entries
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		groups.accept(groups.invite(group, alice, bob), bob).andExpect(status().isOk());
		groups.accept(groups.invite(group, alice, carol), carol).andExpect(status().isOk());
		awaitEntries(alice, 2);

		// when / then
		inbox(alice, "?limit=1").andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.unreadCount").value(2));
	}

	@Test
	void everyNotificationEndpointWithoutATokenIs401() throws Exception {
		mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
		mvc.perform(post("/notifications/read-all")).andExpect(status().isUnauthorized());
		mvc.perform(post("/notifications/" + UUID.randomUUID() + "/read")).andExpect(status().isUnauthorized());
	}

	private ResultActions inbox(AccountFixture caller, String query) throws Exception {
		return mvc.perform(get("/notifications" + query).header("Authorization", caller.bearer()));
	}

	/**
	 * Waits until the caller's inbox has {@code count} entries (the listener is
	 * asynchronous).
	 */
	private JsonNode awaitEntries(AccountFixture caller, int count) {
		JsonNode[] last = new JsonNode[1];
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			last[0] = json.readTree(inbox(caller, "").andReturn().getResponse().getContentAsString());
			assertThat(last[0].get("items").size()).isEqualTo(count);
		});
		return last[0];
	}

}
