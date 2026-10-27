package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.group.GroupController.GroupResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InvitationController {

	private final InvitationService invitations;

	private final CurrentUser currentUser;

	InvitationController(InvitationService invitations, CurrentUser currentUser) {
		this.invitations = invitations;
		this.currentUser = currentUser;
	}

	record InvitationResponse(String token, Instant expiresAt) {
	}

	@PostMapping("/groups/{groupId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	InvitationResponse create(@PathVariable UUID groupId) {
		Invitation i = invitations.create(groupId, currentUser.get().userId());
		return new InvitationResponse(i.token(), i.expiresAt());
	}

	@PostMapping("/invitations/{token}/accept")
	GroupResponse accept(@PathVariable String token) {
		return GroupResponse.of(invitations.accept(token, currentUser.get().userId()));
	}

}
