package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.api.AccountEvent;
import de.hsh.favs.sharewise.account.api.LoginSucceeded;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.domain.Account;
import de.hsh.favs.sharewise.account.domain.AuthSession;
import de.hsh.favs.sharewise.account.domain.Hasher;
import de.hsh.favs.sharewise.account.domain.RawPassword;
import de.hsh.favs.sharewise.account.persistence.AccountRepository;
import de.hsh.favs.sharewise.account.persistence.AuthSessionRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign up, sign in, and turning a bearer token back into the account it belongs to. */
@Service
public class AccountService {

	/** The result of a sign-up or sign-in: the one-time token and who it belongs to. */
	public record SignedIn(String token, UserProfile profile) {

	}

	private final AccountRepository accounts;

	private final AuthSessionRepository sessions;

	private final Hasher hasher;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	AccountService(AccountRepository accounts, AuthSessionRepository sessions, Hasher hasher,
			ApplicationEventPublisher events, Clock clock) {
		this.accounts = accounts;
		this.sessions = sessions;
		this.hasher = hasher;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Creates an account and signs it in.
	 * @throws DomainException EMAIL_TAKEN when the address is already registered
	 */
	@Transactional
	public SignedIn register(RegisterCommand command) {
		if (accounts.existsByEmail(command.email().value())) {
			throw emailTaken();
		}
		var registration = Account.register(command.email(), command.displayName(), command.password(), hasher,
				clock.instant());
		try {
			accounts.saveAndFlush(registration.account());
		}
		catch (DataIntegrityViolationException raced) {
			throw emailTaken();
		}
		events.publishEvent(registration.event());
		return startSession(registration.account());
	}

	/**
	 * Signs in with email and password. A failed attempt is counted and saved even though
	 * the call ends with an exception, hence {@code noRollbackFor}.
	 * @throws DomainException INVALID_CREDENTIALS for an unknown email or a wrong
	 * password (the same answer for both)
	 */
	@Transactional(noRollbackFor = DomainException.class)
	public SignedIn login(LoginCommand command) {
		Optional<Account> account = accounts.findByEmail(command.email().value());
		if (account.isEmpty() || !RawPassword.isAcceptable(command.password())) {
			throw invalidCredentials();
		}
		AccountEvent outcome = account.get().login(new RawPassword(command.password()), hasher, clock.instant());
		accounts.save(account.get());
		events.publishEvent(outcome);
		if (!(outcome instanceof LoginSucceeded)) {
			throw invalidCredentials();
		}
		return startSession(account.get());
	}

	/**
	 * The profile of the account a bearer token belongs to; empty for an unknown token.
	 */
	@Transactional(readOnly = true)
	public Optional<UserProfile> resolveSession(String token) {
		return sessions.findById(hasher.hashToken(token))
			.flatMap(session -> accounts.findById(session.userId()))
			.map(AccountService::toProfile);
	}

	static UserProfile toProfile(Account account) {
		return new UserProfile(account.userId(), account.displayName().value(), account.isActive());
	}

	private SignedIn startSession(Account account) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		sessions.save(new AuthSession(hasher.hashToken(token), account.userId().value(), clock.instant()));
		return new SignedIn(token, toProfile(account));
	}

	private static DomainException emailTaken() {
		return new DomainException(ErrorCode.EMAIL_TAKEN, "This email is already registered");
	}

	private static DomainException invalidCredentials() {
		return new DomainException(ErrorCode.INVALID_CREDENTIALS, "Wrong email or password");
	}

}
