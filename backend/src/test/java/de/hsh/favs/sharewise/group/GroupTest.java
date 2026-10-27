package de.hsh.favs.sharewise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.account.UserFixture;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The rules of the Group aggregate: creation, inviting and joining. No Spring, no
 * database.
 */
class GroupTest {

	static final Instant NOW = MutableClock.START;

	UUID alice = UserFixture.alice().id();

	UUID bob = UserFixture.bob().id();

	Group group = new GroupFixture().withCreator(alice).build();

	@Test
	void createMakesAnOpenGroupWithTheCreatorAsAdmin() {
		// given / when: group built in the field initializer

		// then
		assertThat(group.name()).isEqualTo("Mallorca 2027");
		assertThat(group.status()).isEqualTo(GroupStatus.OPEN);
		assertThat(group.members()).singleElement().satisfies(m -> {
			assertThat(m.memberId()).isEqualTo(alice);
			assertThat(m.role()).isEqualTo(Role.ADMIN);
		});
	}

	@Test
	void memberInvites() {
		// when
		Invitation invitation = group.invite(alice, "abc", NOW);

		// then
		assertThat(invitation.token()).isEqualTo("abc");
		assertThat(invitation.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
	}

	@Test
	void nonMemberCannotInvite() {
		assertThatThrownBy(() -> group.invite(bob, "abc", NOW)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
	}

	@Test
	void acceptAddsTheUserAsMember() {
		// given
		group.invite(alice, "abc", NOW);

		// when
		group.acceptInvitation("abc", bob, NOW.plusSeconds(60));

		// then
		assertThat(group.memberCount()).isEqualTo(2);
		assertThat(group.members()).filteredOn(m -> m.memberId().equals(bob)).singleElement().satisfies(m -> {
			assertThat(m.role()).isEqualTo(Role.MEMBER);
			assertThat(m.joinedAt()).isEqualTo(NOW.plusSeconds(60));
		});
	}

	@Test
	void oneTokenAdmitsSeveralUsers() {
		// given
		group.invite(alice, "abc", NOW);

		// when
		group.acceptInvitation("abc", bob, NOW);
		group.acceptInvitation("abc", UserFixture.carol().id(), NOW);

		// then
		assertThat(group.memberCount()).isEqualTo(3);
	}

	@Test
	void unknownTokenIsRejected() {
		group.invite(alice, "abc", NOW);

		assertThatThrownBy(() -> group.acceptInvitation("other", bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
	}

	@Test
	void tokenOfAnotherGroupIsRejected() {
		// given
		Group other = new GroupFixture().withId(UUID.randomUUID()).build();
		other.invite(alice, "other-token", NOW);

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation("other-token", bob, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_NOT_FOUND));
	}

	@Test
	void expiredInvitationIsRejectedAndNobodyJoins() {
		// given
		group.invite(alice, "abc", NOW);

		// when / then: one millisecond after the 7 days
		assertThatThrownBy(() -> group.acceptInvitation("abc", bob, NOW.plus(Duration.ofDays(7)).plusMillis(1)))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
		assertThat(group.memberCount()).isEqualTo(1);
	}

	@Test
	void invitationStillWorksAtTheExactExpiryInstant() {
		// given
		group.invite(alice, "abc", NOW);

		// when
		group.acceptInvitation("abc", bob, NOW.plus(Duration.ofDays(7)));

		// then
		assertThat(group.hasMember(bob)).isTrue();
	}

	@Test
	void existingMemberCannotJoinAgain() {
		// given
		group.invite(alice, "abc", NOW);

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation("abc", alice, NOW)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_MEMBER));
		assertThat(group.memberCount()).isEqualTo(1);
	}

	@Test
	void expiryIsCheckedBeforeMembership() {
		// given: an expired invitation used by someone who is already a member
		group.invite(alice, "abc", NOW);

		// when / then
		assertThatThrownBy(() -> group.acceptInvitation("abc", alice, NOW.plus(Duration.ofDays(8))))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVITATION_EXPIRED));
	}

}
