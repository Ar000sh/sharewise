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
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.MemberInvited;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.domain.Group;
import de.hsh.favs.sharewise.group.domain.GroupFixture;
import de.hsh.favs.sharewise.group.domain.GroupName;
import de.hsh.favs.sharewise.group.persistence.GroupRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.time.Duration;
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
	ApplicationEventPublisher events;

	GroupService service;

	UserId alice = AccountFixture.alice().userId();

	UserId bob = AccountFixture.bob().userId();

	@BeforeEach
	void setUp() {
		service = new GroupService(groups, membership, events, Clock.fixed(MutableClock.START, ZoneOffset.UTC));
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
		order.verify(events).publishEvent(new MemberJoined(id, alice, GroupRole.ADMIN, MutableClock.START));
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
	void invitingIssuesAnUnguessableUrlSafeTokenThatExpiresInSevenDaysAndPublishesMemberInvited() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		when(groups.findById(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when
		var invitation = service.inviteMember(trip.groupId(), alice);

		// then
		assertThat(invitation.token()).matches("[A-Za-z0-9_-]{43}");
		assertThat(invitation.expiresAt()).isEqualTo(MutableClock.START.plus(Duration.ofDays(7)));
		ArgumentCaptor<MemberInvited> published = ArgumentCaptor.forClass(MemberInvited.class);
		verify(events).publishEvent(published.capture());
		assertThat(published.getValue().invitedBy()).isEqualTo(alice);
		assertThat(published.getValue().groupId()).isEqualTo(trip.groupId());
	}

	@Test
	void invitingToAnUnknownGroupIsGroupNotFound() {
		// given
		GroupId unknown = new GroupId(UUID.randomUUID());
		when(groups.findById(unknown.value())).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.inviteMember(unknown, alice)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_FOUND));
		verifyNoInteractions(events);
	}

	@Test
	void aNonMemberCannotInviteAndNothingIsPublished() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		when(groups.findById(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when / then
		assertThatThrownBy(() -> service.inviteMember(trip.groupId(), bob))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		verifyNoInteractions(events);
	}

	@Test
	void acceptingJoinsTheCallerSavesTheGroupAndPublishesMemberJoined() {
		// given
		Group trip = new GroupFixture().withCreator(alice).build();
		String token = trip.inviteMember(null, alice, "link-token", MutableClock.START).invitation().token();
		when(groups.findIdByInvitationToken(token)).thenReturn(Optional.of(trip.groupId().value()));
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when
		var view = service.acceptInvitation(token, bob);

		// then
		verify(groups).saveAndFlush(trip);
		verify(events).publishEvent(new MemberJoined(trip.groupId(), bob, GroupRole.MEMBER, MutableClock.START));
		assertThat(view.memberCount()).isEqualTo(2);
	}

	@Test
	void acceptingWithAnUnknownTokenIsInvitationNotFound() {
		// given
		when(groups.findIdByInvitationToken("nope")).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.acceptInvitation("nope", bob)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		verify(groups, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

	@Test
	void aRefusedAcceptanceStoresNothingAndPublishesNothing() {
		// given an existing member using the link
		Group trip = new GroupFixture().withCreator(alice).build();
		String token = trip.inviteMember(null, alice, "link-token", MutableClock.START).invitation().token();
		when(groups.findIdByInvitationToken(token)).thenReturn(Optional.of(trip.groupId().value()));
		when(groups.findByIdForUpdate(trip.groupId().value())).thenReturn(Optional.of(trip));

		// when / then
		assertThatThrownBy(() -> service.acceptInvitation(token, alice)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_MEMBER));
		verify(groups, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

}
