package de.hsh.favs.sharewise.group.web;

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
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.shared.GroupId;
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

/**
 * API contract of the invitation endpoints: inviting an account, the caller's pending
 * list, accept and decline, including expiry with a moved clock.
 */
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
	void postCreatesAnInvitationForTheInviteeValidForSevenDays() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		// when / then
		groups.createInvitation(group, alice, bob.username())
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith("application/json"))
			.andExpect(jsonPath("$.invitationId").isString())
			.andExpect(jsonPath("$.invitee.userId").value(bob.idString()))
			.andExpect(jsonPath("$.invitee.username").value(bob.username()))
			.andExpect(jsonPath("$.expiresAt").value("2027-06-08T10:00:00Z"));
		assertThat(events.stream(MemberInvited.class).filter(e -> e.groupId().value().equals(group))).singleElement()
			.satisfies(e -> {
				assertThat(e.invitedBy()).isEqualTo(alice.userId());
				assertThat(e.invitee()).isEqualTo(bob.userId());
			});
	}

	@Test
	void postWithTheEmailOrUpperCaseAndSpacesAlsoFindsThePerson() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		// when / then
		groups.createInvitation(group, alice, bob.email().toUpperCase())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.invitee.userId").value(bob.idString()));
		groups.createInvitation(group, alice, "  " + carol.username().toUpperCase() + " ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.invitee.userId").value(carol.idString()));
	}

	@Test
	void postForAnUnknownIdentifierIs404AccountNotFound() throws Exception {
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		groups.createInvitation(group, alice, "nobody-by-that-name")
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "%", "_", "@", "ali%", "a@%" })
	void postWithWildcardOrAtSignIdentifiersFindsNobody(String identifier) throws Exception {
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		groups.createInvitation(group, alice, identifier)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void postWithABlankIdentifierIs400(String identifier) throws Exception {
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		groups.createInvitation(group, alice, identifier)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void postWithAnIdentifierOfMoreThan254CharactersIs400() throws Exception {
		UUID group = groups.createGroup(alice, "Mallorca 2027");

		groups.createInvitation(group, alice, "a".repeat(255))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void postForAMemberIs409AlreadyMember() throws Exception {
		UUID group = groups.groupWith(alice, "Mallorca 2027", bob);

		groups.createInvitation(group, alice, bob.username())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
		groups.createInvitation(group, alice, alice.username())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
	}

	@Test
	void postTwiceForTheSamePersonIs409AlreadyInvited() throws Exception {
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		groups.invite(group, alice, bob);

		groups.createInvitation(group, alice, bob.username())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_INVITED"));
	}

	@Test
	void postAgainAfterSevenDaysCreatesANewInvitation() throws Exception {
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		groups.invite(group, alice, bob);
		clock.advance(Duration.ofDays(7).plusSeconds(1));

		groups.createInvitation(group, alice, bob.username()).andExpect(status().isCreated());
	}

	@Test
	void postAsNonMemberIs403EvenForAnUnknownIdentifier() throws Exception {
		UUID group = groups.createGroup(alice, "Closed circle");

		groups.createInvitation(group, bob, carol.username())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
		groups.createInvitation(group, bob, "nobody-by-that-name")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void postForAnUnknownGroupIs404() throws Exception {
		groups.createInvitation(UUID.randomUUID(), alice, bob.username())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void pendingListsOnlyTheCallersOpenInvitationsNewestFirstWithGroupAndInviter() throws Exception {
		// given Bob is invited to three groups; the first one expires before the others
		UUID expired = groups.createGroup(alice, "Expired");
		UUID older = groups.createGroup(alice, "Older");
		UUID newer = groups.createGroup(alice, "Newer");
		groups.invite(expired, alice, bob);
		clock.advance(Duration.ofDays(3));
		UUID olderInvitation = groups.invite(older, alice, bob);
		clock.advance(Duration.ofHours(1));
		UUID newerInvitation = groups.invite(newer, alice, bob);
		groups.invite(newer, alice, carol);
		// the first invitation is now 7 days and one second old
		clock.advance(Duration.ofDays(4).minusHours(1).plusSeconds(1));

		// when / then
		groups.pending(bob)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].invitationId").value(newerInvitation.toString()))
			.andExpect(jsonPath("$[0].group.name").value("Newer"))
			.andExpect(jsonPath("$[0].group.id").value(newer.toString()))
			.andExpect(jsonPath("$[0].invitedBy.username").value(alice.username()))
			.andExpect(jsonPath("$[1].invitationId").value(olderInvitation.toString()));
		groups.pending(carol).andExpect(jsonPath("$.length()").value(1));
		groups.pending(alice).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void anInvitationExactlyAtItsExpiryInstantIsStillListed() throws Exception {
		UUID group = groups.createGroup(alice, "Edge");
		groups.invite(group, alice, bob);
		clock.advance(Duration.ofDays(7));

		groups.pending(bob).andExpect(jsonPath("$.length()").value(1));
		clock.advance(Duration.ofSeconds(1));
		groups.pending(bob).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void acceptMakesTheCallerAMemberAndPublishesMemberJoined() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		UUID invitation = groups.invite(group, alice, bob);

		// when
		groups.accept(invitation, bob)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(group.toString()))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.memberCount").value(2));

		// then
		GroupId id = new GroupId(group);
		assertThat(events.stream(MemberJoined.class)
			.filter(e -> e.groupId().equals(id) && e.userId().equals(bob.userId()))).singleElement().satisfies(e -> {
				assertThat(e.role()).isEqualTo(GroupRole.MEMBER);
				assertThat(e.invitedBy()).isEqualTo(alice.userId());
			});
		groups.detail(group, bob).andExpect(status().isOk());
		groups.pending(bob).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void acceptingTwiceIs404TheSecondTimeAndThereIsOneMembership() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Twice");
		UUID invitation = groups.invite(group, alice, bob);
		groups.accept(invitation, bob).andExpect(status().isOk());

		// when / then
		groups.accept(invitation, bob)
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
		groups.detail(group, alice).andExpect(jsonPath("$.memberCount").value(2));
	}

	@Test
	void acceptingSomebodyElsesInvitationIs404AndNobodyJoins() throws Exception {
		// given Carol is a member of another group, which must not matter
		groups.createGroup(carol, "Carols own");
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		UUID invitation = groups.invite(group, alice, bob);

		// when / then
		groups.accept(invitation, carol)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
		groups.decline(invitation, carol).andExpect(status().isNotFound());
		groups.detail(group, alice).andExpect(jsonPath("$.memberCount").value(1));
		groups.pending(bob).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void acceptWithAnUnknownIdIs404() throws Exception {
		groups.accept(UUID.randomUUID(), bob)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
	}

	@Test
	void acceptAfterSevenDaysIs410AndNobodyJoins() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Late");
		UUID invitation = groups.invite(group, alice, bob);
		clock.advance(Duration.ofDays(7).plusSeconds(1));

		// when / then
		groups.accept(invitation, bob)
			.andExpect(status().isGone())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
		groups.detail(group, alice).andExpect(jsonPath("$.memberCount").value(1));
	}

	@Test
	void declineIs204PublishesInvitationDeclinedAndTheInvitationIsGone() throws Exception {
		// given
		UUID group = groups.createGroup(alice, "Mallorca 2027");
		UUID invitation = groups.invite(group, alice, bob);

		// when
		groups.decline(invitation, bob).andExpect(status().isNoContent());

		// then
		assertThat(events.stream(InvitationDeclined.class).filter(e -> e.groupId().value().equals(group)))
			.singleElement()
			.satisfies(e -> {
				assertThat(e.invitee()).isEqualTo(bob.userId());
				assertThat(e.invitedBy()).isEqualTo(alice.userId());
			});
		groups.pending(bob).andExpect(jsonPath("$.length()").value(0));
		groups.detail(group, alice).andExpect(jsonPath("$.memberCount").value(1));
		groups.accept(invitation, bob).andExpect(status().isNotFound());
	}

	@Test
	void decliningAfterSevenDaysIs410() throws Exception {
		UUID group = groups.createGroup(alice, "Late");
		UUID invitation = groups.invite(group, alice, bob);
		clock.advance(Duration.ofDays(7).plusSeconds(1));

		groups.decline(invitation, bob)
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
	}

	@Test
	void everyInvitationEndpointWithoutATokenIs401() throws Exception {
		UUID id = UUID.randomUUID();
		mvc.perform(get("/invitations")).andExpect(status().isUnauthorized());
		mvc.perform(post("/invitations/" + id + "/accept")).andExpect(status().isUnauthorized());
		mvc.perform(post("/invitations/" + id + "/decline")).andExpect(status().isUnauthorized());
		mvc.perform(post("/groups/" + id + "/invitations").contentType(APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
