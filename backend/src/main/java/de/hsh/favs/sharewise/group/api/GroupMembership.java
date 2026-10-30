package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.List;

/**
 * What other modules may ask the group module. Every method works without an open
 * transaction and returns plain values, never entities.
 */
public interface GroupMembership {

	boolean exists(GroupId group);

	boolean isMember(GroupId group, UserId user);

	List<UserId> members(GroupId group);

	GroupStatus status(GroupId group);

	/**
	 * A fresh snapshot of the group's status, active members and admins.
	 * @throws DomainException GROUP_NOT_FOUND when there is no such group
	 */
	GroupMembershipView view(GroupId group);

	/**
	 * The caller check every group-scoped endpoint uses (D16): an unknown group is 404, a
	 * non-member is 403. It lives here so that every implementation behaves the same.
	 */
	default void requireMember(GroupId group, UserId user) {
		if (!exists(group)) {
			throw new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found");
		}
		if (!isMember(group, user)) {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		}
	}

}
