package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.List;

/** What other modules may ask the Group module. */
public interface GroupMembership {

	boolean exists(GroupId group);

	boolean isMember(GroupId group, MemberId member);

	List<MemberId> members(GroupId group);

	GroupStatus status(GroupId group);

	/**
	 * The caller check every group-scoped endpoint uses (spec D16): an unknown group is 404, a non-member
	 * caller is 403. It lives here so fakes and the real implementation behave the same.
	 */
	default void requireMember(GroupId group, MemberId member) {
		if (!exists(group)) {
			throw new DomainException(ErrorCode.GROUP_NOT_FOUND, "Group not found");
		}
		if (!isMember(group, member)) {
			throw new DomainException(ErrorCode.NOT_A_MEMBER, "You are not a member of this group");
		}
	}

}
