package de.hsh.favs.sharewise.account.domain;

/**
 * Where an account is in its life. Only ACTIVE accounts are let in (C3). The other states
 * arrive with deactivation in later exercises; email verification and lock-out belong to
 * the identity provider.
 */
public enum AccountStatus {

	PENDING_VERIFICATION, ACTIVE, LOCKED, DEACTIVATED

}
