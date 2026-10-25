package de.hsh.favs.sharewise.settlement;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class FakeGroupMembership implements GroupMembership {

	private final Map<GroupId, List<MemberId>> groups = new ConcurrentHashMap<>();

	void define(GroupId group, MemberId... members) {
		groups.put(group, List.of(members));
	}

	@Override
	public boolean exists(GroupId group) {
		return groups.containsKey(group);
	}

	@Override
	public boolean isMember(GroupId group, MemberId member) {
		return members(group).contains(member);
	}

	@Override
	public List<MemberId> members(GroupId group) {
		return groups.getOrDefault(group, List.of());
	}

	@Override
	public GroupStatus status(GroupId group) {
		return GroupStatus.OPEN;
	}

}
