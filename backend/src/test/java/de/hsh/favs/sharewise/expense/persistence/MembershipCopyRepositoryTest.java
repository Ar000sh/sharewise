package de.hsh.favs.sharewise.expense.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * An event applied twice, or an older one after a newer one, leaves the same copy: the
 * property that makes redelivery and a later message broker safe.
 */
@ModuleIntegrationTest
class MembershipCopyRepositoryTest {

	static final Instant T1 = Instant.parse("2027-06-01T10:00:00Z");

	static final Instant T2 = Instant.parse("2027-06-01T10:05:00Z");

	@Autowired
	MembershipCopyRepository copy;

	GroupId group = new GroupId(UUID.randomUUID());

	UserId alice = new UserId(UUID.randomUUID());

	@Test
	void aGroupWithoutAnyEventIsUnknown() {
		assertThat(copy.find(group)).isEmpty();
	}

	@Test
	void membersAndAdminsAreReadBackWithTheGroupsOpenFlag() {
		// given
		UserId bob = new UserId(UUID.randomUUID());
		copy.saveGroup(group, true, T1);
		copy.saveMember(group, alice, true, true, T1);
		copy.saveMember(group, bob, false, true, T1);

		// when
		var view = copy.find(group).orElseThrow();

		// then
		assertThat(view.isOpen()).isTrue();
		assertThat(view.activeMembers()).containsExactlyInAnyOrder(alice, bob);
		assertThat(view.admins()).containsExactly(alice);
	}

	@Test
	void theSameEventAppliedTwiceChangesNothing() {
		// given
		copy.saveGroup(group, true, T1);

		// when
		copy.saveMember(group, alice, true, true, T1);
		copy.saveMember(group, alice, true, true, T1);

		// then
		assertThat(copy.find(group).orElseThrow().activeMembers()).containsExactly(alice);
	}

	@Test
	void anOlderEventArrivingAfterANewerOneIsIgnored() {
		// given alice was made inactive at T2
		copy.saveGroup(group, true, T1);
		copy.saveMember(group, alice, true, false, T2);

		// when the older "joined" event is delivered late
		copy.saveMember(group, alice, true, true, T1);

		// then
		assertThat(copy.find(group).orElseThrow().activeMembers()).isEmpty();
	}

	@Test
	void aMemberEventBeforeTheGroupEventDoesNotMakeTheGroupKnown() {
		// when
		copy.saveMember(group, alice, true, true, T1);

		// then the group stays unknown until its own event arrives
		assertThat(copy.find(group)).isEmpty();
		copy.saveGroup(group, true, T1);
		assertThat(copy.find(group).orElseThrow().activeMembers()).containsExactly(alice);
	}

}
