package de.hsh.favs.sharewise.account.api;

/** The caller of the current request. */
public interface CurrentUser {

	/** Throws a DomainException with UNAUTHENTICATED when there is no known caller. */
	UserInfo get();

}
