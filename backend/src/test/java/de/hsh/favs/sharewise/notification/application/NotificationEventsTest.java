package de.hsh.favs.sharewise.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.hsh.favs.sharewise.ModuleIntegrationTest;
import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.api.InvitationDeclined;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.api.MemberJoined;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the module writes for each event, with the service called directly (the listeners
 * are asynchronous and only delegate) and a real database. Ids are random: the module
 * keeps ids only and has no foreign keys.
 */
@ModuleIntegrationTest
class NotificationEventsTest {

	static final Instant NOW = MutableClock.START;

	@Autowired
	NotificationService notifications;

	@Autowired
	JdbcTemplate jdbc;

	GroupId group = new GroupId(UUID.randomUUID());

	UserId inviter = new UserId(UUID.randomUUID());

	UserId joiner = new UserId(UUID.randomUUID());

	@Test
	void joiningThroughAnInvitationCreatesAnEntryForTheInviter() {
		// when
		notifications.recordMemberJoined(new MemberJoined(group, joiner, GroupRole.MEMBER, inviter, NOW));

		// then
		var row = jdbc.queryForMap("select * from notifications where recipient_id = ?", inviter.value());
		assertThat(row).containsEntry("type", "MEMBER_JOINED")
			.containsEntry("actor_id", joiner.value())
			.containsEntry("group_id", group.value())
			.containsEntry("reference", joiner.value());
		assertThat(row.get("read_at")).isNull();
	}

	@Test
	void theCreatorsOwnJoinCreatesNothing() {
		// when
		notifications.recordMemberJoined(new MemberJoined(group, inviter, GroupRole.ADMIN, null, NOW));

		// then
		assertThat(count("actor_id", inviter)).isZero();
		assertThat(count("recipient_id", inviter)).isZero();
	}

	@Test
	void theSameEventTwiceCreatesOneEntry() {
		// given
		var event = new MemberJoined(group, joiner, GroupRole.MEMBER, inviter, NOW);

		// when
		notifications.recordMemberJoined(event);
		notifications.recordMemberJoined(event);

		// then
		assertThat(count("recipient_id", inviter)).isEqualTo(1);
	}

	@Test
	void aPersonJoiningTwiceAtDifferentTimesCreatesTwoEntries() {
		// when
		notifications.recordMemberJoined(new MemberJoined(group, joiner, GroupRole.MEMBER, inviter, NOW));
		notifications
			.recordMemberJoined(new MemberJoined(group, joiner, GroupRole.MEMBER, inviter, NOW.plusSeconds(3600)));

		// then
		assertThat(count("recipient_id", inviter)).isEqualTo(2);
	}

	@Test
	void aDeclineCreatesAnEntryForTheInviterPointingAtTheInvitation() {
		// given
		InvitationId invitation = new InvitationId(UUID.randomUUID());

		// when
		notifications.recordInvitationDeclined(new InvitationDeclined(group, invitation, joiner, inviter, NOW));
		notifications.recordInvitationDeclined(new InvitationDeclined(group, invitation, joiner, inviter, NOW));

		// then
		var row = jdbc.queryForMap("select * from notifications where recipient_id = ?", inviter.value());
		assertThat(row).containsEntry("type", "INVITATION_DECLINED")
			.containsEntry("actor_id", joiner.value())
			.containsEntry("reference", invitation.value());
	}

	private int count(String column, UserId user) {
		return jdbc.queryForObject("select count(*) from notifications where " + column + " = ?", Integer.class,
				user.value());
	}

}
