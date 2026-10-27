package de.hsh.favs.sharewise.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An email address, the sign-in name of an account and the address an invitation is sent
 * to. Stored trimmed and in lower case, so two spellings of one address are equal.
 *
 * @throws DomainException with {@link ErrorCode#BAD_REQUEST} when the text is not an
 * address
 */
public record Email(String value) {

	private static final int MAX_LENGTH = 254;

	private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public Email {
		value = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
		if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "Not a valid email address");
		}
	}

}
