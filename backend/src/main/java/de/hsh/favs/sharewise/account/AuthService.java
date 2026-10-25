package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.UserInfo;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.UserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign up, sign in and resolving a bearer token to its user. Passwords are BCrypt hashes. */
@Service
class AuthService {

	record SignedIn(String token, UserInfo user) {
	}

	private final UserRepository users;

	private final AuthSessionRepository sessions;

	private final PasswordEncoder passwords;

	private final SecureRandom random = new SecureRandom();

	AuthService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder passwords) {
		this.users = users;
		this.sessions = sessions;
		this.passwords = passwords;
	}

	@Transactional
	SignedIn register(String email, String displayName, String password) {
		String normalized = normalize(email);
		if (users.existsByEmail(normalized)) {
			throw emailTaken();
		}
		User user;
		try {
			user = users.saveAndFlush(User.signUp(normalized, displayName.strip(), passwords.encode(password)));
		}
		catch (DataIntegrityViolationException raced) {
			throw emailTaken();
		}
		return startSession(user);
	}

	@Transactional
	SignedIn login(String email, String password) {
		User user = users.findByEmail(normalize(email)).filter(u -> u.passwordHash() != null)
				.filter(u -> passwords.matches(password, u.passwordHash()))
				.orElseThrow(() -> new DomainException(ErrorCode.INVALID_CREDENTIALS, "Wrong email or password"));
		return startSession(user);
	}

	@Transactional(readOnly = true)
	Optional<UserInfo> resolve(String token) {
		return sessions.findById(hash(token)).flatMap(s -> users.findById(s.userId()))
				.map(u -> new UserInfo(new UserId(u.id()), u.displayName()));
	}

	private SignedIn startSession(User user) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		sessions.save(new AuthSession(hash(token), user.id(), Instant.now()));
		return new SignedIn(token, new UserInfo(new UserId(user.id()), user.displayName()));
	}

	private static DomainException emailTaken() {
		return new DomainException(ErrorCode.EMAIL_TAKEN, "This email is already registered");
	}

	private static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static String hash(String token) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
