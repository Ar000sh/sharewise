package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The caller resolved by the identity filter, held as an attribute of the current
 * request.
 */
@Component
class CurrentUserImpl implements CurrentUser {

	private static final String ATTRIBUTE = CurrentUserImpl.class.getName() + ".CALLER";

	static void bind(HttpServletRequest request, UserInfo caller) {
		request.setAttribute(ATTRIBUTE, caller);
	}

	@Override
	public UserInfo get() {
		// getRequestAttributes() is null outside a request thread (e.g. an event
		// listener).
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes != null
				&& attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof UserInfo info) {
			return info;
		}
		throw new DomainException(ErrorCode.UNAUTHENTICATED, "Missing or invalid session");
	}

}
