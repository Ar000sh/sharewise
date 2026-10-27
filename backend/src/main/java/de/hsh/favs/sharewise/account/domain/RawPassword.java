package de.hsh.favs.sharewise.account.domain;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;

/**
 * A password as typed, alive only for the duration of one call. At least 8 characters; at
 * most 72, the limit of the hash function. Never logged: the text is hidden in
 * {@link #toString()}.
 */
public record RawPassword(String value) {

	public static final int MIN_LENGTH = 8;

	public static final int MAX_LENGTH = 72;

	public RawPassword {
		if (!isAcceptable(value)) {
			throw new DomainException(ErrorCode.BAD_REQUEST,
					"The password must have " + MIN_LENGTH + " to " + MAX_LENGTH + " characters");
		}
	}

	/** Whether a text could be a password, without throwing. */
	public static boolean isAcceptable(String text) {
		return text != null && text.length() >= MIN_LENGTH && text.length() <= MAX_LENGTH;
	}

	@Override
	public String toString() {
		return "RawPassword[****]";
	}

}
