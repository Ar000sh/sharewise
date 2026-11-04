package de.hsh.favs.sharewise.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The rules of the Group aggregate: creation, inviting, joining and declining. No Spring,
 * no database.
 */
class GroupTest {

	static final Instant NOW = MutableClock.START;

	static final Instant EXPIRY = NOW.plus(Duration.ofDays(7));

	UserId alice = AccountFixture.alice().userId();

	UserId bob = AccountFixture.bob().userId();

	UserId carol = AccountFixture.carol().userId();

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
	}

	@Test
	void theCreatorsMemberJoinedHasNoInviter() {
		// when
		Group.Creation creation = Group.createGroup(new GroupName("Trip"), Currency.getInstance("EUR"), alice, NOW);

		// then
		assertThat(creation.creatorJoined())
			.isEqualTo(new MemberJoined(creation.group().groupId(), alice, GroupRole.ADMIN, null, NOW));
	}

	@Test
	void aMemberInvitesAnAccountAndTheInvitationIsValidForSevenDays() {
		// when
		Group.Invited invited = group.inviteMember(bob, alice, NOW);

		// then
		Invitation invitation = invited.invitation();
		assertThat(invitation.invitee()).isEqualTo(bob);
		assertThat(invitation.statusAt(NOW)).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.expiresAt()).isEqualTo(EXPIRY);
		assertThat(invited.event()).isEqualTo(new MemberInvited(group.groupId(), invitation.id(), bob, alice, NOW));
	}

	@Test
	void requireActiveMemberPassesForAnActiveMember() {
		assertThatCode(() -> group.requireActiveMember(alice)).doesNotThrowAnyException();
	}

	@Test
	void requireActiveMemberRefusesANonMember() {
		assertThatThrownBy(() -> group.requireActiveMember(bob)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

	@Test
	void invitingACurrentMemberIsRefused() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when / then
		assertThatThrownBy(() -> trip.inviteMember(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_MEMBER));
		assertThatThrownBy(() -> trip.inviteMember(alice, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_MEMBER));
	}

	@Test
	void invitingSomebodyWithAnOpenInvitationIsRefused() {
		// given
		group.inviteMember(bob, alice, NOW);

		// when / then
		assertThatThrownBy(() -> group.inviteMember(bob, alice, NOW.plusSeconds(60))).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_INVITED));
		assertThat(group.pendingInvitationsFor(bob, NOW.plusSeconds(60))).hasSize(1);
	}

	@Test
	void aSecondInvitationIsPossibleOnceTheFirstIsDeclined() {
		// given
		Invitation first = group.inviteMember(bob, alice, NOW).invitation();
		group.declineInvitation(first.id(), bob, NOW);

		// when
		Group.Invited second = group.inviteMember(bob, alice, NOW.plusSeconds(60));

		// then
		assertThat(second.invitation().id()).isNotEqualTo(first.id());
		assertThat(group.pendingInvitationsFor(bob, NOW.plusSeconds(60))).containsExactly(second.invitation());
	}

	@Test
	void anInvitationIsExpiredAfterSevenDaysAndTheSamePersonCanBeInvitedAgain() {
		// given
		Invitation old = group.inviteMember(bob, alice, NOW).invitation();
		Instant later = EXPIRY.plusSeconds(1);

		// when
		Group.Invited again = group.inviteMember(bob, alice, later);

		// then
		assertThat(old.statusAt(later)).isEqualTo(InvitationStatus.EXPIRED);
		assertThat(again.invitation().statusAt(later)).isEqualTo(InvitationStatus.PENDING);
		assertThat(group.pendingInvitationsFor(bob, later)).containsExactly(again.invitation());
	}

	@Test
	void anInvitationIsStillOpenAtTheExactExpiryInstant() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, NOW).invitation();

		// when / then
		assertThat(invitation.statusAt(EXPIRY)).isEqualTo(InvitationStatus.PENDING);
		assertThat(group.pendingInvitationsFor(bob, EXPIRY)).containsExactly(invitation);
		assertThatThrownBy(() -> group.inviteMember(bob, alice, EXPIRY)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_INVITED));
		assertThat(group.acceptInvitation(invitation.id(), bob, EXPIRY).userId()).isEqualTo(bob);
	}

	@Test
	void acceptingAddsTheInviteeAsMemberAndReturnsMemberJoinedWithInvitedBy() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, NOW).invitation();

		// when
		MemberJoined joined = group.acceptInvitation(invitation.id(), bob, NOW.plusSeconds(5));

		// then
		assertThat(joined)
			.isEqualTo(new MemberJoined(group.groupId(), bob, GroupRole.MEMBER, alice, NOW.plusSeconds(5)));
		assertThat(group.hasActiveMember(bob)).isTrue();
		assertThat(invitation.statusAt(NOW.plusSeconds(5))).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(group.pendingInvitationsFor(bob, NOW.plusSeconds(5))).isEmpty();
	}

	@Test
	void somebodyElsesInvitationIsNotFoundAndNobodyJoins() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, NOW).invitation();

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation(invitation.id(), carol, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThatThrownBy(() -> group.declineInvitation(invitation.id(), carol, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThat(group.hasActiveMember(carol)).isFalse();
		assertThat(group.pendingInvitationsFor(bob, NOW)).containsExactly(invitation);
	}

	@Test
	void anUnknownInvitationIdIsNotFound() {
		assertThatThrownBy(() -> group.acceptInvitation(new InvitationId(java.util.UUID.randomUUID()), bob, NOW))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
	}

	@Test
	void acceptingAnExpiredInvitationIs410AndNobodyJoins() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, NOW).invitation();

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation(invitation.id(), bob, EXPIRY.plusSeconds(1)))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
		assertThat(group.hasActiveMember(bob)).isFalse();
		assertThat(group.memberCount()).isEqualTo(1);
	}

	@Test
	void acceptingTwiceIsNotFoundTheSecondTime() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, NOW).invitation();
		group.acceptInvitation(invitation.id(), bob, NOW);

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation(invitation.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThat(group.memberCount()).isEqualTo(2);
	}

	@Test
	void anAnsweredInvitationCannotBeAnsweredAgainWhateverTheAnswerWas() {
		// given one invitation declined and one accepted
		Invitation declined = group.inviteMember(bob, alice, NOW).invitation();
		group.declineInvitation(declined.id(), bob, NOW);
		Invitation accepted = group.inviteMember(bob, alice, NOW).invitation();
		group.acceptInvitation(accepted.id(), bob, NOW);

		// when / then: all four combinations are "not found", nothing changes
		assertThatThrownBy(() -> group.acceptInvitation(declined.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThatThrownBy(() -> group.declineInvitation(declined.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThatThrownBy(() -> group.declineInvitation(accepted.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThatThrownBy(() -> group.acceptInvitation(accepted.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThat(declined.status()).isEqualTo(InvitationStatus.DECLINED);
		assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(group.memberCount()).isEqualTo(2);
	}

	@Test
	void decliningReturnsInvitationDeclinedAndNobodyJoins() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, NOW).invitation();

		// when
		InvitationDeclined declined = group.declineInvitation(invitation.id(), bob, NOW.plusSeconds(5));

		// then
		assertThat(declined)
			.isEqualTo(new InvitationDeclined(group.groupId(), invitation.id(), bob, alice, NOW.plusSeconds(5)));
		assertThat(invitation.statusAt(NOW.plusSeconds(5))).isEqualTo(InvitationStatus.DECLINED);
		assertThat(group.hasActiveMember(bob)).isFalse();
		assertThat(group.pendingInvitationsFor(bob, NOW.plusSeconds(5))).isEmpty();
	}

	@Test
	void decliningAnExpiredInvitationIsExpired() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, NOW).invitation();

		// when / then
		assertThatThrownBy(() -> group.declineInvitation(invitation.id(), bob, EXPIRY.plusSeconds(1)))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void pendingInvitationsAreOnlyTheOpenOnesOfThatUser() {
		// given
		Invitation forBob = group.inviteMember(bob, alice, NOW).invitation();
		group.inviteMember(carol, alice, NOW);

		// when / then
		assertThat(group.pendingInvitationsFor(bob, NOW)).containsExactly(forBob);
		assertThat(group.pendingInvitationsFor(alice, NOW)).isEmpty();
	}

	@Test
	void theMembershipViewListsActiveMembersAndAdminsAndSaysWhetherTheGroupIsOpen() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when
		var view = trip.membershipView();

		// then
		assertThat(view.isOpen()).isTrue();
		assertThat(view.activeMembers()).containsExactlyInAnyOrder(alice, bob);
		assertThat(view.admins()).containsExactly(alice);
		assertThat(view.isActiveMember(bob)).isTrue();
		assertThat(view.isAdmin(bob)).isFalse();
	}

	@Test
	void aFormerMemberIsNeitherActiveNorCountedNorInTheMembershipView() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		Membership bobsMembership = trip.activeMemberships()
			.stream()
			.filter(m -> m.userId().equals(bob))
			.findFirst()
			.orElseThrow();
		ReflectionTestUtils.setField(bobsMembership, "status", MembershipStatus.FORMER_MEMBER);

		// when / then
		assertThat(trip.activeMemberships()).extracting(Membership::userId).containsExactly(alice);
		assertThat(trip.hasActiveMember(bob)).isFalse();
		assertThat(trip.memberCount()).isEqualTo(1);
		assertThat(trip.membershipView().activeMembers()).containsExactly(alice);
		assertThatThrownBy(() -> trip.requireActiveMember(bob)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

}
