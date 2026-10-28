package de.hsh.favs.sharewise.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The rules of the Group aggregate: creation, inviting and joining. No Spring, no
 * database.
 */
class GroupTest {

	static final Instant NOW = MutableClock.START;

	UserId alice = AccountFixture.alice().userId();

	UserId bob = AccountFixture.bob().userId();

	Group group = new GroupFixture().withCreator(alice).build();

	@Test
	void creatingAGroupMakesItOpenWithTheCreatorAsAdminAndReturnsBothEvents() {
		// when
		Group.Creation creation = Group.createGroup(new GroupName("Trip"), Currency.getInstance("EUR"), alice, NOW);

		// then
		Group created = creation.group();
		assertThat(created.name()).isEqualTo(new GroupName("Trip"));
		assertThat(created.status()).isEqualTo(GroupStatus.OPEN);
		assertThat(created.currency()).isEqualTo(Currency.getInstance("EUR"));
		assertThat(created.memberships()).singleElement().satisfies(m -> {
			assertThat(m.userId()).isEqualTo(alice);
			assertThat(m.role()).isEqualTo(GroupRole.ADMIN);
			assertThat(m.status()).isEqualTo(MembershipStatus.ACTIVE);
			assertThat(m.hasZeroBalance()).isTrue();
		});
		assertThat(creation.groupCreated()).isEqualTo(new GroupCreated(created.groupId(), alice, NOW));
		assertThat(creation.creatorJoined())
			.isEqualTo(new MemberJoined(created.groupId(), alice, GroupRole.ADMIN, NOW));
	}

	@Test
	void aMemberInvitesAndTheInvitationIsValidForSevenDays() {
		// when
		Group.Invited invited = group.inviteMember(null, alice, "abc", NOW);

		// then
		assertThat(invited.invitation().token()).isEqualTo("abc");
		assertThat(invited.invitation().status()).isEqualTo(InvitationStatus.PENDING);
		assertThat(invited.invitation().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(invited.event())
			.isEqualTo(new MemberInvited(group.groupId(), invited.invitation().id(), null, alice, NOW));
	}

	@Test
	void anInvitationCanBeMeantForAnEmailAddress() {
		// when
		Group.Invited invited = group.inviteMember(new Email("Bob@Example.com"), alice, "abc", NOW);

		// then
		assertThat(invited.invitation().email()).isEqualTo(new Email("bob@example.com"));
		assertThat(invited.event().email()).isEqualTo(new Email("bob@example.com"));
	}

	@Test
	void aNonMemberCannotInvite() {
		assertThatThrownBy(() -> group.inviteMember(null, bob, "abc", NOW))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

	@Test
	void acceptingAddsTheUserAsMemberAndReturnsMemberJoined() {
		// given
		Invitation invitation = group.inviteMember(null, alice, "abc", NOW).invitation();

		// when
		MemberJoined joined = group.acceptInvitation(invitation.id(), bob, NOW.plusSeconds(60));

		// then
		assertThat(joined).isEqualTo(new MemberJoined(group.groupId(), bob, GroupRole.MEMBER, NOW.plusSeconds(60)));
		assertThat(group.memberCount()).isEqualTo(2);
		assertThat(group.memberships()).filteredOn(m -> m.userId().equals(bob)).singleElement().satisfies(m -> {
			assertThat(m.role()).isEqualTo(GroupRole.MEMBER);
			assertThat(m.joinedAt()).isEqualTo(NOW.plusSeconds(60));
		});
	}

	@Test
	void aLinkInvitationAdmitsSeveralPeople() {
		// given
		Invitation invitation = group.inviteMember(null, alice, "abc", NOW).invitation();

		// when
		group.acceptInvitation(invitation.id(), bob, NOW);
		group.acceptInvitation(invitation.id(), AccountFixture.carol().userId(), NOW);

		// then
		assertThat(group.memberCount()).isEqualTo(3);
		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void anInvitationToAnEmailAddressIsUsedUpByTheFirstAcceptance() {
		// given
		Invitation invitation = group.inviteMember(new Email("bob@example.com"), alice, "abc", NOW).invitation();
		group.acceptInvitation(invitation.id(), bob, NOW);

		// when / then
		assertThat(invitation.status()).isEqualTo(InvitationStatus.ACCEPTED);
		assertThatThrownBy(() -> group.acceptInvitation(invitation.id(), AccountFixture.carol().userId(), NOW))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
	}

	@Test
	void anUnknownTokenIsNotFound() {
		group.inviteMember(null, alice, "abc", NOW);

		assertThatThrownBy(() -> group.invitationWithToken("other")).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
	}

	@Test
	void anInvitationOfAnotherGroupCannotBeAccepted() {
		// given
		Group other = new GroupFixture().withId(UUID.randomUUID()).build();
		Invitation foreign = other.inviteMember(null, alice, "other-token", NOW).invitation();

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation(foreign.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThat(group.memberCount()).isEqualTo(1);
	}

	@Test
	void anExpiredInvitationIsRefusedAndNobodyJoins() {
		// given
		Invitation invitation = group.inviteMember(null, alice, "abc", NOW).invitation();

		// when / then: one millisecond after the 7 days
		assertThatThrownBy(
				() -> group.acceptInvitation(invitation.id(), bob, NOW.plus(Duration.ofDays(7)).plusMillis(1)))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
		assertThat(group.memberCount()).isEqualTo(1);
	}

	@Test
	void anInvitationStillWorksAtTheExactExpiryInstant() {
		// given
		Invitation invitation = group.inviteMember(null, alice, "abc", NOW).invitation();

		// when
		group.acceptInvitation(invitation.id(), bob, NOW.plus(Duration.ofDays(7)));

		// then
		assertThat(group.hasActiveMember(bob)).isTrue();
	}

	@Test
	void aMemberCannotJoinAgain() {
		// given
		Invitation invitation = group.inviteMember(null, alice, "abc", NOW).invitation();

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation(invitation.id(), alice, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_MEMBER));
		assertThat(group.memberCount()).isEqualTo(1);
	}

	@Test
	void expiryIsCheckedBeforeMembership() {
		// given an expired invitation used by someone who is already a member
		Invitation invitation = group.inviteMember(null, alice, "abc", NOW).invitation();

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation(invitation.id(), alice, NOW.plus(Duration.ofDays(8))))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
	}

}
