package de.hsh.favs.sharewise.account.web;

import de.hsh.favs.sharewise.account.api.UserProfile;
import java.util.UUID;

/** Response of {@code GET /me}. */
record UserResponse(UUID userId, String displayName) {

	static UserResponse of(UserProfile profile) {
		return new UserResponse(profile.userId().value(), profile.displayName());
	}

}
