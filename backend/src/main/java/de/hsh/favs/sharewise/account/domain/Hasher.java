package de.hsh.favs.sharewise.account.domain;

/**
 * Domain service: one-way hashing of passwords and of session tokens. Implemented in the
 * application layer.
 */
public interface Hasher {

	PasswordHash hashPassword(RawPassword password);

	boolean matchesPassword(RawPassword password, PasswordHash hash);

	/** A fixed-length hash of a random token, so the token itself need not be stored. */
	String hashToken(String rawToken);

	boolean matchesToken(String rawToken, String tokenHash);

}
