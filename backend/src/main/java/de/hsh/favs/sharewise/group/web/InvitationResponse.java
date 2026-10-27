package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.group.application.GroupService.InvitationView;
import java.time.Instant;

record InvitationResponse(String token, Instant expiresAt) {

	static InvitationResponse of(InvitationView invitation) {
		return new InvitationResponse(invitation.token(), invitation.expiresAt());
	}

}
