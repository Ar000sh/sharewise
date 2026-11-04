package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.group.application.GroupService.InvitationView;
import java.time.Instant;
import java.util.UUID;

/** Response of {@code POST /groups/{groupId}/invitations}. */
record InvitationResponse(UUID invitationId, UserSummaryResponse invitee, Instant expiresAt) {

	static InvitationResponse of(InvitationView invitation) {
		return new InvitationResponse(invitation.id().value(), UserSummaryResponse.of(invitation.invitee()),
				invitation.expiresAt());
	}

}
