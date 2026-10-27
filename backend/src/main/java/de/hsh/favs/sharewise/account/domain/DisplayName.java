package de.hsh.favs.sharewise.account.domain;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;

/**
 * The name other people see for an account. Trimmed, not blank, at most 100 characters.
 */
public record DisplayName(String value) {

	private static final int MAX_LENGTH = 100;

	public DisplayName {
		value = value == null ? "" : value.strip();
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "The name must have 1 to " + MAX_LENGTH + " characters");
		}
	}

}
