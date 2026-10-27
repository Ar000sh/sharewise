package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The caller resolved by {@link SessionIdentityFilter}, held as an attribute of the
 * current request.
 */
@Component
class CurrentUserImpl implements CurrentUser {

	private static final String ATTRIBUTE = CurrentUserImpl.class.getName() + ".CALLER";

	static void bind(HttpServletRequest request, UserProfile caller) {
		request.setAttribute(ATTRIBUTE, caller);
	}

	@Override
	public UserProfile get() {
		// getRequestAttributes() is null outside a request thread (e.g. an event
		// listener).
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes != null
				&& attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof UserProfile profile) {
			return profile;
		}
		throw new DomainException(ErrorCode.UNAUTHENTICATED, "Missing or invalid session");
	}

}
