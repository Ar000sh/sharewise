package de.hsh.favs.sharewise.account.domain;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The name people type to find an account, chosen at sign-up at the identity provider.
 * Trimmed and lower case, 3 to 30 characters of {@code a-z 0-9 . _ -}. It never contains
 * an {@code @}: the invitation lookup tells an email from a username by that character,
 * so a username that looked like an address could receive the invitations meant for the
 * real owner of the address. The Keycloak realm enforces the same rule at sign-up.
 *
 * @throws DomainException with {@link ErrorCode#BAD_REQUEST} when the text breaks the
 * rule
 */
public record Username(String value) {

	private static final Pattern FORMAT = Pattern.compile("^[a-z0-9._-]{3,30}$");

	public Username {
		value = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
		if (!FORMAT.matcher(value).matches()) {
			throw new DomainException(ErrorCode.BAD_REQUEST,
					"The username must have 3 to 30 characters: letters, digits, dot, underscore or hyphen");
		}
	}

}
