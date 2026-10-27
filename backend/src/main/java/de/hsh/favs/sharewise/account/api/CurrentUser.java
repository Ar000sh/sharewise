package de.hsh.favs.sharewise.account.api;

/** The signed-in caller of the current HTTP request. */
public interface CurrentUser {

	/**
	 * The caller's profile.
	 * @throws de.hsh.favs.sharewise.shared.DomainException with UNAUTHENTICATED when
	 * there is no valid session, which includes any call made outside an HTTP request
	 */
	UserProfile get();

}
