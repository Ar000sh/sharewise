package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.Set;

/**
 * What another context needs to know about a group to enforce its own rules: is it open,
 * who is an active member, who is an admin. A snapshot taken when it was asked for; it is
 * not updated afterwards.
 */
public record GroupMembershipView(GroupId groupId, boolean isOpen, Set<UserId> activeMembers, Set<UserId> admins) {

	public GroupMembershipView {
		activeMembers = Set.copyOf(activeMembers);
		admins = Set.copyOf(admins);
	}

	public boolean isActiveMember(UserId user) {
		return activeMembers.contains(user);
	}

	public boolean isAdmin(UserId user) {
		return admins.contains(user);
	}

}
