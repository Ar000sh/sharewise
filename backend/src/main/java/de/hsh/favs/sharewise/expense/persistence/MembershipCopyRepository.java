package de.hsh.favs.sharewise.expense.persistence;

import de.hsh.favs.sharewise.expense.domain.GroupMembershipView;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The expense context's copy of the group facts it needs. Every write is an upsert that
 * only applies an event at least as new as what is stored, so an event delivered twice or
 * out of order leaves the same result.
 */
@Repository
public class MembershipCopyRepository {

	private final JdbcClient jdbc;

	MembershipCopyRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void saveGroup(GroupId group, boolean open, Instant occurredAt) {
		jdbc.sql("""
				INSERT INTO expense_known_groups (group_id, is_open, as_of) VALUES (:group, :open, :asOf)
				ON CONFLICT (group_id) DO UPDATE SET is_open = EXCLUDED.is_open, as_of = EXCLUDED.as_of
				WHERE expense_known_groups.as_of <= EXCLUDED.as_of
				""").param("group", group.value()).param("open", open).param("asOf", utc(occurredAt)).update();
	}

	public void saveMember(GroupId group, UserId user, boolean admin, boolean active, Instant occurredAt) {
		jdbc.sql("""
				INSERT INTO expense_known_members (group_id, user_id, is_admin, is_active, as_of)
				VALUES (:group, :user, :admin, :active, :asOf)
				ON CONFLICT (group_id, user_id) DO UPDATE
				SET is_admin = EXCLUDED.is_admin, is_active = EXCLUDED.is_active, as_of = EXCLUDED.as_of
				WHERE expense_known_members.as_of <= EXCLUDED.as_of
				""")
			.param("group", group.value())
			.param("user", user.value())
			.param("admin", admin)
			.param("active", active)
			.param("asOf", utc(occurredAt))
			.update();
	}

	/** Empty when no event about this group has arrived yet. */
	public Optional<GroupMembershipView> find(GroupId group) {
		return jdbc.sql("SELECT is_open FROM expense_known_groups WHERE group_id = :group")
			.param("group", group.value())
			.query(Boolean.class)
			.optional()
			.map(open -> {
				Set<UserId> active = new HashSet<>();
				Set<UserId> admins = new HashSet<>();
				jdbc.sql("SELECT user_id, is_admin FROM expense_known_members WHERE group_id = :group AND is_active")
					.param("group", group.value())
					.query((rs, row) -> {
						UserId user = new UserId(rs.getObject("user_id", java.util.UUID.class));
						active.add(user);
						if (rs.getBoolean("is_admin")) {
							admins.add(user);
						}
						return user;
					})
					.list();
				return new GroupMembershipView(group, open, active, admins);
			});
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
