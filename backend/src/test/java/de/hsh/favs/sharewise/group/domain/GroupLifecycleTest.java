package de.hsh.favs.sharewise.group.domain;

import static org.assertj.core.api.Assertions.*;
import de.hsh.favs.sharewise.group.api.*;
import de.hsh.favs.sharewise.shared.*;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupLifecycleTest {

	final UserId admin = new UserId(UUID.randomUUID());

	final UserId member = new UserId(UUID.randomUUID());

	final Instant now = Instant.parse("2026-11-21T10:00:00Z");

	Group group() {
		return Group.createGroup(new GroupName("Trip"), Currency.getInstance("EUR"), admin, now).group();
	}

	@Test
	void closeReopenAndClosePreserveEarlierSnapshots() {
		var g = group();
		var event = g.closeGroup(admin, 0, true, "original snapshot", now);
		assertThat(event.status()).isEqualTo(GroupStatus.CLOSED);
		assertThat(g.lifecycleRevision()).isEqualTo(1);
		g.reopenGroup(admin, 1, "Correct receipt", now.plusSeconds(1));
		g.closeGroup(admin, 2, true, "corrected snapshot", now.plusSeconds(2));
		assertThat(g.archives()).extracting(GroupArchive::version).containsExactly(1, 2);
		assertThat(g.archives()).extracting(GroupArchive::snapshot)
			.containsExactly("original snapshot", "corrected snapshot");
		assertThat(g.lifecycleEntries()).extracting(GroupLifecycleEntry::reason)
			.containsExactly("", "Correct receipt", "");
	}

	@Test
	void refusalLeavesStateAndHistoryUnchanged() {
		var g = group();
		var invitation = g.inviteMember(member, admin, GroupRole.MEMBER, now).invitation();
		g.acceptInvitation(invitation.id(), member, now);
		assertThatThrownBy(() -> g.closeGroup(member, 0, true, "{}", now)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_AN_ADMIN));
		assertThatThrownBy(() -> g.closeGroup(admin, 0, false, "{}", now)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.BALANCES_NOT_ZERO));
		assertThat(g.status()).isEqualTo(GroupStatus.OPEN);
		assertThat(g.archives()).isEmpty();
		assertThat(g.lifecycleRevision()).isZero();
	}

	@Test
	void staleRequestsCannotCloseAReopenedGroup() {
		var g = group();
		g.closeGroup(admin, 0, true, "{}", now);
		g.reopenGroup(admin, 1, "Correction", now);
		assertThatThrownBy(() -> g.closeGroup(admin, 0, true, "{}", now)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.LIFECYCLE_CONFLICT));
		assertThat(g.status()).isEqualTo(GroupStatus.OPEN);
		assertThat(g.archives()).hasSize(1);
	}

	@Test
	void reopeningRequiresAdminAndReason() {
		var g = group();
		g.closeGroup(admin, 0, true, "{}", now);
		assertThatThrownBy(() -> g.reopenGroup(member, 1, "Correction", now)).isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> g.reopenGroup(admin, 1, "  ", now)).isInstanceOf(DomainException.class);
		assertThat(g.status()).isEqualTo(GroupStatus.CLOSED);
		assertThat(g.lifecycleEntries()).hasSize(1);
	}

}
