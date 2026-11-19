package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.group.api.GroupRole;
import de.hsh.favs.sharewise.group.application.GroupService.PendingInvitationView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One open invitation of the caller. */
record PendingInvitationResponse(UUID invitationId, GroupRefResponse group, UserSummaryResponse invitedBy,
		GroupRole role, Instant expiresAt) {

	static PendingInvitationResponse of(PendingInvitationView invitation, Map<UUID, UserSummaryResponse> people) {
		UUID inviter = invitation.invitedBy().value();
		return new PendingInvitationResponse(invitation.id().value(),
				new GroupRefResponse(invitation.groupId().value(), invitation.groupName()),
				people.getOrDefault(inviter, UserSummaryResponse.unknown(inviter)), invitation.role(),
				invitation.expiresAt());
	}

}
