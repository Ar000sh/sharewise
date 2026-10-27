package de.hsh.favs.sharewise.account.domain;

/**
 * Where an account is in its life. Only ACTIVE can sign in. The other states arrive with
 * email verification, lock-out and deactivation in later exercises.
 */
public enum AccountStatus {

	PENDING_VERIFICATION, ACTIVE, LOCKED, DEACTIVATED

}
