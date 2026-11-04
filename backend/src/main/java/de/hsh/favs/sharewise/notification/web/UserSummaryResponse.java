package de.hsh.favs.sharewise.notification.web;

import de.hsh.favs.sharewise.account.api.UserProfile;
import java.util.UUID;

/** The person who did something, as the inbox shows them. */
record UserSummaryResponse(UUID userId, String displayName, String username) {

	static UserSummaryResponse of(UserProfile profile) {
		return new UserSummaryResponse(profile.userId().value(), profile.displayName(), profile.username());
	}

}
