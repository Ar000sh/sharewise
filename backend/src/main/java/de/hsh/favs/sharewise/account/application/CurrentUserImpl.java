package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The caller that {@link JwtCallerConverter} put into the Spring Security context of the
 * current request. Needs no transaction and no HTTP request: outside a request (an event
 * listener thread) the context is empty and the answer is UNAUTHENTICATED.
 */
@Component
class CurrentUserImpl implements CurrentUser {

	@Override
	public UserProfile get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof UserProfile profile) {
			return profile;
		}
		throw new DomainException(ErrorCode.UNAUTHENTICATED, "Missing or invalid token");
	}

}
