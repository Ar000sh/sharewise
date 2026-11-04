package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.account.api.UserProfile;
import java.util.UUID;

/** A person as the invitation endpoints show them. */
record UserSummaryResponse(UUID userId, String displayName, String username) {

	private static final String UNKNOWN_NAME = "Unknown user";

	static UserSummaryResponse of(UserProfile profile) {
		return new UserSummaryResponse(profile.userId().value(), profile.displayName(), profile.username());
	}

	/** For an account that cannot be resolved any more. */
	static UserSummaryResponse unknown(UUID userId) {
		return new UserSummaryResponse(userId, UNKNOWN_NAME, "unknown");
	}

}
