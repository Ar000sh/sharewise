package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.shared.DomainException;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Runs right after the resource server has validated a Keycloak access token: resolves
 * (or on the first request creates) the caller's account and makes its
 * {@link UserProfile} the principal (C4). Runs in the filter chain without a transaction;
 * {@link AccountService#resolveOrCreate} opens its own.
 */
@Component
class JwtCallerConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final AccountService accounts;

	JwtCallerConverter(AccountService accounts) {
		this.accounts = accounts;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		var claims = new IdentityClaims(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"),
				jwt.getClaimAsString("preferred_username"));
		try {
			UserProfile caller = accounts.resolveOrCreate(claims);
			return new PreAuthenticatedAuthenticationToken(caller, jwt, List.of());
		}
		catch (DomainException refused) {
			// An authentication exception lets the resource server answer 401 through the
			// entry point instead of the DomainException escaping the filter chain as a
			// 500.
			throw new InvalidBearerTokenException(refused.getMessage(), refused);
		}
	}

}
