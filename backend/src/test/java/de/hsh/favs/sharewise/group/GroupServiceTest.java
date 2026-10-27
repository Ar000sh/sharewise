package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.UserFixture;
import de.hsh.favs.sharewise.group.GroupService.GroupView;
import de.hsh.favs.sharewise.group.api.GroupCreated;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

	@Mock
	GroupRepository groups;

	@Mock
	GroupMembership membership;

	@Mock
	ApplicationEventPublisher events;

	GroupService service;

	UserFixture alice = UserFixture.alice();

	UserFixture bob = UserFixture.bob();

	@BeforeEach
	void setUp() {
		service = new GroupService(groups, membership, events, Clock.fixed(MutableClock.START, ZoneOffset.UTC));
	}

	@Test
	void createStoresAnOpenGroupAndPublishesGroupCreatedThenMemberJoinedAsAdmin() {
		// given
		when(groups.save(any(Group.class))).thenAnswer(call -> call.getArgument(0));

		// when
		GroupView view = service.create("Mallorca 2027", alice.info());

		// then
		assertThat(view.name()).isEqualTo("Mallorca 2027");
		assertThat(view.status()).isEqualTo(GroupStatus.OPEN);
		assertThat(view.memberCount()).isEqualTo(1);
		GroupId id = new GroupId(view.id());
		InOrder order = inOrder(events);
		order.verify(events).publishEvent((Object) new GroupCreated(id, alice.userId()));
		order.verify(events).publishEvent((Object) new MemberJoined(id, alice.userId(), Role.ADMIN));
	}

	@Test
	void listForReturnsTheGroupsOfTheCaller() {
		// given
		Group mallorca = new GroupFixture().withMember(bob.id()).build();
		when(groups.findAllByMember(bob.id())).thenReturn(List.of(mallorca));

		// when
		List<GroupView> views = service.listFor(bob.userId());

		// then
		assertThat(views).singleElement().satisfies(v -> {
			assertThat(v.name()).isEqualTo("Mallorca 2027");
			assertThat(v.memberCount()).isEqualTo(2);
		});
	}

	@Test
	void detailChecksMembershipThenReturnsTheGroup() {
		// given
		Group mallorca = new GroupFixture().build();
		when(groups.findById(mallorca.id())).thenReturn(Optional.of(mallorca));

		// when
		GroupView view = service.detail(mallorca.id(), alice.userId());

		// then
		verify(membership).requireMember(new GroupId(mallorca.id()), alice.userId());
		assertThat(view.id()).isEqualTo(mallorca.id());
	}

	@Test
	void detailForANonMemberFailsBeforeTheGroupIsRead() {
		// given
		UUID groupId = UUID.randomUUID();
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(membership)
			.requireMember(new GroupId(groupId), bob.userId());

		// when / then
		assertThatThrownBy(() -> service.detail(groupId, bob.userId())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		verifyNoInteractions(groups);
	}

	@Test
	void createPublishesNothingWhenStoringFails() {
		// given
		when(groups.save(any(Group.class))).thenThrow(new IllegalStateException("db down"));

		// when / then
		assertThatThrownBy(() -> service.create("Mallorca 2027", alice.info()))
			.isInstanceOf(IllegalStateException.class);
		verify(events, never()).publishEvent(any(Object.class));
	}

}
