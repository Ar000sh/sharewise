package de.hsh.favs.sharewise.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupArchived;
import de.hsh.favs.sharewise.group.api.GroupClosedForSettlement;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupReopened;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.MemberLeft;
import de.hsh.favs.sharewise.group.api.MemberPromoted;
import de.hsh.favs.sharewise.group.api.MemberRemoved;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The rules of the Group aggregate: creation, inviting, joining, declining, leaving and
 * removal. No Spring, no database.
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
	void anAdminInvitesAnAccountAndTheInvitationIsValidForSevenDays() {
		// when
		Group.Invited invited = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW);

		// then
		Invitation invitation = invited.invitation();
		assertThat(invitation.invitee()).isEqualTo(bob);
		assertThat(invitation.statusAt(NOW)).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.expiresAt()).isEqualTo(EXPIRY);
		assertThat(invited.event())
			.isEqualTo(new MemberInvited(group.groupId(), invitation.id(), bob, alice, GroupRole.MEMBER, NOW));
	}

	@Test
	void theRoleOfTheInvitationIsWhatTheInviteeBecomesOnAccepting() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.ADMIN, NOW).invitation();

		// when
		MemberJoined joined = group.acceptInvitation(invitation.id(), bob, NOW);

		// then
		assertThat(joined.role()).isEqualTo(GroupRole.ADMIN);
		assertThat(group.membershipView().admins()).containsExactlyInAnyOrder(alice, bob);
	}

	@Test
	void requireAdminPassesForAnAdmin() {
		assertThatCode(() -> group.requireAdmin(alice)).doesNotThrowAnyException();
	}

	@Test
	void requireAdminRefusesANonMemberWithNotAMember() {
		assertThatThrownBy(() -> group.requireAdmin(bob)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

	@Test
	void aMemberWhoIsNotAdminCannotInviteAndNoInvitationIsAdded() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when / then
		assertThatThrownBy(() -> trip.requireAdmin(bob)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_AN_ADMIN));
		assertThat(trip.pendingInvitationsFor(carol, NOW)).isEmpty();
	}

	@Test
	void promotingAMemberMakesThemAdminAndReturnsMemberPromoted() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when
		MemberPromoted promoted = trip.promoteToAdmin(bob, alice, NOW.plusSeconds(5));

		// then
		assertThat(promoted).isEqualTo(new MemberPromoted(trip.groupId(), bob, alice, NOW.plusSeconds(5)));
		assertThat(trip.membershipView().admins()).containsExactlyInAnyOrder(alice, bob);
	}

	@Test
	void aPromotedMemberMayInvite() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.promoteToAdmin(bob, alice, NOW);

		// when / then
		assertThatCode(() -> trip.requireAdmin(bob)).doesNotThrowAnyException();
	}

	@Test
	void aMemberWhoIsNotAdminCannotPromoteAndNothingChanges() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).withMember(carol).build();

		// when / then
		assertThatThrownBy(() -> trip.promoteToAdmin(carol, bob, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_AN_ADMIN));
		assertThat(trip.membershipView().admins()).containsExactly(alice);
	}

	@Test
	void aNonMemberCannotPromote() {
		assertThatThrownBy(() -> group.promoteToAdmin(alice, bob, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

	@Test
	void promotingSomebodyWhoIsNotAnActiveMemberIsMemberNotFound() {
		// given bob has only an open invitation, carol never was invited
		group.inviteMember(bob, alice, GroupRole.MEMBER, NOW);

		// when / then
		assertThatThrownBy(() -> group.promoteToAdmin(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
		assertThatThrownBy(() -> group.promoteToAdmin(carol, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
		assertThat(group.membershipView().admins()).containsExactly(alice);
	}

	@Test
	void promotingAnAdminOrOneselfIsAlreadyAdmin() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.promoteToAdmin(bob, alice, NOW);

		// when / then
		assertThatThrownBy(() -> trip.promoteToAdmin(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_ADMIN));
		assertThatThrownBy(() -> trip.promoteToAdmin(alice, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_ADMIN));
	}

	@Test
	void invitingACurrentMemberIsRefused() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when / then
		assertThatThrownBy(() -> trip.inviteMember(bob, alice, GroupRole.MEMBER, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_MEMBER));
		assertThatThrownBy(() -> trip.inviteMember(alice, alice, GroupRole.MEMBER, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_MEMBER));
	}

	@Test
	void invitingSomebodyWithAnOpenInvitationIsRefused() {
		// given
		group.inviteMember(bob, alice, GroupRole.MEMBER, NOW);

		// when / then
		assertThatThrownBy(() -> group.inviteMember(bob, alice, GroupRole.MEMBER, NOW.plusSeconds(60)))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_INVITED));
		assertThat(group.pendingInvitationsFor(bob, NOW.plusSeconds(60))).hasSize(1);
	}

	@Test
	void aSecondInvitationIsPossibleOnceTheFirstIsDeclined() {
		// given
		Invitation first = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
		group.declineInvitation(first.id(), bob, NOW);

		// when
		Group.Invited second = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW.plusSeconds(60));

		// then
		assertThat(second.invitation().id()).isNotEqualTo(first.id());
		assertThat(group.pendingInvitationsFor(bob, NOW.plusSeconds(60))).containsExactly(second.invitation());
	}

	@Test
	void anInvitationIsExpiredAfterSevenDaysAndTheSamePersonCanBeInvitedAgain() {
		// given
		Invitation old = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
		Instant later = EXPIRY.plusSeconds(1);

		// when
		Group.Invited again = group.inviteMember(bob, alice, GroupRole.MEMBER, later);

		// then
		assertThat(old.statusAt(later)).isEqualTo(InvitationStatus.EXPIRED);
		assertThat(again.invitation().statusAt(later)).isEqualTo(InvitationStatus.PENDING);
		assertThat(group.pendingInvitationsFor(bob, later)).containsExactly(again.invitation());
	}

	@Test
	void anInvitationIsStillOpenAtTheExactExpiryInstant() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();

		// when / then
		assertThat(invitation.statusAt(EXPIRY)).isEqualTo(InvitationStatus.PENDING);
		assertThat(group.pendingInvitationsFor(bob, EXPIRY)).containsExactly(invitation);
		assertThatThrownBy(() -> group.inviteMember(bob, alice, GroupRole.MEMBER, EXPIRY)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_INVITED));
		assertThat(group.acceptInvitation(invitation.id(), bob, EXPIRY).userId()).isEqualTo(bob);
	}

	@Test
	void acceptingAddsTheInviteeAsMemberAndReturnsMemberJoinedWithInvitedBy() {
		// given
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();

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
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();

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
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();

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
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
		group.acceptInvitation(invitation.id(), bob, NOW);

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation(invitation.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		assertThat(group.memberCount()).isEqualTo(2);
	}

	@Test
	void anAnsweredInvitationCannotBeAnsweredAgainWhateverTheAnswerWas() {
		// given one invitation declined and one accepted
		Invitation declined = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
		group.declineInvitation(declined.id(), bob, NOW);
		Invitation accepted = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
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
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();

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
		Invitation invitation = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();

		// when / then
		assertThatThrownBy(() -> group.declineInvitation(invitation.id(), bob, EXPIRY.plusSeconds(1)))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
		assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void pendingInvitationsAreOnlyTheOpenOnesOfThatUser() {
		// given
		Invitation forBob = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
		group.inviteMember(carol, alice, GroupRole.MEMBER, NOW);

		// when / then
		assertThat(group.pendingInvitationsFor(bob, NOW)).containsExactly(forBob);
		assertThat(group.pendingInvitationsFor(alice, NOW)).isEmpty();
	}

	@Test
	void expirePendingInvitationsFlipsOnlyPastExpiryPendingInvitations() {
		// given: one invitation at NOW, one accepted, one newer invitation still valid
		UserId dave = new UserId(UUID.randomUUID());
		Invitation expired = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
		Invitation accepted = group.inviteMember(carol, alice, GroupRole.MEMBER, NOW).invitation();
		group.acceptInvitation(accepted.id(), carol, NOW.plusSeconds(10));
		Invitation stillOpen = group.inviteMember(dave, alice, GroupRole.MEMBER, NOW.plusSeconds(60)).invitation();

		// when: at exact expiry nothing flips yet
		assertThat(group.expirePendingInvitations(EXPIRY)).isZero();
		assertThat(expired.status()).isEqualTo(InvitationStatus.PENDING);

		// when: one second past expiry only the expired pending one flips
		int flipped = group.expirePendingInvitations(EXPIRY.plusSeconds(1));

		// then
		assertThat(flipped).isEqualTo(1);
		assertThat(expired.status()).isEqualTo(InvitationStatus.EXPIRED);
		assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(stillOpen.status()).isEqualTo(InvitationStatus.PENDING);
		// idempotent on a second sweep
		assertThat(group.expirePendingInvitations(EXPIRY.plusSeconds(2))).isZero();
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
	void updateBalanceStatusSetsTheMemberFlagAndIsIdempotent() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when the same status arrives twice
		trip.updateBalanceStatus(bob, false, NOW);
		trip.updateBalanceStatus(bob, false, NOW);

		// then only Bob's flag changed
		assertThat(hasZeroBalance(trip, bob)).isFalse();
		assertThat(hasZeroBalance(trip, alice)).isTrue();
	}

	@Test
	void updateBalanceStatusIgnoresAUserWhoIsNotAMember() {
		// given
		UserId carol = AccountFixture.carol().userId();

		// when
		group.updateBalanceStatus(carol, false, NOW);

		// then no membership appeared and nobody's flag changed
		assertThat(group.memberships()).noneMatch(m -> m.userId().equals(carol));
		assertThat(group.memberships()).allMatch(Membership::hasZeroBalance);
	}

	@Test
	void anOlderStatusEventDoesNotOverrideANewerOne() {
		// given Bob went non-zero at a later instant
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.updateBalanceStatus(bob, false, NOW.plusSeconds(10));

		// when the older "zero" event arrives late
		trip.updateBalanceStatus(bob, true, NOW);

		// then the newer status stays
		assertThat(hasZeroBalance(trip, bob)).isFalse();
	}

	@Test
	void eventsWithTheSameTimestampApply() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.updateBalanceStatus(bob, false, NOW);

		// when a second event carries the same instant (a frozen clock)
		trip.updateBalanceStatus(bob, true, NOW);

		// then it is applied
		assertThat(hasZeroBalance(trip, bob)).isTrue();
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
		assertThatThrownBy(() -> trip.requireAdmin(bob)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		assertThatThrownBy(() -> trip.promoteToAdmin(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
	}

	@Test
	void aMemberAtZeroBalanceLeavesBecomesAFormerMemberAndMemberLeftIsReturned() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when
		MemberLeft left = trip.leaveGroup(bob, NOW);

		// then
		assertThat(left).isEqualTo(new MemberLeft(trip.groupId(), bob, NOW));
		assertThat(trip.hasActiveMember(bob)).isFalse();
		assertThat(trip.formerMemberships()).extracting(Membership::userId).containsExactly(bob);
		assertThat(trip.membershipView().activeMembers()).containsExactly(alice);
	}

	@Test
	void aMemberWithABalanceCannotLeaveAndStaysActive() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.updateBalanceStatus(bob, false, NOW);

		// when / then
		assertThatThrownBy(() -> trip.leaveGroup(bob, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BALANCE_NOT_ZERO));
		assertThat(trip.hasActiveMember(bob)).isTrue();
	}

	@Test
	void theLastAdminCannotLeaveWhileOtherMembersRemain() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when / then
		assertThatThrownBy(() -> trip.leaveGroup(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.LAST_ADMIN));
		assertThat(trip.hasActiveMember(alice)).isTrue();
	}

	@Test
	void anAdminMayLeaveOnceAnotherMemberIsAdmin() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.promoteToAdmin(bob, alice, NOW);

		// when
		trip.leaveGroup(alice, NOW);

		// then
		assertThat(trip.membershipView().admins()).containsExactly(bob);
	}

	@Test
	void theOnlyMemberMayLeave() {
		// when
		group.leaveGroup(alice, NOW);

		// then
		assertThat(group.activeMemberships()).isEmpty();
	}

	@Test
	void somebodyWhoIsNotAMemberCannotLeave() {
		// when / then
		assertThatThrownBy(() -> group.leaveGroup(bob, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

	@Test
	void anAdminRemovesAMemberAtZeroBalanceAndMemberRemovedIsReturned() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when
		MemberRemoved removed = trip.removeMember(bob, alice, NOW);

		// then
		assertThat(removed).isEqualTo(new MemberRemoved(trip.groupId(), bob, alice, NOW));
		assertThat(trip.hasActiveMember(bob)).isFalse();
		assertThat(trip.formerMemberships()).extracting(Membership::userId).containsExactly(bob);
	}

	@Test
	void aMemberWithABalanceCannotBeRemovedAndStays() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.updateBalanceStatus(bob, false, NOW);

		// when / then
		assertThatThrownBy(() -> trip.removeMember(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BALANCE_NOT_ZERO));
		assertThat(trip.hasActiveMember(bob)).isTrue();
	}

	@Test
	void anAdminCannotRemoveThemselves() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when / then
		assertThatThrownBy(() -> trip.removeMember(alice, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.CANNOT_REMOVE_YOURSELF));
		assertThat(trip.hasActiveMember(alice)).isTrue();
	}

	@Test
	void aMemberWhoIsNotAdminCannotRemoveAnybody() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).withMember(carol).build();

		// when / then
		assertThatThrownBy(() -> trip.removeMember(carol, bob, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_AN_ADMIN));
		assertThat(trip.hasActiveMember(carol)).isTrue();
	}

	@Test
	void removingSomebodyWhoIsNotAnActiveMemberIsMemberNotFound() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.leaveGroup(bob, NOW);

		// when / then
		assertThatThrownBy(() -> trip.removeMember(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
	}

	@Test
	void anAdminMayRemoveAnotherAdmin() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.promoteToAdmin(bob, alice, NOW);

		// when
		trip.removeMember(bob, alice, NOW);

		// then
		assertThat(trip.membershipView().admins()).containsExactly(alice);
	}

	@Test
	void aFormerMemberWhoAcceptsANewInvitationGetsTheSameMembershipBackWithTheNewRole() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.leaveGroup(bob, NOW);
		Instant later = NOW.plus(Duration.ofDays(2));
		Invitation again = trip.inviteMember(bob, alice, GroupRole.ADMIN, later).invitation();

		// when
		trip.acceptInvitation(again.id(), bob, later);

		// then one membership row for Bob, active again, with the role and time of the
		// new invitation
		assertThat(trip.memberships()).filteredOn(m -> m.userId().equals(bob)).singleElement().satisfies(m -> {
			assertThat(m.status()).isEqualTo(MembershipStatus.ACTIVE);
			assertThat(m.role()).isEqualTo(GroupRole.ADMIN);
			assertThat(m.joinedAt()).isEqualTo(later);
		});
		assertThat(trip.formerMemberships()).isEmpty();
	}

	@Test
	void anAdminClosesAnOpenGroupForSettlementAndExpensesAreFrozen() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.updateBalanceStatus(bob, false, NOW);

		// when closing does not need balances of 0 (L4)
		GroupClosedForSettlement closed = trip.closeForSettlement(alice, NOW);

		// then
		assertThat(closed).isEqualTo(new GroupClosedForSettlement(trip.groupId(), alice, NOW));
		assertThat(trip.status()).isEqualTo(GroupStatus.CLOSED_FOR_SETTLEMENT);
		assertThat(trip.membershipView().isOpen()).isFalse();
	}

	@Test
	void aMemberWhoIsNotAdminCannotCloseAndTheGroupStaysOpen() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();

		// when / then
		assertThatThrownBy(() -> trip.closeForSettlement(bob, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_AN_ADMIN));
		assertThat(trip.status()).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void closingAGroupThatIsAlreadyClosedIsGroupNotOpen() {
		// given
		group.closeForSettlement(alice, NOW);

		// when / then
		assertThatThrownBy(() -> group.closeForSettlement(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_OPEN));
	}

	@Test
	void anAdminReopensAClosedGroupAndGroupReopenedIsReturned() {
		// given
		group.closeForSettlement(alice, NOW);

		// when
		GroupReopened reopened = group.reopenGroup(alice, NOW);

		// then
		assertThat(reopened).isEqualTo(new GroupReopened(group.groupId(), alice, NOW));
		assertThat(group.status()).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void reopeningAnOpenGroupIsGroupNotClosed() {
		// when / then
		assertThatThrownBy(() -> group.reopenGroup(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_CLOSED));
	}

	@Test
	void anAdminArchivesAClosedGroupWhoseBalancesAreAllZero() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.closeForSettlement(alice, NOW);

		// when
		GroupArchived archived = trip.archiveGroup(alice, NOW);

		// then
		assertThat(archived).isEqualTo(new GroupArchived(trip.groupId(), alice, NOW));
		assertThat(trip.status()).isEqualTo(GroupStatus.ARCHIVED);
	}

	@Test
	void archivingWhileABalanceIsNotZeroIsRefusedAndTheGroupStaysClosed() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.updateBalanceStatus(bob, false, NOW);
		trip.closeForSettlement(alice, NOW);

		// when / then
		assertThatThrownBy(() -> trip.archiveGroup(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BALANCE_NOT_ZERO));
		assertThat(trip.status()).isEqualTo(GroupStatus.CLOSED_FOR_SETTLEMENT);
	}

	@Test
	void archivingAnOpenGroupIsGroupNotClosed() {
		// when / then
		assertThatThrownBy(() -> group.archiveGroup(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_CLOSED));
		assertThat(group.status()).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void anArchivedGroupRefusesEveryChange() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		trip.closeForSettlement(alice, NOW);
		trip.archiveGroup(alice, NOW);

		// when / then (L6)
		assertThatThrownBy(() -> trip.closeForSettlement(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_ARCHIVED));
		assertThatThrownBy(() -> trip.reopenGroup(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_ARCHIVED));
		assertThatThrownBy(() -> trip.archiveGroup(alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_ARCHIVED));
		assertThatThrownBy(() -> trip.promoteToAdmin(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_ARCHIVED));
		assertThatThrownBy(() -> trip.leaveGroup(bob, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_ARCHIVED));
		assertThatThrownBy(() -> trip.removeMember(bob, alice, NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_ARCHIVED));
		assertThat(trip.status()).isEqualTo(GroupStatus.ARCHIVED);
		assertThat(trip.membershipView().activeMembers()).containsExactlyInAnyOrder(alice, bob);
	}

	@Test
	void nobodyCanBeInvitedToOrJoinAGroupThatIsClosed() {
		// given Bob was invited while the group was open
		Invitation open = group.inviteMember(bob, alice, GroupRole.MEMBER, NOW).invitation();
		group.closeForSettlement(alice, NOW);

		// when / then (J3)
		assertThatThrownBy(() -> group.inviteMember(carol, alice, GroupRole.MEMBER, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_OPEN));
		assertThatThrownBy(() -> group.acceptInvitation(open.id(), bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_OPEN));
		assertThat(group.hasActiveMember(bob)).isFalse();
		assertThat(open.status()).isEqualTo(InvitationStatus.PENDING);
	}

	@Test
	void whenAllBalancesAreSettledAClosedGroupIsArchivedWithoutAnActor() {
		// given
		group.closeForSettlement(alice, NOW);

		// when (L5)
		var archived = group.archiveWhenAllBalancesSettled(NOW);

		// then
		assertThat(archived).contains(new GroupArchived(group.groupId(), null, NOW));
		assertThat(group.status()).isEqualTo(GroupStatus.ARCHIVED);
	}

	@Test
	void whenAllBalancesAreSettledAnOpenGroupStaysOpen() {
		// when (design 2026-11-28-group-lifecycle G2)
		var archived = group.archiveWhenAllBalancesSettled(NOW);

		// then
		assertThat(archived).isEmpty();
		assertThat(group.status()).isEqualTo(GroupStatus.OPEN);
	}

	private static boolean hasZeroBalance(Group group, UserId user) {
		return group.memberships()
			.stream()
			.filter(m -> m.userId().equals(user))
			.findFirst()
			.orElseThrow()
			.hasZeroBalance();
	}

}
