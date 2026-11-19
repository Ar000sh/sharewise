package de.hsh.favs.sharewise.group.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.MemberPromoted;
import de.hsh.favs.sharewise.group.domain.Group;
import de.hsh.favs.sharewise.group.domain.GroupFixture;
import de.hsh.favs.sharewise.group.domain.GroupName;
import de.hsh.favs.sharewise.group.domain.Invitation;
import de.hsh.favs.sharewise.group.persistence.GroupRepository;
import de.hsh.favs.sharewise.group.persistence.PendingInvitationRow;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Orchestration only: what is loaded, saved and published, and that a refused call leaves
 * nothing behind.
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

	@Mock
	GroupRepository groups;

	@Mock
	GroupMembership membership;

	@Mock
	UserProfiles profiles;

	@Mock
	ApplicationEventPublisher events;

	GroupService service;

	UserId alice = AccountFixture.alice().userId();

	UserId bob = AccountFixture.bob().userId();

	@BeforeEach
	void setUp() {
		service = new GroupService(groups, membership, profiles, events,
				Clock.fixed(MutableClock.START, ZoneOffset.UTC));
	}

	@Test
	void creatingSavesAnOpenGroupAndPublishesGroupCreatedThenMemberJoinedAsAdmin() {
		// when
		var view = service.createGroup(new GroupName("Mallorca 2027"), alice);

		// then
		ArgumentCaptor<Group> saved = ArgumentCaptor.forClass(Group.class);
		var order = inOrder(groups, events);
		order.verify(groups).save(saved.capture());
		GroupId id = saved.getValue().groupId();
		order.verify(events).publishEvent(new GroupCreated(id, alice, MutableClock.START));
		order.verify(events).publishEvent(new MemberJoined(id, alice, GroupRole.ADMIN, null, MutableClock.START));
		assertThat(view.status()).isEqualTo(GroupStatus.OPEN);
		assertThat(view.memberCount()).isEqualTo(1);
	}

	@Test
	void creatingPublishesNothingWhenStoringFails() {
		// given
		when(groups.save(any())).thenThrow(new IllegalStateException("db down"));

		// when / then
		assertThatThrownBy(() -> service.createGroup(new GroupName("x"), alice))
			.isInstanceOf(IllegalStateException.class);
		verifyNoInteractions(events);
	}

	@Test
	void listingReturnsTheGroupsOfTheCaller() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		when(groups.findAllByActiveMember(alice.value())).thenReturn(List.of(trip));

		// when / then
		assertThat(service.listGroups(alice)).singleElement()
			.satisfies(g -> assertThat(g.id()).isEqualTo(trip.groupId()));
	}

	@Test
	void gettingAGroupChecksMembershipFirstThenReturnsIt() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		when(groups.findById(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when
		var view = service.getGroup(trip.groupId(), bob);

		// then
		var order = inOrder(membership, groups);
		order.verify(membership).requireMember(trip.groupId(), bob);
		order.verify(groups).findById(trip.groupId().value());
		assertThat(view.memberCount()).isEqualTo(2);
	}

	@Test
	void gettingAGroupAsANonMemberFailsBeforeTheGroupIsRead() {
		// given
		GroupId id = new GroupId(UUID.randomUUID());
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(membership).requireMember(id, bob);

		// when / then
		assertThatThrownBy(() -> service.getGroup(id, bob)).isInstanceOf(DomainException.class);
		verify(groups, never()).findById(any());
	}

	@Test
	void inviteResolvesTheIdentifierLocksTheGroupAndPublishesMemberInvited() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));
		when(profiles.findByIdentifier("bob")).thenReturn(Optional.of(AccountFixture.bob().profile()));

		// when
		var invitation = service.inviteMember(trip.groupId(), alice, "bob", GroupRole.ADMIN);

		// then
		assertThat(invitation.invitee()).isEqualTo(AccountFixture.bob().profile());
		assertThat(invitation.expiresAt()).isEqualTo(MutableClock.START.plus(Duration.ofDays(7)));
		verify(groups).findByIdForUpdate(trip.groupId().value());
		ArgumentCaptor<MemberInvited> published = ArgumentCaptor.forClass(MemberInvited.class);
		verify(events).publishEvent(published.capture());
		assertThat(published.getValue().invitee()).isEqualTo(bob);
		assertThat(published.getValue().invitedBy()).isEqualTo(alice);
		assertThat(published.getValue().role()).isEqualTo(GroupRole.ADMIN);
		assertThat(published.getValue().groupId()).isEqualTo(trip.groupId());
		assertThat(published.getValue().invitationId()).isEqualTo(invitation.id());
	}

	@Test
	void inviteForAnUnknownIdentifierIsAccountNotFoundAndNothingIsPublished() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));
		when(profiles.findByIdentifier("nobody")).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.inviteMember(trip.groupId(), alice, "nobody", GroupRole.MEMBER))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
		verifyNoInteractions(events);
	}

	@Test
	void invitingToAnUnknownGroupIsGroupNotFound() {
		// given
		GroupId unknown = new GroupId(UUID.randomUUID());
		when(groups.findByIdForUpdate(unknown.value())).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.inviteMember(unknown, alice, "bob", GroupRole.MEMBER)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_FOUND));
		verifyNoInteractions(events, profiles);
	}

	@Test
	void aNonMemberCannotInviteAndLearnsNothingAboutAccounts() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when / then
		assertThatThrownBy(() -> service.inviteMember(trip.groupId(), bob, "carol", GroupRole.MEMBER))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		verifyNoInteractions(events, profiles);
	}

	@Test
	void aMemberWhoIsNotAdminCannotInviteAndNothingIsLookedUpOrPublished() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when / then
		assertThatThrownBy(() -> service.inviteMember(trip.groupId(), bob, "carol", GroupRole.MEMBER))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_AN_ADMIN));
		verifyNoInteractions(events, profiles);
	}

	@Test
	void promoteLocksTheGroupAndPublishesMemberPromoted() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when
		var promoted = service.promoteToAdmin(trip.groupId(), alice, bob);

		// then
		assertThat(promoted.role()).isEqualTo(GroupRole.ADMIN);
		verify(events).publishEvent(new MemberPromoted(trip.groupId(), bob, alice, MutableClock.START));
	}

	@Test
	void aRefusedPromotionPublishesNothing() {
		// given
		Group trip = new GroupFixture().withCreator(alice).withMember(bob).build();
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when / then
		assertThatThrownBy(() -> service.promoteToAdmin(trip.groupId(), bob, alice))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_AN_ADMIN));
		verifyNoInteractions(events);
	}

	@Test
	void acceptLocksSavesAndPublishesMemberJoinedWithInvitedBy() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		Invitation invitation = trip.inviteMember(bob, alice, GroupRole.MEMBER, MutableClock.START).invitation();
		when(groups.findIdByInvitationId(invitation.id().value())).thenReturn(Optional.of(trip.groupId().value()));
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when
		var view = service.acceptInvitation(invitation.id(), bob);

		// then
		verify(groups).saveAndFlush(trip);
		verify(events).publishEvent(new MemberJoined(trip.groupId(), bob, GroupRole.MEMBER, alice, MutableClock.START));
		assertThat(view.memberCount()).isEqualTo(2);
	}

	@Test
	void acceptingAnUnknownInvitationIsInvitationNotFound() {
		// given
		InvitationId unknown = new InvitationId(UUID.randomUUID());
		when(groups.findIdByInvitationId(unknown.value())).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.acceptInvitation(unknown, bob)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		verify(groups, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

	@Test
	void aRefusedAcceptSavesAndPublishesNothing() {
		// given an invitation meant for bob, answered by somebody else
		Group trip = new GroupFixture().withCreator(alice).build();
		Invitation invitation = trip.inviteMember(bob, alice, GroupRole.MEMBER, MutableClock.START).invitation();
		when(groups.findIdByInvitationId(invitation.id().value())).thenReturn(Optional.of(trip.groupId().value()));
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));
		UserId carol = AccountFixture.carol().userId();

		// when / then
		assertThatThrownBy(() -> service.acceptInvitation(invitation.id(), carol)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		verify(groups, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

	@Test
	void declineSavesAndPublishesInvitationDeclined() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		Invitation invitation = trip.inviteMember(bob, alice, GroupRole.MEMBER, MutableClock.START).invitation();
		when(groups.findIdByInvitationId(invitation.id().value())).thenReturn(Optional.of(trip.groupId().value()));
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when
		service.declineInvitation(invitation.id(), bob);

		// then
		verify(groups).saveAndFlush(trip);
		verify(events)
			.publishEvent(new InvitationDeclined(trip.groupId(), invitation.id(), bob, alice, MutableClock.START));
	}

	@Test
	void pendingInvitationsAreMappedFromTheRowsInTheOrderTheDatabaseGivesThem() {
		// given the database answers with plain rows, newest first
		UUID newerInvitation = UUID.randomUUID();
		UUID olderInvitation = UUID.randomUUID();
		UUID newerGroup = UUID.randomUUID();
		UUID olderGroup = UUID.randomUUID();
		Instant expiry = MutableClock.START.plus(Duration.ofDays(7));
		when(groups.findPendingInvitationsFor(bob.value(), MutableClock.START)).thenReturn(List.of(
				new PendingInvitationRow(newerInvitation, newerGroup, "Newer", alice.value(), GroupRole.ADMIN, expiry),
				new PendingInvitationRow(olderInvitation, olderGroup, "Older", alice.value(), GroupRole.MEMBER,
						expiry)));

		// when
		var pending = service.listPendingInvitations(bob);

		// then
		assertThat(pending).extracting(p -> p.id().value()).containsExactly(newerInvitation, olderInvitation);
		assertThat(pending).extracting(p -> p.groupId().value()).containsExactly(newerGroup, olderGroup);
		assertThat(pending).extracting(p -> p.groupName()).containsExactly("Newer", "Older");
		assertThat(pending).extracting(p -> p.role()).containsExactly(GroupRole.ADMIN, GroupRole.MEMBER);
		assertThat(pending).allSatisfy(p -> {
			assertThat(p.invitedBy()).isEqualTo(alice);
			assertThat(p.expiresAt()).isEqualTo(expiry);
		});
	}

}
