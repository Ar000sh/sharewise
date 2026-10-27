package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test data for a {@link Group}. By default: "Mallorca 2027", created by Alice at
 * {@link MutableClock#START}, no other members. Members are added through the aggregate's
 * own invitation flow, so the built group is always valid.
 */
class GroupFixture {

	private UUID id = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

	private String name = "Mallorca 2027";

	private UUID creator = AccountFixture.alice().id();

	private Instant createdAt = MutableClock.START;

	private final List<UUID> joiners = new ArrayList<>();

	GroupFixture withId(UUID id) {
		this.id = id;
		return this;
	}

	GroupFixture withName(String name) {
		this.name = name;
		return this;
	}

	GroupFixture withCreator(UUID creator) {
		this.creator = creator;
		return this;
	}

	GroupFixture withCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	/** Adds a MEMBER who joined right after creation. */
	GroupFixture withMember(UUID member) {
		joiners.add(member);
		return this;
	}

	Group build() {
		Group group = Group.create(name, creator, createdAt);
		ReflectionTestUtils.setField(group, "id", id);
		for (UUID joiner : joiners) {
			group.invite(creator, "fixture-token-" + joiner, createdAt);
			group.acceptInvitation("fixture-token-" + joiner, joiner, createdAt);
		}
		return group;
	}

}
