package de.hsh.favs.sharewise.account.domain;

/** A password after hashing. The only form in which a password is ever stored. */
public record PasswordHash(String value) {

	@Override
	public String toString() {
		return "PasswordHash[****]";
	}

}
