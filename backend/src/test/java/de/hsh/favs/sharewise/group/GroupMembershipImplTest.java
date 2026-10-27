package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * The GroupMembership bean the expense and settlement modules call, against the real
 * database and, like those modules, from outside any transaction. A mocked repository
 * could not catch a lazy-loading failure here.
 */
@ModuleIntegrationTest
class GroupMembershipImplTest {

	@Autowired
	GroupMembership membership;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	AccountFixture alice;

	AccountFixture bob;

	AccountFixture carol;

	AccountFixture david;

	GroupId trip;

	@BeforeEach
	void setUp() throws Exception {
		alice = AccountFixture.signUp(mvc, json, "Alice");
		bob = AccountFixture.signUp(mvc, json, "Bob");
		carol = AccountFixture.signUp(mvc, json, "Carol");
		david = AccountFixture.signUp(mvc, json, "David");
		trip = new GroupId(new GroupRequests(mvc, json).groupWith(alice, "Trip", bob, carol));
	}

	@Test
	void requireMemberAcceptsMembersAndRejectsStrangersAndUnknownGroups() {
		assertThatCode(() -> membership.requireMember(trip, carol.userId())).doesNotThrowAnyException();
		assertThatThrownBy(() -> membership.requireMember(trip, david.userId()))
			.isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		assertThatThrownBy(() -> membership.requireMember(new GroupId(UUID.randomUUID()), alice.userId()))
			.isInstanceOfSatisfying(DomainException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_FOUND));
	}

	@Test
	void allMembersAreListedAndTheGroupIsOpen() {
		assertThat(membership.exists(trip)).isTrue();
		// the test clock is frozen, so everyone joined at the same instant and the order
		// is not asserted
		assertThat(membership.members(trip)).containsExactlyInAnyOrder(alice.userId(), bob.userId(), carol.userId());
		assertThat(membership.status(trip)).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void anUnknownGroupDoesNotExistAndHasNoMembersOrStatus() {
		GroupId unknown = new GroupId(UUID.randomUUID());

		assertThat(membership.exists(unknown)).isFalse();
		assertThat(membership.isMember(unknown, alice.userId())).isFalse();
		assertThatThrownBy(() -> membership.members(unknown)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> membership.status(unknown)).isInstanceOf(DomainException.class);
	}

}
