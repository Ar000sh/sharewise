package de.hsh.favs.sharewise.account.web;

import de.hsh.favs.sharewise.account.application.AccountService;
import java.util.UUID;

/**
 * The session token (shown once) and who it belongs to. The Next.js route handler keeps
 * the token in a cookie.
 */
record AuthResponse(String token, UUID userId, String displayName) {

	static AuthResponse of(AccountService.SignedIn signedIn) {
		return new AuthResponse(signedIn.token(), signedIn.profile().userId().value(),
				signedIn.profile().displayName());
	}

}
