package de.hsh.favs.sharewise.account.api;

/** The signed-in caller of the current HTTP request. */
public interface CurrentUser {

	/**
	 * The caller's profile, resolved from the validated Keycloak access token. Works
	 * without a transaction.
	 * @throws de.hsh.favs.sharewise.shared.DomainException with UNAUTHENTICATED when
	 * there is no authenticated caller, which includes any call made outside an HTTP
	 * request
	 */
	UserProfile get();

}
