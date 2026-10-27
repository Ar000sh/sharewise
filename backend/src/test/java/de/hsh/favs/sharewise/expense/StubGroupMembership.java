package de.hsh.favs.sharewise.expense;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupMembershipView;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-written stand-in for the real GroupMembership. Keeps the default requireMember.
 */
public class StubGroupMembership implements GroupMembership {

	private final Map<GroupId, List<UserId>> members = new HashMap<>();

	private final Map<GroupId, GroupStatus> status = new HashMap<>();

	public void group(GroupId group, GroupStatus groupStatus, UserId... memberIds) {
		members.put(group, List.of(memberIds));
		status.put(group, groupStatus);
	}

	public void reset() {
		members.clear();
		status.clear();
	}

	@Override
	public boolean exists(GroupId group) {
		return members.containsKey(group);
	}

	@Override
	public boolean isMember(GroupId group, UserId member) {
		return members.getOrDefault(group, List.of()).contains(member);
	}

	@Override
	public List<UserId> members(GroupId group) {
		return members.getOrDefault(group, List.of());
	}

	@Override
	public GroupStatus status(GroupId group) {
		return status.get(group);
	}

	@Override
	public GroupMembershipView view(GroupId group) {
		return new GroupMembershipView(group, status.get(group) == GroupStatus.OPEN, Set.copyOf(members(group)),
				Set.of());
	}

}
