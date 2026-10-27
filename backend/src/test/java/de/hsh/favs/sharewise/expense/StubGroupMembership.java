package de.hsh.favs.sharewise.expense;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written stand-in for the real GroupMembership. Keeps the default requireMember.
 */
public class StubGroupMembership implements GroupMembership {

	private final Map<GroupId, List<MemberId>> members = new HashMap<>();

	private final Map<GroupId, GroupStatus> status = new HashMap<>();

	public void group(GroupId group, GroupStatus groupStatus, MemberId... memberIds) {
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
	public boolean isMember(GroupId group, MemberId member) {
		return members.getOrDefault(group, List.of()).contains(member);
	}

	@Override
	public List<MemberId> members(GroupId group) {
		return members.getOrDefault(group, List.of());
	}

	@Override
	public GroupStatus status(GroupId group) {
		return status.get(group);
	}

}
