package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.account.UserFixture;
import de.hsh.favs.sharewise.group.GroupService.GroupView;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

	@Mock
	GroupRepository groups;

	@Mock
	ApplicationEventPublisher events;

	InvitationService service;

	UserFixture alice = UserFixture.alice();

	UserFixture bob = UserFixture.bob();

	Group group = new GroupFixture().withCreator(alice.id()).build();

	@BeforeEach
	void setUp() {
		service = serviceAt(Clock.fixed(MutableClock.START, ZoneOffset.UTC));
	}

	InvitationService serviceAt(Clock clock) {
		return new InvitationService(groups, events, clock);
	}

	@Test
	void createIssuesAnUnguessableUrlSafeTokenThatExpiresInSevenDays() {
		// given
		when(groups.findById(group.id())).thenReturn(Optional.of(group));

		// when
		Invitation invitation = service.create(group.id(), alice.userId());

		// then
		assertThat(invitation.token()).matches("[A-Za-z0-9_-]+");
		assertThat(Base64.getUrlDecoder().decode(invitation.token())).hasSizeGreaterThanOrEqualTo(16);
		assertThat(invitation.expiresAt()).isEqualTo(MutableClock.START.plus(Duration.ofDays(7)));
		assertThat(service.create(group.id(), alice.userId()).token()).isNotEqualTo(invitation.token());
	}

	@Test
	void createForAnUnknownGroupIsGroupNotFound() {
		// given
		UUID unknown = UUID.randomUUID();
		when(groups.findById(unknown)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.create(unknown, alice.userId())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_FOUND));
	}

	@Test
	void createByANonMemberIsRejected() {
		// given
		when(groups.findById(group.id())).thenReturn(Optional.of(group));

		// when / then
		assertThatThrownBy(() -> service.create(group.id(), bob.userId())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

	@Test
	void acceptJoinsTheCallerStoresTheGroupAndPublishesMemberJoined() {
		// given
		group.invite(alice.id(), "abc", MutableClock.START);
		when(groups.findIdByInvitationToken("abc")).thenReturn(Optional.of(group.id()));
		when(groups.findByIdForUpdate(group.id())).thenReturn(Optional.of(group));

		// when
		GroupView view = service.accept("abc", bob.userId());

		// then
		assertThat(view.memberCount()).isEqualTo(2);
		verify(groups).saveAndFlush(group);
		verify(events).publishEvent((Object) new MemberJoined(new GroupId(group.id()), bob.memberId(), Role.MEMBER));
	}

	@Test
	void acceptWithAnUnknownTokenIsInvitationNotFound() {
		// given
		when(groups.findIdByInvitationToken("nope")).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.accept("nope", bob.userId())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void rejectedAcceptStoresNothingAndPublishesNothing() {
		// given: an invitation that has expired by the time the service clock says
		group.invite(alice.id(), "abc", MutableClock.START);
		when(groups.findIdByInvitationToken("abc")).thenReturn(Optional.of(group.id()));
		when(groups.findByIdForUpdate(group.id())).thenReturn(Optional.of(group));
		InvitationService late = serviceAt(Clock.fixed(MutableClock.START.plus(Duration.ofDays(8)), ZoneOffset.UTC));

		// when / then
		assertThatThrownBy(() -> late.accept("abc", bob.userId())).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
		verify(groups, never()).saveAndFlush(any());
		verify(events, never()).publishEvent(any(Object.class));
	}

}
