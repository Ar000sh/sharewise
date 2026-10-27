package de.hsh.favs.sharewise.account.domain;

/**
 * A {@link Hasher} that is readable and fast, for unit tests that do not test hashing.
 */
public class StubHasher implements Hasher {

	@Override
	public PasswordHash hashPassword(RawPassword password) {
		return new PasswordHash("hash:" + password.value());
	}

	@Override
	public boolean matchesPassword(RawPassword password, PasswordHash hash) {
		return hash.value().equals("hash:" + password.value());
	}

	@Override
	public String hashToken(String rawToken) {
		return "token-hash:" + rawToken;
	}

	@Override
	public boolean matchesToken(String rawToken, String tokenHash) {
		return tokenHash.equals(hashToken(rawToken));
	}

}
