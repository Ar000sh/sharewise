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

	/**
	 * The name for a first login from the identity provider: the {@code name} claim, else
	 * the {@code preferred_username}, else the part of the email before the {@code @};
	 * trimmed and cut to the limit so a first login never fails on it (design C7).
	 */
	public static DisplayName derive(String name, String preferredUsername, String email) {
		String chosen = firstNonBlank(name, preferredUsername, email == null ? null : email.split("@", 2)[0]);
		return new DisplayName(chosen.length() > MAX_LENGTH ? chosen.substring(0, MAX_LENGTH) : chosen);
	}

	private static String firstNonBlank(String... candidates) {
		for (String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate.strip();
			}
		}
		return "";
	}

}
