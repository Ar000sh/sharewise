package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class GroupMembershipImpl implements GroupMembership {

	private final GroupRepository groups;

	GroupMembershipImpl(GroupRepository groups) {
		this.groups = groups;
	}

	@Override
	public boolean exists(GroupId group) {
		return groups.existsById(group.value());
	}

	@Override
	public boolean isMember(GroupId group, UserId member) {
		return groups.existsMember(group.value(), member.value());
	}

	@Override
	public List<UserId> members(GroupId group) {
		return load(group).members().stream().map(m -> new UserId(m.memberId())).toList();
	}

	@Override
	public GroupStatus status(GroupId group) {
		return load(group).status();
	}

	private Group load(GroupId group) {
		return groups.findById(group.value())
			.orElseThrow(() -> new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found"));
	}

}
