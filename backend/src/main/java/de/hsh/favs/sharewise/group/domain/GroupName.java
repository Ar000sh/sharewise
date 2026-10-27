package de.hsh.favs.sharewise.group.domain;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;

/** The name of a group: trimmed, not blank, at most 100 characters. */
public record GroupName(String value) {

	private static final int MAX_LENGTH = 100;

	public GroupName {
		value = value == null ? "" : value.strip();
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new DomainException(ErrorCode.BAD_REQUEST,
					"The group name must have 1 to " + MAX_LENGTH + " characters");
		}
	}

}
