package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.domain.Account;
import de.hsh.favs.sharewise.account.domain.DisplayName;
import de.hsh.favs.sharewise.account.persistence.AccountRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the identity of a validated Keycloak token into the caller's account, creating
 * the account on the first request of a new person (D4).
 */
@Service
class AccountService {

	private static final Logger log = LoggerFactory.getLogger(AccountService.class);

	private final AccountRepository accounts;

	private final Clock clock;

	AccountService(AccountRepository accounts, Clock clock) {
		this.accounts = accounts;
		this.clock = clock;
	}

	/**
	 * The profile of the account the token's subject belongs to. An unknown subject gets
	 * its account now; two concurrent first requests create it once (F1, C4). A known
	 * account is never updated from the token (D10, C2).
	 * @throws DomainException UNAUTHENTICATED for a subject that is not a UUID, a missing
	 * or malformed email (F9), an email that already belongs to another account (F3) or
	 * an account that is not active (C3, F11); nothing is saved then
	 */
	@Transactional
	public UserProfile resolveOrCreate(IdentityClaims claims) {
		UUID id = parseSubject(claims.subject());
		Optional<Account> known = accounts.findById(id);
		if (known.isPresent()) {
			return requireActive(known.get());
		}
		Account created = Account.registerFromIdentityProvider(new UserId(id), parseEmail(claims.email()),
				DisplayName.derive(claims.name(), claims.preferredUsername(), claims.email()), clock.instant());
		if (accounts.insertIfAbsent(created)) {
			return toProfile(created);
		}
		// Lost the race against a concurrent first request (its row is committed and
		// visible now), or the email belongs to an account with another id.
		return accounts.findById(id).map(AccountService::requireActive).orElseThrow(() -> {
			log.warn("Refused first login of subject {}: its email belongs to another account", id);
			return unauthenticated();
		});
	}

	static UserProfile toProfile(Account account) {
		return new UserProfile(account.userId(), account.displayName().value(), account.isActive());
	}

	private static UserProfile requireActive(Account account) {
		if (!account.isActive()) {
			throw unauthenticated();
		}
		return toProfile(account);
	}

	private static UUID parseSubject(String subject) {
		if (subject == null) {
			throw unauthenticated();
		}
		try {
			return UUID.fromString(subject);
		}
		catch (IllegalArgumentException notAUuid) {
			throw unauthenticated();
		}
	}

	private static Email parseEmail(String email) {
		try {
			return new Email(email);
		}
		catch (DomainException malformed) {
			throw unauthenticated();
		}
	}

	private static DomainException unauthenticated() {
		return new DomainException(ErrorCode.UNAUTHENTICATED, "Missing or invalid token");
	}

}
