package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.MutableClock;
import de.hsh.favs.sharewise.account.domain.AccountFixture;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test data for a {@link Group}. By default: "Mallorca 2027" in EUR, created by Alice at
 * {@link MutableClock#START}, no other members. Members join through the aggregate's own
 * invitation flow, so the built group is always valid.
 */
public class GroupFixture {

	private UUID id = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

	private String name = "Mallorca 2027";

	private UserId creator = AccountFixture.alice().userId();

	private Instant createdAt = MutableClock.START;

	private final List<UserId> joiners = new ArrayList<>();

	public GroupFixture withId(UUID id) {
		this.id = id;
		return this;
	}

	public GroupFixture withName(String name) {
		this.name = name;
		return this;
	}

	public GroupFixture withCreator(UserId creator) {
		this.creator = creator;
		return this;
	}

	public GroupFixture withCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	/** Adds a MEMBER who joined right after creation. */
	public GroupFixture withMember(UserId member) {
		joiners.add(member);
		return this;
	}

	public Group build() {
		Group group = Group.createGroup(new GroupName(name), Currency.getInstance("EUR"), creator, createdAt).group();
		ReflectionTestUtils.setField(group, "id", id);
		for (UserId joiner : joiners) {
			String token = "fixture-token-" + joiner.value();
			Invitation invitation = group.inviteMember(null, creator, token, createdAt).invitation();
			group.acceptInvitation(invitation.id(), joiner, createdAt);
		}
		return group;
	}

}
