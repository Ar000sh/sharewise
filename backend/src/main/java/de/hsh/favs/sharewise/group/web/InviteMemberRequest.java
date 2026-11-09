package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.group.api.GroupRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /groups/{groupId}/invitations}: an exact username or email, and
 * optionally the role the invitee gets (MEMBER when omitted).
 */
record InviteMemberRequest(@NotBlank @Size(max = 254) String identifier, GroupRole role) {

	GroupRole roleOrDefault() {
		return role == null ? GroupRole.MEMBER : role;
	}

}
